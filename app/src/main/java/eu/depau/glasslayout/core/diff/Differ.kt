package eu.depau.glasslayout.core.diff

import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.render.RenderCommand

/**
 * Plan for presenting a frame: which rectangles to erase (black-fill) and which commands to (re)draw.
 * For a full redraw, [dirtyRects] is the whole screen and [draw] is every command.
 */
data class DiffPlan(
    val fullRedraw: Boolean,
    val dirtyRects: List<LRect>,
    val draw: List<RenderCommand>,
)

/**
 * Pure value-identity differ: commands equal by structure are "unchanged". Computes the minimal set
 * of dirty rectangles from changed (added/removed) command bounds, then asks the sink to erase those
 * and redraw every command intersecting them (so erasing can't leave unchanged neighbours wiped).
 */
class Differ(
    private val screen: LRect,
    private val maxDirtyRects: Int,
    private val mergeSlop: Int,
    private val fullRedrawThresholdPct: Int,
) {
    fun plan(old: List<RenderCommand>?, new: List<RenderCommand>): DiffPlan {
        if (old == null) return full(new)

        val changed = symmetricDifference(old, new)
        if (changed.isEmpty()) return DiffPlan(false, emptyList(), emptyList())

        var rects = changed.mapNotNull { it.bounds.intersect(screen) }
        if (rects.isEmpty()) return DiffPlan(false, emptyList(), emptyList())
        rects = mergeRects(rects)

        val dirtyArea = rects.sumOf { it.area }
        if (rects.size > maxDirtyRects || dirtyArea * 100 >= screen.area * fullRedrawThresholdPct) {
            return full(new)
        }

        val redraw = new.filter { c -> rects.any { c.bounds.intersects(it) } }.sortedBy { it.z }
        return DiffPlan(false, rects, redraw)
    }

    private fun full(new: List<RenderCommand>) =
        DiffPlan(true, listOf(screen), new.sortedBy { it.z })

    private fun symmetricDifference(old: List<RenderCommand>, new: List<RenderCommand>): List<RenderCommand> {
        val oldBag = HashMap<RenderCommand, Int>().apply { old.forEach { merge(it, 1, Int::plus) } }
        val newBag = HashMap<RenderCommand, Int>().apply { new.forEach { merge(it, 1, Int::plus) } }
        val changed = ArrayList<RenderCommand>()
        for ((c, n) in newBag) repeat((n - (oldBag[c] ?: 0)).coerceAtLeast(0)) { changed += c }
        for ((c, o) in oldBag) repeat((o - (newBag[c] ?: 0)).coerceAtLeast(0)) { changed += c }
        return changed
    }

    private fun mergeRects(input: List<LRect>): List<LRect> {
        val list = input.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    if (list[i].inflate(mergeSlop).intersects(list[j])) {
                        list[i] = list[i].union(list[j])
                        list.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        return if (list.size > maxDirtyRects) listOf(list.reduce { a, b -> a.union(b) }) else list
    }
}
