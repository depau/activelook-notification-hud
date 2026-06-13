package eu.depau.activelooknotifications.phone

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Gathers phone-side status for the glasses status bar: battery (always) and cellular signal +
 * network type (only when READ_PHONE_STATE is granted — otherwise [SignalInfo] stays null and the
 * right side of the idle bar is left blank).
 *
 * Network type uses [TelephonyDisplayInfo.getOverrideNetworkType] so 5G NSA (which reports a LTE
 * `dataNetworkType`) is correctly shown as 5G, and listens for display-info changes so the label
 * updates live without a reconnect. Calls [onUpdate] whenever battery or signal changes.
 */
class PhoneStatusProvider(
    private val context: Context,
    private val onUpdate: (battery: Int, signal: SignalInfo?) -> Unit,
) {
    private var battery: Int = 0
    @Volatile private var bars: Int = 0
    @Volatile
    private var wifiBars: Int = 4 // WiFi RSSI level 0..4 (full until we hear otherwise)
    @Volatile private var overrideType: Int = 0 // TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var signal: SignalInfo? = null
    private var registered = false

    private val telephony: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivity: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Fires on default-network changes (e.g. WiFi ↔ cellular) so the network label updates instantly.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = recompute()
        override fun onLost(network: Network) = recompute()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiBars =
                wifiLevelFrom(caps)
            recompute()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                battery = level * 100 / scale
                emit()
            }
        }
    }

    // API 31+ callback (signal + display info).
    @RequiresApi(Build.VERSION_CODES.S)
    private inner class TCallback : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.DisplayInfoListener {
        override fun onSignalStrengthsChanged(strength: SignalStrength) {
            bars = strength.level
            recompute()
        }

        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            overrideType = info.overrideNetworkType
            recompute()
        }
    }

    private var telephonyCallback: TelephonyCallback? = null

    // < API 31 fallback.
    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(strength: SignalStrength?) {
            bars = strength?.level ?: 0
            recompute()
        }

        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            overrideType = info.overrideNetworkType
            recompute()
        }
    }

    fun start() {
        if (registered) return
        registered = true

        val sticky = context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) battery = level * 100 / scale
        }

        if (hasPhoneStatePermission()) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = TCallback()
                    telephonyCallback = cb
                    telephony?.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
                } else {
                    @Suppress("DEPRECATION")
                    val flags = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED else 0
                    @Suppress("DEPRECATION")
                    telephony?.listen(phoneStateListener, flags)
                }
                recompute()
            }.onFailure { Log.w(TAG, "Failed to register telephony listeners", it) }
        }
        // Watch connectivity (WiFi ↔ cellular) regardless of phone-state permission.
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
        recompute()
        emit()
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(batteryReceiver) }
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephony?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                telephony?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun recompute() {
        signal = when {
            isWifiActive() -> SignalInfo(
                NetworkType.WIFI,
                wifiBars.coerceIn(0, 4)
            ) // no phone-state perm needed
            hasPhoneStatePermission() -> SignalInfo(currentNetworkType(), bars.coerceIn(0, 4))
            else -> null
        }
        emit()
    }

    /** Map a WiFi network's RSSI (API 29+) to 0..4 bars; full when unknown or pre-Q. */
    @Suppress("DEPRECATION")
    private fun wifiLevelFrom(caps: NetworkCapabilities): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 4
        val rssi = caps.signalStrength
        if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return 4
        return WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
    }

    private fun emit() = onUpdate(battery, signal)

    private fun currentNetworkType(): NetworkType {
        if (isWifiActive()) return NetworkType.WIFI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isNrOverride(overrideType)) return NetworkType.FIVE_G
        val type = runCatching {
            @Suppress("DEPRECATION")
            telephony?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> NetworkType.FIVE_G
            TelephonyManager.NETWORK_TYPE_LTE -> NetworkType.LTE
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkType.HSPA

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> NetworkType.THREE_G

            TelephonyManager.NETWORK_TYPE_EDGE -> NetworkType.EDGE
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT -> NetworkType.GPRS

            else -> NetworkType.NONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isNrOverride(override: Int): Boolean = when (override) {
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> true
        else -> false
    }

    private fun isWifiActive(): Boolean = try {
        val cm = connectivity ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } catch (e: Exception) {
        false
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "PhoneStatusProvider"
    }
}
