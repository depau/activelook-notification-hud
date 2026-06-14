package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
import eu.depau.activelooknotifications.Const

/**
 * Converts a colourful launcher [Drawable] into a small monochrome/grayscale [Bitmap] suitable for the
 * glasses' 4bpp image commands.
 *
 * The SDK applies its own weighted-grayscale + 4bpp reduction. For silhouettes, we pre-threshold to
 * pure black/white for a crisp result. For legacy icons, we map pixels to 16 levels of gray (clamping
 * the max value to 240, which represents level 15) to avoid the SDK's nibble overflow bug.
 */
object IconRasterizer {

    private const val ON = 0xFFF0F0F0.toInt()
    private const val OFF = Color.BLACK

    fun toMono(drawable: Drawable, size: Int = Const.ICON_SIZE): Bitmap {
        var targetDrawable = drawable
        var isSilhouette = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawable.monochrome
            } else {
                null
            }
            if (mono != null) {
                targetDrawable = mono
                isSilhouette = true
            } else {
                targetDrawable = drawable.foreground
                isSilhouette = true
            }
        }

        // Render the drawable (adaptive icons included) into an ARGB bitmap.
        val src = createBitmap(size, size)
        val canvas = Canvas(src)
        targetDrawable.setBounds(0, 0, size, size)
        targetDrawable.draw(canvas)

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

                if (isSilhouette) {
                    out[x, y] = if (lum >= Const.ICON_THRESHOLD) ON else OFF
                } else {
                    val level = (lum * 15 / 255).coerceIn(0, 15)
                    val grayVal = level * 16
                    out[x, y] = Color.rgb(grayVal, grayVal, grayVal)
                }
            }
        }
        src.recycle()
        return out
    }
}
