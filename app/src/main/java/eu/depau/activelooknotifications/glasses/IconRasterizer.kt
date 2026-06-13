package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
import eu.depau.activelooknotifications.Const

/**
 * Converts a colourful launcher [Drawable] into a small monochrome [Bitmap] suitable for the
 * glasses' 4bpp image commands.
 *
 * The SDK applies its own weighted-grayscale + 4bpp reduction, so we pre-threshold to pure
 * black/white for a crisp result. "On" pixels are emitted as gray 240 which maps to the top 4bpp
 * level (15) — using pure white (255) would round to 16 and overflow the nibble.
 */
object IconRasterizer {

    private const val ON = 0xFFF0F0F0.toInt()
    private const val OFF = Color.BLACK

    fun toMono(drawable: Drawable, size: Int = Const.ICON_SIZE): Bitmap {
        // Render the drawable (adaptive icons included) into an ARGB bitmap.
        val src = createBitmap(size, size)
        val canvas = Canvas(src)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)

        val out = createBitmap(size, size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = src[x, y]
                val alpha = Color.alpha(p)
                // Composite over black: transparent areas become "off".
                val lum = if (alpha < 32) {
                    0
                } else {
                    val a = alpha / 255.0
                    val r = Color.red(p) * a
                    val g = Color.green(p) * a
                    val b = Color.blue(p) * a
                    (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                }
                out[x, y] = if (lum >= Const.ICON_THRESHOLD) ON else OFF
            }
        }
        src.recycle()
        return out
    }
}
