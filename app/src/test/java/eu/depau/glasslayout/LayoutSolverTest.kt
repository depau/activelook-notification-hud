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
import eu.depau.glasslayout.core.model.BoxInsets
import eu.depau.glasslayout.core.model.Border
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
            box(width = Fill, height = Fill, background = 1)
            box(width = Fill, height = Fill, background = 1)
            box(width = Fill, height = Fill, background = 1)
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

    @Test fun marginShiftsTextElPosition() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            text("AB", font = FontToken.Small, margin = BoxInsets(left = 10, top = 5, right = 0, bottom = 0))
        }
        val t = texts(solver.solve(root, LSize(100, 100))).single()
        assertEquals(10, t.x)
        assertEquals(5, t.y)
    }

    @Test fun paddingShiftsTextElContent() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            text("AB", font = FontToken.Small, padding = BoxInsets(left = 15, top = 8, right = 5, bottom = 2))
        }
        val t = texts(solver.solve(root, LSize(100, 100))).single()
        assertEquals(15, t.x)
        assertEquals(8, t.y)
    }

    @Test fun borderDrawsRectAndOffsetsTextElContent() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            text("AB", font = FontToken.Small, border = Border(color = 3, thickness = 2))
        }
        val cmds = solver.solve(root, LSize(100, 100))
        val borders = cmds.filterIsInstance<RenderCommand.BorderRect>()
        val border = borders.single()
        assertEquals(0, border.rect.left)
        assertEquals(0, border.rect.top)
        assertEquals(24, border.rect.width)
        assertEquals(14, border.rect.height)
        assertEquals(3, border.color)
        assertEquals(2, border.thick)
        
        val t = texts(cmds).single()
        assertEquals(2, t.x)
        assertEquals(2, t.y)
    }

    @Test fun backgroundDrawsRect() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            text("AB", font = FontToken.Small, background = 5)
        }
        val cmds = solver.solve(root, LSize(100, 100))
        val bg = fills(cmds).single()
        assertEquals(0, bg.rect.left)
        assertEquals(0, bg.rect.top)
        assertEquals(20, bg.rect.width)
        assertEquals(10, bg.rect.height)
        assertEquals(5, bg.color)
    }

    @Test fun boxModelCombinationOnContainer() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            column(
                width = Fill,
                height = Fixed(50),
                margin = BoxInsets(left = 5, top = 10, right = 15, bottom = 20),
                padding = BoxInsets(left = 2, top = 4, right = 6, bottom = 8),
                border = Border(color = 7, thickness = 3),
                background = 9
            ) {
                text("A", font = FontToken.Small)
            }
        }
        val cmds = solver.solve(root, LSize(100, 100))
        
        val bg = fills(cmds).single()
        assertEquals(5, bg.rect.left)
        assertEquals(10, bg.rect.top)
        assertEquals(80, bg.rect.width)
        assertEquals(20, bg.rect.height)
        assertEquals(9, bg.color)
        
        val border = cmds.filterIsInstance<RenderCommand.BorderRect>().single()
        assertEquals(5, border.rect.left)
        assertEquals(10, border.rect.top)
        assertEquals(80, border.rect.width)
        assertEquals(20, border.rect.height)
        assertEquals(7, border.color)
        assertEquals(3, border.thick)
        
        val t = texts(cmds).single()
        assertEquals(10, t.x)
        assertEquals(17, t.y)
    }

    @Test fun suppressImagesInContainerHidesImagesButKeepsSpace() {
        val root = column(width = Fixed(100), height = Fixed(100)) {
            column(width = Fill, height = Fill, suppressImages = true) {
                image(key = "img", payload = "payload", w = 20, h = 20)
                text("AB", font = FontToken.Small)
            }
        }
        val cmds = solver.solve(root, LSize(100, 100))
        
        val t = texts(cmds).single()
        assertEquals(20, t.y)
        
        val images = cmds.filterIsInstance<RenderCommand.Image>()
        assertTrue("images should be suppressed", images.isEmpty())
    }
}
