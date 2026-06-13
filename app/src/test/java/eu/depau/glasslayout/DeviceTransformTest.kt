package eu.depau.glasslayout

import eu.depau.glasslayout.activelook.DeviceTransform
import eu.depau.glasslayout.core.geom.LRect
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransformTest {
    private val t = DeviceTransform(304, 256)

    @Test fun centeredTextAnchorMatchesProvenFormula() {
        // A width-w text centered → logical left = (304 - w)/2; device anchor must be (304 + w)/2.
        val w = 100
        val logicalLeft = (304 - w) / 2
        val a = t.textAnchor(logicalLeft, 30)
        assertEquals(((304 + w) / 2).toShort(), a.x)
        assertEquals((256 - 30).toShort(), a.y)
    }

    @Test fun rectCornersAreNormalized() {
        val b = t.rect(LRect(10, 20, 30, 40))
        // device xs: 304-10=294, 304-30=274 → (274,294); ys: 256-20=236, 256-40=216 → (216,236)
        assertEquals(274.toShort(), b.x0)
        assertEquals(216.toShort(), b.y0)
        assertEquals(294.toShort(), b.x1)
        assertEquals(236.toShort(), b.y1)
    }

    @Test fun imageAnchorIsBottomRightCorner() {
        // Centered 48px icon → logical left 128; image device anchor x = 304-(128+48) = 128.
        val a = t.imageAnchor(128, 100, 48, 48)
        assertEquals(128.toShort(), a.x)
        assertEquals((256 - (100 + 48)).toShort(), a.y)
    }
}
