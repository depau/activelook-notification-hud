package eu.depau.glasslayout

import eu.depau.glasslayout.core.diff.Differ
import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.render.RenderCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DifferTest {
    private val screen = LRect(0, 0, 100, 100)
    private val differ = Differ(screen, maxDirtyRects = 4, mergeSlop = 4, fullRedrawThresholdPct = 70)

    private fun txt(x: Int, y: Int, s: String) =
        RenderCommand.Text(x, y, s, FontToken.Small, 15, s.length * 10, 10)

    @Test fun firstFrameIsFullRedraw() {
        val a = listOf(txt(0, 0, "a"))
        val plan = differ.plan(null, a)
        assertTrue(plan.fullRedraw)
        assertEquals(listOf(screen), plan.dirtyRects)
        assertEquals(a, plan.draw)
    }

    @Test fun identicalFrameDoesNothing() {
        val a = listOf(txt(0, 0, "a"))
        val plan = differ.plan(a, a)
        assertTrue(plan.dirtyRects.isEmpty())
        assertTrue(plan.draw.isEmpty())
    }

    @Test fun singleTextChangeProducesSmallDirtyRect() {
        val a = listOf(txt(0, 0, "a"), txt(50, 50, "keep"))
        val b = listOf(txt(0, 0, "b"), txt(50, 50, "keep"))
        val plan = differ.plan(a, b)
        assertFalse(plan.fullRedraw)
        assertEquals(1, plan.dirtyRects.size)
        assertEquals(LRect(0, 0, 10, 10), plan.dirtyRects.single())
        // Only the changed text is redrawn (the far one doesn't intersect).
        assertEquals(listOf(txt(0, 0, "b")), plan.draw)
    }

    @Test fun overlapRedrawsUnchangedNeighbour() {
        val fillA = RenderCommand.FillRect(LRect(0, 0, 40, 40), 5)
        val fillB = RenderCommand.FillRect(LRect(0, 0, 40, 40), 7) // color changed
        val text = txt(0, 0, "x") // sits on top of the fill, unchanged
        val plan = differ.plan(listOf(fillA, text), listOf(fillB, text))
        assertFalse(plan.fullRedraw)
        // The unchanged text overlaps the erased region → must be redrawn.
        assertTrue(plan.draw.contains(text))
        assertTrue(plan.draw.contains(fillB))
    }

    @Test fun largeChangeFallsBackToFullRedraw() {
        val a = listOf(txt(0, 0, "a"))
        val b = listOf(RenderCommand.FillRect(LRect(0, 0, 100, 100), 5)) // whole screen
        val plan = differ.plan(a, b)
        assertTrue(plan.fullRedraw)
    }

    @Test fun drawIsSortedByZ() {
        val text = txt(0, 0, "x")
        val fill = RenderCommand.FillRect(LRect(0, 0, 100, 100), 5)
        val plan = differ.plan(null, listOf(text, fill))
        // FillRect (z=0) must come before Text (z=4).
        assertTrue(plan.draw.indexOf(fill) < plan.draw.indexOf(text))
    }
}
