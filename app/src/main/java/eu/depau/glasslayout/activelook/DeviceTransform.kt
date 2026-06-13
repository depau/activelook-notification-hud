package eu.depau.glasslayout.activelook

import eu.depau.glasslayout.core.geom.LRect

/**
 * Pure mapping from the engine's LOGICAL coordinates (origin top-left, x→right, y→down) to the
 * ActiveLook device coordinates (origin bottom-RIGHT, x→left, y→up). The flip is
 * `deviceX = screenW − logicalX`, `deviceY = screenH − logicalY`; the per-primitive anchor corner
 * differs because the SDK's `txt` and `imgStream` anchor different corners of the drawn object:
 *
 * - **Text** (`txt` TOP_LR) anchors the glyph row's visual top-left → the logical top-left corner.
 * - **Image** (`imgStream`/`imgDisplay`) anchors the device low-x/low-y corner → the logical
 *   bottom-right corner (verified: a centered icon stays centered, matching the prior renderer).
 * - **Rects/lines** flip every corner/endpoint.
 *
 * No safe-area math here — the safe margin is baked into the logical layout (screen padding).
 */
class DeviceTransform(private val screenW: Int, private val screenH: Int) {

    data class P(val x: Short, val y: Short)
    data class Box(val x0: Short, val y0: Short, val x1: Short, val y1: Short)

    /** Device anchor for a Text command (logical top-left → device high-x/high-y corner). */
    fun textAnchor(logicalX: Int, logicalY: Int): P =
        P((screenW - logicalX).toShort(), (screenH - logicalY).toShort())

    /** Device anchor for an Image (logical bottom-right → device low-x/low-y corner). */
    fun imageAnchor(logicalX: Int, logicalY: Int, w: Int, h: Int): P =
        P((screenW - (logicalX + w)).toShort(), (screenH - (logicalY + h)).toShort())

    fun point(logicalX: Int, logicalY: Int): P =
        P((screenW - logicalX).toShort(), (screenH - logicalY).toShort())

    /** Flip a logical rect to a normalized device box (x0≤x1, y0≤y1). */
    fun rect(r: LRect): Box {
        val ax = screenW - r.left
        val bx = screenW - r.right
        val ay = screenH - r.top
        val by = screenH - r.bottom
        return Box(minOf(ax, bx).toShort(), minOf(ay, by).toShort(), maxOf(ax, bx).toShort(), maxOf(ay, by).toShort())
    }
}
