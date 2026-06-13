package eu.depau.glasslayout.activelook

import android.graphics.Bitmap
import android.util.Log
import com.activelook.activelooksdk.Glasses
import com.activelook.activelooksdk.types.Rotation
import com.activelook.activelooksdk.types.holdFlushAction
import eu.depau.glasslayout.core.diff.Differ
import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.render.RenderCommand
import eu.depau.glasslayout.core.render.RenderSink

/**
 * Presents frames to ActiveLook glasses with partial redraw: diffs against the previous frame,
 * erases only the changed rectangles (black `rectf`), and redraws the commands intersecting them —
 * all inside one `holdFlush(HOLD→FLUSH)` so the update is atomic and flicker-free. `clear()` is never
 * used here (the SDK does not hold it → it would flash); full redraws erase via a screen-sized rectf.
 */
class ActiveLookSink(
    private val differ: Differ,
    private val transform: DeviceTransform,
    private val fonts: FontResolver,
) : RenderSink {

    @Volatile
    var glasses: Glasses? = null
        set(value) {
            field = value
            invalidate() // force a full redraw on (re)connect
        }

    private var previous: List<RenderCommand>? = null
    private var lastColor = -1

    override fun invalidate() {
        previous = null
    }

    /** Hardware full-screen clear (only for explicit blanking, e.g. disconnect) + reset diff cache. */
    fun blank() {
        runCatching { glasses?.clear() }
        previous = null
    }

    override fun present(commands: List<RenderCommand>) {
        val g = glasses
        if (g == null) {
            previous = null
            return
        }
        val plan = differ.plan(previous, commands)
        previous = commands
        if (plan.dirtyRects.isEmpty() && plan.draw.isEmpty()) return // nothing changed

        runCatching {
            g.holdFlush(holdFlushAction.HOLD)
            lastColor = -1
            for (r in plan.dirtyRects) erase(g, r)
            for (cmd in plan.draw) draw(g, cmd)
            g.holdFlush(holdFlushAction.FLUSH)
        }.onFailure { Log.w(TAG, "present failed", it) }
    }

    private fun erase(g: Glasses, r: LRect) {
        setColor(g, 0)
        val b = transform.rect(r)
        g.rectf(b.x0, b.y0, b.x1, b.y1)
    }

    private fun draw(g: Glasses, cmd: RenderCommand) {
        when (cmd) {
            is RenderCommand.FillRect -> {
                setColor(g, cmd.color)
                val b = transform.rect(cmd.rect)
                g.rectf(b.x0, b.y0, b.x1, b.y1)
            }
            is RenderCommand.BorderRect -> {
                setColor(g, cmd.color)
                for (k in 0 until cmd.thick.coerceAtLeast(1)) {
                    val b = transform.rect(cmd.rect.inflate(-k))
                    if (b.x1 > b.x0 && b.y1 > b.y0) g.rect(b.x0, b.y0, b.x1, b.y1)
                }
            }
            is RenderCommand.Line -> {
                setColor(g, cmd.color)
                val a = transform.point(cmd.x0, cmd.y0)
                val b = transform.point(cmd.x1, cmd.y1)
                g.line(a.x, a.y, b.x, b.y)
            }
            is RenderCommand.Image -> {
                val a = transform.imageAnchor(cmd.x, cmd.y, cmd.w, cmd.h)
                when (val p = cmd.payload) {
                    is Bitmap -> g.imgStream4bppHeatShrink(p, a.x, a.y)
                    is Int -> g.imgDisplay(p.toByte(), a.x, a.y)
                    is Byte -> g.imgDisplay(p, a.x, a.y)
                }
            }
            is RenderCommand.Text -> {
                val a = transform.textAnchor(cmd.x, cmd.y)
                val rf = fonts.resolve(cmd.font)
                g.txt(a.x, a.y, Rotation.TOP_LR, rf.id, cmd.color.toByte(), cmd.text)
            }
        }
    }

    /** ActiveLook `color` is sticky state; only re-issue it when it actually changes. */
    private fun setColor(g: Glasses, c: Int) {
        if (c != lastColor) {
            g.color(c.toByte())
            lastColor = c
        }
    }

    companion object {
        private const val TAG = "ActiveLookSink"
    }
}
