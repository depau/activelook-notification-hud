package eu.depau.glasslayout

import eu.depau.glasslayout.core.dsl.Fill
import eu.depau.glasslayout.core.dsl.Fixed
import eu.depau.glasslayout.core.dsl.column
import eu.depau.glasslayout.core.dsl.row
import eu.depau.glasslayout.core.geom.LSize
import eu.depau.glasslayout.core.layout.LayoutSolver
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.render.RenderCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutSolverTest {
    private val solver = LayoutSolver(FakeMeasurer(charW = 10))
    private fun texts(cmds: List<RenderCommand>) = cmds.filterIsInstance<RenderCommand.Text>()
    private fun fills(cmds: List<RenderCommand>) = cmds.filterIsInstance<RenderCommand.FillRect>()

    @Test fun centersTextHorizontally() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            text("AB", font = FontToken.Small, width = Fill, align = eu.depau.glasslayout.core.model.TextAlign.Center)
        }
        val t = texts(solver.solve(root, LSize(100, 100))).single()
        // "AB" → 20px wide, centered in 100 → x = 40, y = 0, height 10.
        assertEquals(40, t.x)
        assertEquals(0, t.y)
        assertEquals(20, t.measuredW)
        assertEquals(10, t.heightPx)
    }

    @Test fun spaceBetweenRowPushesChildrenToEdges() {
        val root = row(width = Fixed(100), height = Fixed(20), main = MainAlign.SpaceBetween) {
            text("A")
            text("B")
        }
        val t = texts(solver.solve(root, LSize(100, 100))).sortedBy { it.x }
        assertEquals(0, t[0].x)
        assertEquals(90, t[1].x) // 100 - 10
    }

    @Test fun growSpacerPushesContentDown() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            spacer(height = Fill)
            text("X")
        }
        val t = texts(solver.solve(root, LSize(100, 100))).single()
        assertEquals(90, t.y) // spacer grows to 90, text below
    }

    @Test fun growWidthsSplitWithRemainderToLast() {
        val root = row(width = Fixed(101), height = Fixed(10)) {
            box(width = Fill, height = Fill, fill = 1)
            box(width = Fill, height = Fill, fill = 1)
            box(width = Fill, height = Fill, fill = 1)
        }
        val f = fills(solver.solve(root, LSize(101, 10))).sortedBy { it.rect.left }
        assertEquals(3, f.size)
        assertEquals(0, f[0].rect.left); assertEquals(33, f[0].rect.right)
        assertEquals(33, f[1].rect.left); assertEquals(66, f[1].rect.right)
        assertEquals(66, f[2].rect.left); assertEquals(101, f[2].rect.right) // remainder → last
    }

    @Test fun clipPrunesOffscreenLines() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            column(width = Fill, height = Fill, clip = true, scrollY = 50) {
                repeat(10) { text("L$it") }
            }
        }
        val all = texts(solver.solve(root, LSize(100, 100)))
        assertTrue("some lines pruned", all.size in 1..9)
        // every emitted line is within the clip band
        assertTrue(all.all { it.y + it.heightPx > 0 && it.y < 100 })
    }

    @Test fun crossCenterCentersChildInColumn() {
        val root = column(width = Fixed(100), height = Fixed(100), cross = CrossAlign.Center) {
            text("AB") // width 20, Fit
        }
        val t = texts(solver.solve(root, LSize(100, 100))).single()
        assertEquals(40, t.x) // (100-20)/2
    }
}
