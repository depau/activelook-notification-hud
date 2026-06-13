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
    val iconBitmap: Bitmap? = null,
) {
    val bodyFirstLine: String
        get() = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
}

/** What the glasses are currently showing. */
sealed interface DisplayState {
    /** Clock + status bar. */
    data object Idle : DisplayState

    /** App icon + app name splash. */
    data class AppPresent(val notif: NotifItem) : DisplayState

    /** Title + first body line. */
    data class Peek(val notif: NotifItem) : DisplayState

    /** Full, scrollable body. [offset] is a line index into the wrapped body. */
    data class Open(val notif: NotifItem, val offset: Int) : DisplayState
}
