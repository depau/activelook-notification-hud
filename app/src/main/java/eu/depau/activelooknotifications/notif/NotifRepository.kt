package eu.depau.activelooknotifications.notif

import eu.depau.activelooknotifications.display.NotifItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-wide bridge between the [NotificationListener] (a separate system-bound component) and
 * [eu.depau.activelooknotifications.service.NotifGlassService]. The listener emits; the service
 * collects. A buffered SharedFlow decouples the two and drops the oldest on overflow so a burst of
 * notifications can't block the listener.
 */
object NotifRepository {
    private val _incoming = MutableSharedFlow<NotifItem>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incoming: SharedFlow<NotifItem> = _incoming

    /** The most recent notification, so an idle gesture can re-open it. */
    @Volatile
    var lastNotif: NotifItem? = null
        private set

    /**
     * Returns a filtered, newest→oldest, capped snapshot of all currently-posted notifications.
     * Set by [NotificationListener] while it's connected (cleared otherwise), so a gesture can read
     * the live list from the system. Null when no listener is connected.
     */
    @Volatile
    var activeProvider: (() -> List<NotifItem>)? = null

    fun publish(item: NotifItem) {
        lastNotif = item
        _incoming.tryEmit(item)
    }
}
