package eu.depau.glasslayout.core.geom

/** A size in logical pixels. */
data class LSize(val width: Int, val height: Int)

/**
 * An axis-aligned rectangle in logical layout space, [left,top) inclusive to [right,bottom) exclusive.
 * Always normalized (left ≤ right, top ≤ bottom) when produced by [of].
 */
data class LRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width.coerceAtLeast(0) * height.coerceAtLeast(0)
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun intersects(o: LRect): Boolean =
        left < o.right && right > o.left && top < o.bottom && bottom > o.top

    /** Intersection, or null if they don't overlap. */
    fun intersect(o: LRect): LRect? {
        val l = maxOf(left, o.left)
        val t = maxOf(top, o.top)
        val r = minOf(right, o.right)
        val b = minOf(bottom, o.bottom)
        return if (r > l && b > t) LRect(l, t, r, b) else null
    }

    /** Smallest rect containing both. */
    fun union(o: LRect): LRect = when {
        isEmpty -> o
        o.isEmpty -> this
        else -> LRect(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
    }

    fun contains(o: LRect): Boolean =
        left <= o.left && top <= o.top && right >= o.right && bottom >= o.bottom

    /** Grow (or shrink, if negative) the rect by [d] on every side. */
    fun inflate(d: Int): LRect = LRect(left - d, top - d, right + d, bottom + d)

    companion object {
        /** Build a normalized rect from any two opposite corners. */
        fun of(x0: Int, y0: Int, x1: Int, y1: Int): LRect =
            LRect(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))

        fun xywh(x: Int, y: Int, w: Int, h: Int): LRect = LRect(x, y, x + w, y + h)
    }
}
