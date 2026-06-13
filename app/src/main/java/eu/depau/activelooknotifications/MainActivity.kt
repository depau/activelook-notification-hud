package eu.depau.activelooknotifications

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.depau.activelooknotifications.data.InstalledAppsRepository
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.service.NotifGlassService
import eu.depau.activelooknotifications.ui.AppRoot
import eu.depau.activelooknotifications.ui.theme.NotificationHudTheme

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<NotifGlassService?>(null)
    private var isBound by mutableStateOf(false)

    private var hasRuntimePerms by mutableStateOf(false)
    private var hasNotifAccess by mutableStateOf(false)
    private var hasPhoneState by mutableStateOf(false)

    private val settings by lazy { SettingsRepository(applicationContext) }
    private val installedApps by lazy { InstalledAppsRepository(applicationContext) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as NotifGlassService.LocalBinder).getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBound = false
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshPermissionState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissionState()

        setContent {
            NotificationHudTheme {
                AppRoot(
                    service = service,
                    isBound = isBound,
                    settings = settings,
                    installedApps = installedApps,
                    hasRuntimePerms = hasRuntimePerms,
                    hasNotifAccess = hasNotifAccess,
                    hasPhoneState = hasPhoneState,
                    onRequestRuntimePerms = { requestPermissions.launch(requiredPermissions()) },
                    onRequestNotifAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestPhoneState = {
                        requestPermissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, NotifGlassService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun refreshPermissionState() {
        hasRuntimePerms = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        hasNotifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        hasPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }
}
