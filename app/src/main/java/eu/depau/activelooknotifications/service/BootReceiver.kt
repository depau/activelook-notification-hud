package eu.depau.activelooknotifications.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import eu.depau.activelooknotifications.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts [NotifGlassService] on device boot when the user enabled auto-start.
 *
 * The guards (setting on, a device paired, BLE-connect permission granted) are mandatory, not
 * defensive: starting a foreground service via [Context.startForegroundService] that then fails to
 * call `startForeground()` within ~5 s crashes on Android 12+. We only start when
 * [NotifGlassService.connectGlasses] is guaranteed to promote to foreground.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope.launch {
            try {
                val settings = SettingsRepository(context)
                val enabled = settings.autoStartOnBoot.first()
                val hasSaved = settings.serializedGlasses.first().isNotEmpty()
                if (enabled && hasSaved && hasConnectPermission(context)) {
                    val svc = Intent(context, NotifGlassService::class.java).apply {
                        action = NotifGlassService.ACTION_START_FROM_BOOT
                    }
                    context.startForegroundService(svc)
                } else {
                    Log.d(TAG, "Skipping boot start (enabled=$enabled, hasSaved=$hasSaved)")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasConnectPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
