package eu.depau.activelooknotifications.ui

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import eu.depau.glasslayout.core.render.RenderCommand

/**
 * On-phone debug mirror of the glasses screen: renders the engine's logical [RenderCommand]s the way
 * the optics show them — monochrome **yellow** on black, with the same Source Sans Pro font and an
 * opaque **black** background behind text (mimicking the device's non-transparent text cells).
 *
 * Commands are in logical coordinates (origin top-left) which already match screen space, so they're
 * drawn directly, scaled to the canvas (304×256 aspect).
 */
@Composable
fun GlassesPreview(commands: List<RenderCommand>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val typeface = remember {
        runCatching { Typeface.createFromAsset(context.assets, "fonts/SSP-SemiBold-Spacing.otf") }.getOrNull()
    }
    val sorted = remember(commands) { commands.sortedBy { it.z } }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(304f / 256f)
            .background(Color.Black),
    ) {
        val sx = size.width / 304f
        val sy = size.height / 256f
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.typeface = typeface
            for (cmd in sorted) {
                when (cmd) {
                    is RenderCommand.FillRect -> {
                        paint.reset(); paint.color = yellow(cmd.color); paint.style = Paint.Style.FILL
                        nc.drawRect(cmd.rect.left * sx, cmd.rect.top * sy, cmd.rect.right * sx, cmd.rect.bottom * sy, paint)
                    }
                    is RenderCommand.BorderRect -> {
                        paint.reset(); paint.color = yellow(cmd.color); paint.style = Paint.Style.STROKE
                        paint.strokeWidth = (cmd.thick * sx).coerceAtLeast(1f)
                        nc.drawRect(cmd.rect.left * sx, cmd.rect.top * sy, cmd.rect.right * sx, cmd.rect.bottom * sy, paint)
                    }
                    is RenderCommand.Line -> {
                        paint.reset(); paint.color = yellow(cmd.color); paint.strokeWidth = sx.coerceAtLeast(1f)
                        nc.drawLine(cmd.x0 * sx, cmd.y0 * sy, cmd.x1 * sx, cmd.y1 * sy, paint)
                    }
                    is RenderCommand.Image -> {
                        val bmp = cmd.payload as? Bitmap ?: continue
                        paint.reset()
                        paint.colorFilter = PorterDuffColorFilter(yellow(15), PorterDuff.Mode.MULTIPLY)
                        val dst = Rect(
                            (cmd.x * sx).toInt(),
                            ((cmd.y + 2) * sy).toInt(),
                            ((cmd.x + cmd.w) * sx).toInt(),
                            ((cmd.y + cmd.h + 2) * sy).toInt()
                        )
                        nc.drawBitmap(bmp, null, dst, paint)
                    }
                    is RenderCommand.Text -> {
                        // Opaque black cell behind the glyphs (the glasses' text background isn't transparent).
                        paint.reset(); paint.color = android.graphics.Color.BLACK; paint.style = Paint.Style.FILL
                        nc.drawRect(cmd.x * sx, cmd.y * sy, (cmd.x + cmd.measuredW) * sx, (cmd.y + cmd.heightPx) * sy, paint)
                        paint.reset(); paint.typeface = typeface; paint.isAntiAlias = true
                        paint.color = yellow(cmd.color); paint.textSize = cmd.heightPx * sy
                        val baseline = cmd.y * sy - paint.ascent()
                        nc.drawText(cmd.text, cmd.x * sx, baseline, paint)
                    }
                }
            }
        }
    }
}

/** Map a glasses grey level (0..15) to the device's yellow phosphor brightness. */
private fun yellow(level: Int): Int {
    val f = (level.coerceIn(0, 15)) / 15f
    return android.graphics.Color.argb(255, (255 * f).toInt(), (255 * f).toInt(), 0)
}
