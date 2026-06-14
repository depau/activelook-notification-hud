package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap

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

    /** Cellular: signal, network type, and optional roaming icons resolved from Material Symbols. [key] is a stable diff key. */
    data class Cellular(val key: String, val typeIcon: Bitmap, val roamingIcon: Bitmap?, val signalIcon: Bitmap) : StatusRight
    data class Time(val text: String) : StatusRight
    data object None : StatusRight
}

