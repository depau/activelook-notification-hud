package eu.depau.glasslayout.core.render

import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.model.FontToken

/**
 * A flat, positioned drawing primitive in LOGICAL coordinates (origin top-left). Produced by the
 * layout solver, consumed by a [RenderSink]. All commands are value types so structural equality is
 * the diff identity (no id bookkeeping needed).
 *
 * [bounds] is the pixel bounding box used for dirty-rect computation — it must be tight/accurate
 * (e.g. text bounds use the measured width + line height).
 */
sealed interface RenderCommand {
    val bounds: LRect
    /** Painter's order within a redraw region (lower drawn first), so layering survives partial redraws. */
    val z: Int

    data class FillRect(val rect: LRect, val color: Int) : RenderCommand {
        override val bounds get() = rect
        override val z get() = 0
    }

    data class BorderRect(val rect: LRect, val color: Int, val thick: Int) : RenderCommand {
        override val bounds get() = rect
        override val z get() = 1
    }

    data class Line(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val color: Int) : RenderCommand {
        override val bounds get() = LRect.of(x0, y0, x1, y1).inflate(1)
        override val z get() = 2
    }

    data class Image(
        val x: Int, val y: Int, val w: Int, val h: Int, val payload: Any,
    ) : RenderCommand {
        override val bounds get() = LRect.xywh(x, y, w, h)
        override val z get() = 3
    }

    data class Text(
        val x: Int, val y: Int, val text: String, val font: FontToken, val color: Int,
        val measuredW: Int, val heightPx: Int,
    ) : RenderCommand {
        override val bounds get() = LRect.xywh(x, y, measuredW, heightPx)
        override val z get() = 4
    }
}
