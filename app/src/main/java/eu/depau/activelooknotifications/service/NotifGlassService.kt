package eu.depau.activelooknotifications.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.activelook.activelooksdk.DiscoveredGlasses
import com.activelook.activelooksdk.Glasses
import com.activelook.activelooksdk.Sdk
import com.activelook.activelooksdk.SerializedGlasses
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.MainActivity
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.display.DisplayController
import eu.depau.activelooknotifications.display.DisplayState
import eu.depau.activelooknotifications.glasses.GlassesRenderer
import eu.depau.activelooknotifications.glasses.GlassesTextMetrics
import eu.depau.activelooknotifications.notif.NotifRepository
import eu.depau.activelooknotifications.phone.PhoneStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Foreground service that owns the glasses connection and drives the [DisplayController].
 *
 * Connection lifecycle mirrors the multimeter app's proven pattern (scan / connect / token-based
 * reconnect / WakeLock / FGS notification with a Disconnect action), extended with: fast reconnect
 * via a persisted [SerializedGlasses], gesture + battery subscriptions, phone status, and the
 * notification → glasses pipeline collected from [NotifRepository].
 */
class NotifGlassService : Service() {

    enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): NotifGlassService = this@NotifGlassService
    }

    // --- State exposed to the UI ---
    private val _glassesState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val glassesState: StateFlow<ConnectionState> = _glassesState

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _running = MutableStateFlow(false)
    /** Whether mirroring is enabled (the user turned it on); independent of momentary BLE state. */
    val running: StateFlow<Boolean> = _running

    /** Human-readable glasses fonts + configurations, shown in the debug section of Settings. */
    private val _glassesInfo = MutableStateFlow("Not connected.")
    val glassesInfo: StateFlow<String> = _glassesInfo

    /** Last rendered frame (logical render commands) for the on-phone debug preview. */
    private val _lastFrame = MutableStateFlow<List<eu.depau.glasslayout.core.render.RenderCommand>>(emptyList())
    val lastFrame: StateFlow<List<eu.depau.glasslayout.core.render.RenderCommand>> = _lastFrame

    /** Discovered glasses for the device picker (populated by [scanForDevices]). */
    private val _availableDevices = MutableStateFlow<List<GlassesDevice>>(emptyList())
    val availableDevices: StateFlow<List<GlassesDevice>> = _availableDevices
    private val discoveredMap = LinkedHashMap<String, DiscoveredGlasses>()
    @Volatile private var discovering = false

    data class GlassesDevice(val name: String, val address: String)
    @Volatile private var fontInfoText = "(querying…)"
    @Volatile private var configInfoText = "(querying…)"

    /** Config names available on the device (from cfgList), for the debug selector. */
    private val _configNames = MutableStateFlow<List<String>>(emptyList())
    val configNames: StateFlow<List<String>> = _configNames

    val displayState: StateFlow<DisplayState> get() = controller.state

    // --- Internals ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Built in onCreate(): GlassesTextMetrics loads a font asset and needs an attached Context.
    private lateinit var renderer: GlassesRenderer
    private lateinit var controller: DisplayController
    private lateinit var settings: SettingsRepository
    private var phoneStatus: PhoneStatusProvider? = null

    private var sdk: Sdk? = null
    private var connectedGlasses: Glasses? = null
    private var isScanning = false
    private var shouldBeConnected = false
    private var isForeground = false
    @Volatile private var brightness = Const.DEFAULT_BRIGHTNESS
    @Volatile private var ambientLightSensor = true
    @Volatile private var glassesConfigName = "ALooK"

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ALNotif::ServiceWakeLock")
            .apply { setReferenceCounted(false) }

        renderer = GlassesRenderer(GlassesTextMetrics(applicationContext), applicationContext)
        renderer.frameSink = { _lastFrame.value = it }
        controller = DisplayController(renderer, scope)
        settings = SettingsRepository(applicationContext)
        phoneStatus = PhoneStatusProvider(applicationContext) { battery, signal ->
            controller.updatePhoneStatus(battery, signal)
        }

        initSdk()
        observeSettings()
        observeNotifications()
        maybeAutoConnect()
    }

    /** Connect on startup if the user enabled auto-connect and a device was previously paired. */
    private fun maybeAutoConnect() {
        scope.launch {
            val auto = runCatching { settings.autoConnect.first() }.getOrDefault(true)
            val hasSaved = runCatching { settings.serializedGlasses.first().isNotEmpty() }.getOrDefault(false)
            if (auto && hasSaved && !shouldBeConnected) {
                // Run on the main thread (BLE/FGS calls expect it).
                handler.post { if (!shouldBeConnected) connectGlasses() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnectGlasses()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        scope.cancel()
    }

    // --- Settings + notifications wiring ---

    private fun observeSettings() {
        scope.launch { settings.appNameDurationMs.collect { controller.appNameDurationMs = it } }
        scope.launch { settings.peekTimeoutMs.collect { controller.peekTimeoutMs = it } }
        scope.launch { settings.openTimeoutMs.collect { controller.openTimeoutMs = it } }
        scope.launch { settings.animateTransitions.collect { controller.animate = it } }
        scope.launch { settings.showIcon.collect { renderer.showIcon = it } }
        scope.launch {
            settings.glassesConfig.collect { name ->
                val changed = name != glassesConfigName
                glassesConfigName = name
                // If already connected and the choice changed, re-apply and re-read fonts live.
                if (changed) connectedGlasses?.let { g ->
                    runCatching {
                        if (name.isNotEmpty()) g.cfgSet(name)
                        g.fontList { fonts ->
                            renderer.setFonts(fonts)
                            fontInfoText = fonts.joinToString("\n") { "id ${it.id}: height ${it.height}px" }
                            updateGlassesInfo()
                            controller.refresh()
                        }
                    }
                }
            }
        }
        scope.launch {
            settings.debugScreenBorder.collect {
                renderer.debugBorder = it
                controller.refresh()
            }
        }
        scope.launch {
            settings.brightness.collect {
                brightness = it
                renderer.setBrightness(it)
            }
        }
        scope.launch {
            settings.ambientLightSensor.collect {
                ambientLightSensor = it
                // When ALS is on it auto-controls brightness, overriding luma(); turning it off
                // lets the brightness slider take effect (re-apply it immediately).
                runCatching {
                    connectedGlasses?.als(it)
                    if (!it) connectedGlasses?.luma(brightness.coerceIn(0, 15).toByte())
                }
            }
        }
    }

    private fun observeNotifications() {
        scope.launch {
            NotifRepository.incoming.collect { controller.onNewNotification(it) }
        }
    }

    // --- Public connection control (called by the UI) ---

    fun connectGlasses() {
        shouldBeConnected = true
        _running.value = true
        _statusMessage.value = "Connecting glasses…"
        acquireWakeLock()
        if (canStartForegroundService()) startForegroundServiceNotification("Connecting glasses…")
        phoneStatus?.start()
        tryFastReconnectThenScan()
    }

    fun disconnectGlasses() {
        shouldBeConnected = false
        _running.value = false
        _statusMessage.value = "Disconnected"
        handler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        stopScan()
        teardownGlasses()
        controller.stop()
        phoneStatus?.stop()
        _glassesState.value = ConnectionState.DISCONNECTED
        releaseWakeLock()
        stopForegroundCompat()
    }

    val isRunning: Boolean get() = shouldBeConnected

    /**
     * Forget the saved glasses and reconnect by scanning fresh (so a different device can be picked
     * up, e.g. switching between real glasses and an emulator without wiping app data).
     */
    fun forgetGlasses() {
        scope.launch { settings.setSerializedGlasses("") }
        _statusMessage.value = "Forgetting device, rescanning…"
        handler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        stopScan()
        teardownGlasses()
        _glassesState.value = ConnectionState.DISCONNECTED
        if (shouldBeConnected) {
            acquireWakeLock()
            if (canStartForegroundService()) startForegroundServiceNotification("Scanning for glasses…")
            phoneStatus?.start()
            scheduleReconnect() // a plain scan (no saved device) → connects to whatever is found
        }
    }

    // --- SDK / connection ---

    private fun initSdk() {
        try {
            sdk = Sdk.init(
                applicationContext,
                Consumer { Log.d(TAG, "update start") },
                Consumer { pair -> pair.second.run() },
                Consumer { Log.d(TAG, "update progress") },
                Consumer { Log.d(TAG, "update success") },
                Consumer { Log.d(TAG, "update error") },
            )
        } catch (e: Exception) {
            Log.e(TAG, "SDK init failed", e)
        }
    }

    private fun tryFastReconnectThenScan() {
        scope.launch {
            val serialized = runCatching { settings.serializedGlasses.first() }.getOrDefault("")
            val sg = if (serialized.isNotEmpty()) deserializeGlasses(serialized) else null
            if (sg != null) {
                _statusMessage.value = "Reconnecting to ${sg.name}…"
                _glassesState.value = ConnectionState.CONNECTING
                val ok = runCatching {
                    sdk?.connect(
                        sg,
                        Consumer { glasses -> onGlassesConnected(glasses) },
                        Consumer {
                            Log.d(TAG, "Fast reconnect failed; scanning")
                            startScan()
                        },
                        Consumer { glasses -> handleDisconnect(glasses) },
                    )
                }.isSuccess
                if (!ok) startScan()
            } else {
                startScan()
            }
        }
    }

    private fun startScan() {
        val s = sdk ?: Sdk.getInstance()
        if (s == null) {
            _statusMessage.value = "SDK not initialized"
            _glassesState.value = ConnectionState.ERROR
            return
        }
        if (isScanning) return
        if (!hasBlePermissions()) {
            _statusMessage.value = "Bluetooth permission missing"
            _glassesState.value = ConnectionState.ERROR
            return
        }
        _statusMessage.value = "Scanning for glasses…"
        _glassesState.value = ConnectionState.SCANNING
        try {
            isScanning = true
            s.startScan(Consumer { discovered ->
                if (discovering) {
                    // Discovery mode: collect devices for the picker, don't auto-connect.
                    discoveredMap[discovered.address] = discovered
                    _availableDevices.value = discoveredMap.values.map { GlassesDevice(it.name ?: it.address, it.address) }
                    _statusMessage.value = "Found ${discoveredMap.size} device(s)…"
                } else {
                    _statusMessage.value = "Found ${discovered.name}. Connecting…"
                    stopScan()
                    connectDiscovered(discovered)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            _glassesState.value = ConnectionState.ERROR
            isScanning = false
            scheduleReconnect()
        }
    }

    private fun connectDiscovered(discovered: DiscoveredGlasses) {
        _glassesState.value = ConnectionState.CONNECTING
        runCatching {
            discovered.connect(
                Consumer { glasses -> onGlassesConnected(glasses) },
                Consumer {
                    _statusMessage.value = "Connection failed"
                    _glassesState.value = ConnectionState.ERROR
                    scheduleReconnect()
                },
                Consumer { glasses -> handleDisconnect(glasses) },
            )
        }.onFailure {
            Log.e(TAG, "connect() threw", it)
            scheduleReconnect()
        }
    }

    /** Scan for nearby glasses and publish them via [availableDevices] without connecting. */
    fun scanForDevices() {
        if (!hasBlePermissions()) {
            _statusMessage.value = "Bluetooth permission missing"
            return
        }
        discovering = true
        discoveredMap.clear()
        _availableDevices.value = emptyList()
        _statusMessage.value = "Scanning for devices…"
        startScan()
        handler.removeCallbacksAndMessages(DISCOVER_TOKEN)
        handler.postAtTime({ discovering = false; stopScan() }, DISCOVER_TOKEN, SystemClock.uptimeMillis() + 10_000L)
    }

    /** Connect to a specific device chosen from [availableDevices]. */
    fun selectDevice(address: String) {
        val dg = discoveredMap[address] ?: return
        discovering = false
        handler.removeCallbacksAndMessages(DISCOVER_TOKEN)
        stopScan()
        shouldBeConnected = true
        _running.value = true
        acquireWakeLock()
        if (canStartForegroundService()) startForegroundServiceNotification("Connecting glasses…")
        phoneStatus?.start()
        _statusMessage.value = "Connecting to ${dg.name ?: dg.address}…"
        connectDiscovered(dg)
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        runCatching { sdk?.stopScan() }
    }

    private fun onGlassesConnected(glasses: Glasses) {
        connectedGlasses = glasses
        renderer.glasses = glasses
        _glassesState.value = ConnectionState.CONNECTED
        _running.value = true
        _statusMessage.value = "Connected: ${glasses.name}"

        // Persist for fast reconnect next time.
        runCatching {
            val blob = serializeGlasses(glasses.serializedGlasses)
            scope.launch { settings.setSerializedGlasses(blob) }
        }

        glasses.setOnDisconnected(Consumer { handleDisconnect(it) })

        runCatching {
            glasses.power(true)
            // Pin a known configuration so the available fonts/images are deterministic across
            // power cycles (a power cycle can otherwise leave a different config active). "ALooK"
            // is the documented system config; the user can change it in debug settings.
            if (glassesConfigName.isNotEmpty()) glasses.cfgSet(glassesConfigName)
            glasses.clear()
            fontInfoText = "(querying…)"
            configInfoText = "(querying…)"
            updateGlassesInfo()
            glasses.fontList { fontList ->
                renderer.setFonts(fontList)
                fontInfoText = if (fontList.isEmpty()) "(none reported)"
                else fontList.joinToString("\n") { "id ${it.id}: height ${it.height}px" }
                fontList.forEach { Log.i(TAG, "ROM font id=${it.id} height=${it.height}") }
                updateGlassesInfo()
            }
            // Read available configurations (read-only; we don't cfgSet yet) so we can decide
            // later whether to pin a specific one across power cycles.
            glasses.cfgList { cfgs ->
                _configNames.value = cfgs.map { it.name }
                configInfoText = if (cfgs.isEmpty()) "(none reported)"
                else cfgs.joinToString("\n") {
                    "'${it.name}' v${it.version}, ${it.size}B${if (it.isSystem) ", system" else ""}"
                }
                cfgs.forEach {
                    Log.i(TAG, "config name='${it.name}' version=${it.version} size=${it.size} system=${it.isSystem}")
                }
                updateGlassesInfo()
            }
            glasses.als(ambientLightSensor)
            glasses.luma(brightness.coerceIn(0, 15).toByte())
            glasses.gesture(true)
            glasses.subscribeToSensorInterfaceNotifications(Runnable { controller.onGesture() })
            glasses.subscribeToBatteryLevelNotifications(Consumer { level -> controller.updateGlassesBattery(level) })
            glasses.battery(Consumer { level -> controller.updateGlassesBattery(level) })
        }.onFailure { Log.w(TAG, "post-connect setup failed", it) }

        controller.start()
        updateNotification()
    }

    private fun handleDisconnect(glasses: Glasses) {
        if (connectedGlasses === glasses) connectedGlasses = null
        renderer.glasses = null
        controller.stop()
        _glassesState.value = ConnectionState.DISCONNECTED
        _glassesInfo.value = "Not connected."
        if (shouldBeConnected) {
            _statusMessage.value = "Disconnected. Reconnecting…"
            scheduleReconnect()
        } else {
            _statusMessage.value = "Disconnected"
        }
        updateNotification()
    }

    private fun teardownGlasses() {
        val g = connectedGlasses ?: return
        runCatching {
            g.unsubscribeToSensorInterfaceNotifications()
            g.unsubscribeToBatteryLevelNotifications()
            g.gesture(false)
            g.clear()
            g.disconnect()
        }
        connectedGlasses = null
        renderer.glasses = null
    }

    private fun scheduleReconnect() {
        if (!shouldBeConnected) return
        _glassesState.value = ConnectionState.SCANNING
        handler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        handler.postAtTime(
            { if (shouldBeConnected && connectedGlasses == null) startScan() },
            RECONNECT_TOKEN,
            SystemClock.uptimeMillis() + Const.RECONNECT_DELAY_MS,
        )
    }

    private fun updateGlassesInfo() {
        _glassesInfo.value = "Fonts:\n$fontInfoText\n\nConfigurations:\n$configInfoText"
    }

    // --- SerializedGlasses (Java-serializable) persistence ---

    private fun serializeGlasses(sg: SerializedGlasses): String {
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(sg) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun deserializeGlasses(blob: String): SerializedGlasses? = try {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as SerializedGlasses }
    } catch (e: Exception) {
        Log.w(TAG, "failed to deserialize glasses", e)
        null
    }

    // --- WakeLock ---

    private fun acquireWakeLock() {
        runCatching { if (wakeLock?.isHeld == false) wakeLock?.acquire(60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
    }

    // --- Foreground notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Notification HUD", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Mirrors notifications to ActiveLook glasses" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification(text: String) {
        val notification = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun updateNotification() {
        if (!isForeground) return
        val text = when (_glassesState.value) {
            ConnectionState.CONNECTED -> "Glasses connected"
            ConnectionState.CONNECTING -> "Connecting…"
            ConnectionState.SCANNING -> "Scanning for glasses…"
            ConnectionState.ERROR -> "Connection error"
            ConnectionState.DISCONNECTED -> "Disconnected"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            isForeground = false
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground failed", e)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1, Intent(this, NotifGlassService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ActiveLook Notification HUD")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectIntent)
            .build()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        stopScan()
        teardownGlasses()
        controller.stop()
        phoneStatus?.stop()
        releaseWakeLock()
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun canStartForegroundService(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPermission(Manifest.permission.BLUETOOTH_CONNECT) else true

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "NotifGlassService"
        private const val CHANNEL_ID = "NotifGlassServiceChannel"
        private const val NOTIFICATION_ID = 7421
        private val RECONNECT_TOKEN = Any()
        private val DISCOVER_TOKEN = Any()
        const val ACTION_DISCONNECT = "eu.depau.activelooknotifications.action.DISCONNECT"
    }
}
