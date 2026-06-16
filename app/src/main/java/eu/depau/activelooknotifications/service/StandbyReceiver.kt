package eu.depau.activelooknotifications.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.depau.activelooknotifications.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Exported entry point so external automation (Tasker, MacroDroid, or a future Garmin Connect IQ
 * companion app) can engage/release "pause for workout" standby. Send an *explicit* broadcast to
 * this receiver with one of [NotifGlassService.ACTION_STANDBY_ON] / `_OFF` / `_TOGGLE`; it forwards
 * the command to the service.
 *
 * Gated by the "Allow other apps to pause" setting (default off), so an exported receiver can't
 * toggle standby unless the user opted in. Delivering via `startService` is allowed because the
 * service is already a running foreground service whenever standby is meaningful (mirroring is on).
 */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
class StandbyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != NotifGlassService.ACTION_STANDBY_ON &&
            action != NotifGlassService.ACTION_STANDBY_OFF &&
            action != NotifGlassService.ACTION_STANDBY_TOGGLE
        ) return
        val appContext = context.applicationContext
        // ponytail: DataStore read is async, so finish the broadcast off-thread via goAsync().
        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (SettingsRepository(appContext).allowExternalStandby.first()) {
                    runCatching {
                        appContext.startService(Intent(appContext, NotifGlassService::class.java).setAction(action))
                    }.onFailure { Log.w("StandbyReceiver", "forward $action failed", it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
