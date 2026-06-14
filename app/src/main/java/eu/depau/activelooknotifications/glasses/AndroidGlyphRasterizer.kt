package eu.depau.activelooknotifications.glasses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlin.math.ceil

/**
 * Rasterizes non-ASCII runs to monochrome bitmaps. Accented Latin / symbols are drawn with the
 * **bundled SSP font** (the same the glasses use) so they're indistinguishable from native ROM text;
 * emoji are drawn with the **bundled monochrome Noto Emoji** font (crisp B&W, scaled + vertically
 * centered to match the text's optical size); other scripts (CJK, …) fall back via SSP's system
 * fallback. Thresholded by alpha coverage; "on" pixels use gray 0xF0F0F0 → glasses 4bpp level 15.
 */
class AndroidGlyphRasterizer(context: Context) : GlyphRasterizer {

    private val ssp: Typeface =
        runCatching { Typeface.createFromAsset(context.applicationContext.assets, "fonts/SSP-SemiBold-Spacing.otf") }
            .getOrDefault(Typeface.DEFAULT)
    private val emoji: Typeface =
        runCatching { Typeface.createFromAsset(context.applicationContext.assets, "fonts/NotoEmoji-Regular.ttf") }
            .getOrDefault(Typeface.DEFAULT)

    private val paints = HashMap<Int, Paint>()        // key = fontPx (text) ; emoji paints offset by a flag
    private val cache = object : LruCache<String, Bitmap>(CACHE_ENTRIES) {}

    private fun paintFor(fontPx: Int, isEmoji: Boolean): Paint {
        val key = if (isEmoji) -fontPx else fontPx
        return paints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (isEmoji) {
                    typeface = emoji
                    textSize = fontPx * EMOJI_SCALE
                } else {
                    typeface = ssp
                    textSize = fontPx.toFloat()
                }
            }
        }
    }

    override fun measureRun(run: String, fontPx: Int): Int {
        if (run.isEmpty()) return 0
        return ceil(paintFor(fontPx, isEmoji(run)).measureText(run).toDouble()).toInt()
    }

    override fun rasterize(run: String, fontPx: Int): Any? {
        val key = "$run@$fontPx"
        cache.get(key)?.let { return it }
        val emojiRun = isEmoji(run)
        val paint = paintFor(fontPx, emojiRun)
        val w = ceil(paint.measureText(run).toDouble()).toInt()
        if (w <= 0 || fontPx <= 0) return null

        val src = createBitmap(w, fontPx)
        val canvas = Canvas(src)
        val fm = paint.fontMetrics
        val textBaseline = -paintFor(fontPx, isEmoji = false).fontMetrics.top
        val baseline = if (emojiRun) {
            // Sit the emoji on the text baseline like a letter, but clamp into the cell so a tall
            // glyph is never cropped; if it can't fit baseline-aligned, fall back to centering.
            val ascentExt = -fm.top
            val maxBaseline = fontPx - fm.bottom
            if (ascentExt > maxBaseline) (fontPx - (fm.bottom - fm.top)) / 2f - fm.top
            else textBaseline.coerceIn(ascentExt, maxBaseline)
        } else {
            // Accented letters / CJK sit on the native text baseline so they align with the letters.
            -fm.top
        }
        canvas.drawText(run, 0f, baseline, paint)

        val pixels = IntArray(w * fontPx)
        src.getPixels(pixels, 0, w, 0, 0, w, fontPx)
        for (i in pixels.indices) {
            pixels[i] = if (Color.alpha(pixels[i]) >= ALPHA_THRESHOLD) ON else OFF
        }
        val out = createBitmap(w, fontPx)
        out.setPixels(pixels, 0, w, 0, 0, w, fontPx)
        src.recycle()
        cache.put(key, out)
        return out
    }

    /** Heuristic: a run is emoji if it contains a pictographic/symbol codepoint or a VS-16. */
    private fun isEmoji(run: String): Boolean {
        var i = 0
        while (i < run.length) {
            val cp = run.codePointAt(i)
            if (cp == 0xFE0F ||
                cp in 0x1F000..0x1FAFF ||
                cp in 0x2600..0x27BF ||
                cp in 0x2B00..0x2BFF ||
                cp in 0x2190..0x21FF
            ) return true
            i += Character.charCount(cp)
        }
        return false
    }

    companion object {
        private const val ON = 0xFFF0F0F0.toInt()
        private const val OFF = Color.BLACK
        private const val ALPHA_THRESHOLD = 96
        private const val CACHE_ENTRIES = 64
        private const val EMOJI_SCALE = 0.7f
    }
}
