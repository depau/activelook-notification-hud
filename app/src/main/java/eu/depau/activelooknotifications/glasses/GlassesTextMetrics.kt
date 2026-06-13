package eu.depau.activelooknotifications.glasses

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import eu.depau.activelooknotifications.Const
import kotlin.math.ceil

/**
 * Pixel-accurate text layout for the glasses, measured with the glasses' actual font.
 *
 * ActiveLook's ROM fonts are rendered from `SSP-SemiBold-Spacing.otf` (bundled in assets). The
 * reported font "height" H equals the rasterization ppem, and the OTF's typo ascent−descent equals
 * its em (1000 units), so setting `Paint.textSize = H` reproduces the glasses' glyph advances
 * exactly. (Do NOT derive textSize from Android's `descent−ascent`, which uses hhea metrics ≈1257/em
 * and would under-measure by ~20%.) A single [Const.WIDTH_CAL] scalar absorbs per-glyph rounding.
 *
 * Coordinate model (unchanged): ActiveLook origin is bottom-RIGHT, x grows toward the visual LEFT
 * (0 = right edge, SCREEN_W = left edge). The TOP_LR text anchor (x,y) is the glyph row's visual
 * top-left corner (the HIGH-x end); text extends toward lower x and hangs downward from y.
 */
class GlassesTextMetrics(context: Context) {

    private val typeface: Typeface =
        Typeface.createFromAsset(context.applicationContext.assets, FONT_ASSET)

    private val paints = HashMap<Int, Paint>()

    private fun paintFor(glassesHeight: Int): Paint = paints.getOrPut(glassesHeight) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = this@GlassesTextMetrics.typeface
            textSize = glassesHeight.toFloat() // calibration: ppem == reported font height
        }
    }

    /** Rendered pixel width of [text] at the glasses font of height [glassesHeight]. */
    fun measureWidth(text: String, glassesHeight: Int): Int =
        ceil(paintFor(glassesHeight).measureText(text) * Const.WIDTH_CAL).toInt()

    // --- Vertical helpers ---

    /** Height in px of one text cell (anchored at the top, hanging downward). */
    fun cellHeight(glassesHeight: Int): Int = glassesHeight

    /** Vertical pitch between successive lines. */
    fun linePitch(glassesHeight: Int): Int = glassesHeight + Const.LINE_GAP

    // --- Text fitting ---

    /** Truncate [text] with an ASCII "..." so it fits within [maxWidth] px (the ROM font has no `…`). */
    fun ellipsize(text: String, glassesHeight: Int, maxWidth: Int): String {
        if (measureWidth(text, glassesHeight) <= maxWidth) return text
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end).trimEnd() + "..."
            if (measureWidth(candidate, glassesHeight) <= maxWidth) return candidate
            end--
        }
        return "..."
    }

    /**
     * Word-wrap [text] into lines no wider than [maxWidth] px. Words longer than a line are
     * hard-split. Newlines in the source are preserved as paragraph breaks.
     */
    fun wrap(text: String, glassesHeight: Int, maxWidth: Int = Const.MAX_LINE_W): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            if (paragraph.isBlank()) {
                lines.add("")
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.trim().split(Regex("\\s+"))) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (measureWidth(candidate, glassesHeight) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) {
                        lines.add(current.toString())
                        current = StringBuilder()
                    }
                    if (measureWidth(word, glassesHeight) > maxWidth) {
                        var chunk = StringBuilder()
                        for (ch in word) {
                            if (measureWidth("$chunk$ch", glassesHeight) <= maxWidth) {
                                chunk.append(ch)
                            } else {
                                lines.add(chunk.toString())
                                chunk = StringBuilder().append(ch)
                            }
                        }
                        current = chunk
                    } else {
                        current = StringBuilder(word)
                    }
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }
        return lines
    }

    companion object {
        private const val FONT_ASSET = "fonts/SSP-SemiBold-Spacing.otf"
    }
}
