package eu.depau.activelooknotifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.depau.activelooknotifications.service.NotifGlassService

/**
 * Base for activities that need the [NotifGlassService]. Starts + binds it in [onStart] and unbinds
 * in [onStop]; the service is `START_STICKY` + foreground, so it survives the brief unbind when
 * switching activities. [service]/[isBound] are Compose state for the hosted screens to observe.
 */
abstract class BoundServiceActivity : ComponentActivity() {

    protected var service by mutableStateOf<NotifGlassService?>(null)
    protected var isBound by mutableStateOf(false)

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

    override fun onStart() {
        super.onStart()
        Intent(this, NotifGlassService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
