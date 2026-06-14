package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.text.TextSpan

/**
 * What the HUD status bar should draw, resolved by [GlassesRenderer] from a
 * [eu.depau.activelooknotifications.phone.StatusInfo] and handed to [HudScreens] (which only lays it
 * out — it never touches a Context or rasterizes anything). Keeps the same façade↔builder boundary
 * the pre-shaped [Inline] runs already use.
 */
data class StatusBarModel(
    /** Glasses battery; null when unknown (indicator hidden). */
    val glasses: BatteryViz?,
    /** Phone battery (always present). */
    val phone: BatteryViz,
    /** Right side: signal when idle, the time otherwise, or nothing. */
    val right: StatusRight,
    /** Small ROM-font height in px; sizes the icons and the primitive battery. */
    val fontPx: Int,
)

/** A rasterized device icon (bluetooth / smartphone) + percentage. [key] is a stable diff key. */
data class BatteryViz(val key: String, val bitmap: Bitmap, val percent: String)

sealed interface StatusRight {
    /** WiFi icon by level (rasterized Material Symbol). [key] is a level-stable diff key. */
    data class Wifi(val key: String, val icon: Bitmap) : StatusRight

    /** Cellular: ascending signal bars (drawn from primitives) by [level] 0..4 + a "5G"/"LTE" label. */
    data class Cellular(val level: Int, val typeLabel: String) : StatusRight
    data class Time(val text: String) : StatusRight
    data object None : StatusRight
}

/**
 * One row in the gesture-opened notification list, pre-shaped by [GlassesRenderer] so [HudScreens]
 * only lays it out and the renderer's pagination height math can mirror the layout exactly.
 */
sealed interface ListRow {
    /** Full-width horizontal separator line. */
    data object Sep : ListRow

    /** `[icon] appName - HH:mm` header (Small font). [icon] is null if unavailable. */
    data class Header(val icon: Bitmap?, val appName: String, val time: String) : ListRow

    /** One wrapped title (Medium) or body (Small) line. */
    data class Line(val text: String, val font: FontToken) : ListRow

    /** Centered filled circle marking the end of the list. */
    data object Bullet : ListRow
}
