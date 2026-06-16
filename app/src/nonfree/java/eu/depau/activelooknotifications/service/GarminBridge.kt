package eu.depau.activelooknotifications.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp

/**
 * nonfree flavor: listens for the "ActiveLook Pause" Connect IQ data field via the Garmin ConnectIQ
 * Mobile SDK and drives the service's standby. The data field transmits "workout_running" while a Garmin
 * activity is recording and "workout_stopped" when it ends (see connectiq-datafield/); we map those to
 * [NotifGlassService.enterStandby]/[exitStandby], releasing the glasses for the watch.
 *
 * All SDK traffic is relayed by Garmin Connect Mobile; if it isn't installed, init fails and we just
 * log it — the rest of the app is unaffected. Lives only while the "Auto-pause for Garmin" setting
 * is on (the service calls [start]/[stop]).
 */
class GarminBridge(private val service: NotifGlassService) {

    private val main = Handler(Looper.getMainLooper())
    private val app = IQApp(APP_UUID)
    private var connectIQ: ConnectIQ? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val ciq = ConnectIQ.getInstance(service, ConnectIQ.IQConnectType.WIRELESS)
        connectIQ = ciq
        // autoUI = false: we're a service, not an Activity, so don't try to pop the "install Garmin
        // Connect Mobile" dialog — just report the error.
        runCatching {
            ciq.initialize(service, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() = registerAll(ciq)
                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus?) {
                    Log.w(TAG, "ConnectIQ init failed: $status (is Garmin Connect Mobile installed?)")
                }
                override fun onSdkShutDown() {}
            })
        }.onFailure { Log.w(TAG, "ConnectIQ init threw", it) }
    }

    private fun registerAll(ciq: ConnectIQ) {
        val devices = runCatching { ciq.knownDevices ?: emptyList() }.getOrDefault(emptyList())
        for (device in devices) {
            runCatching {
                ciq.registerForAppEvents(device, app) { _, _, message, _ ->
                    // "workout_running" doubles as the keepalive: onGarminWorkoutActive re-arms the
                    // auto-resume fail-safe each time, so a watch that goes silent doesn't strand us.
                    when (message?.firstOrNull()?.toString()) {
                        "workout_running" -> main.post { service.onGarminWorkoutActive() }
                        "workout_stopped" -> main.post { service.exitStandby() }
                    }
                }
            }.onFailure { Log.w(TAG, "registerForAppEvents failed for ${device.friendlyName}", it) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        val ciq = connectIQ ?: return
        connectIQ = null
        runCatching { ciq.unregisterAllForEvents() }
        runCatching { ciq.shutdown(service) }
    }

    companion object {
        const val SUPPORTED = true
        private const val TAG = "GarminBridge"
        // Must match connectiq-datafield/manifest.xml's application id.
        private const val APP_UUID = "BFD5D3459FD642ECA9DE0A05E91CD16D"
    }
}
