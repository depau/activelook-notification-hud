package eu.depau.activelooknotifications.display

import android.graphics.Bitmap

/**
 * A notification reduced to what the glasses need. [iconBitmap] is the monochrome bitmap shown on
 * the splash (the notification's small icon — a purpose-built silhouette — rasterized by the
 * listener), or null if unavailable.
 */
data class NotifItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val postTime: Long,
    /** 48px splash icon (AppPresent). */
    val iconBitmap: Bitmap? = null,
    /** [Const.LIST_ICON_SIZE]px header icon for the notification list; null if unavailable. */
    val listIconBitmap: Bitmap? = null,
) {
    /** Body with CR/CRLF normalized to LF, for consistent wrapping. */
    val sanitizedBody: String
        get() = body.replace("\r\n", "\n").replace('\r', '\n')
}

/** What the glasses are currently showing. */
sealed interface DisplayState {
    /** Clock + status bar. */
    data object Idle : DisplayState

    /** App icon + app name splash. */
    data class AppPresent(val notif: NotifItem) : DisplayState

    /** Title + first body lines (glance). */
    data class Peek(val notif: NotifItem) : DisplayState

    /** Paginated live list of all currently-posted notifications. [page] is 0-based. */
    data class NotifList(val page: Int) : DisplayState

    /** Glance-like "No notifications" message (gesture with nothing posted). */
    data object NoNotifs : DisplayState
}
