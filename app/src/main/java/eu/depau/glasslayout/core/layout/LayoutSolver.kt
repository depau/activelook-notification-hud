package eu.depau.glasslayout.core.layout

import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.geom.LSize
import eu.depau.glasslayout.core.model.Container
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Dir
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.ImageEl
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.Sizing
import eu.depau.glasslayout.core.model.SpacerEl
import eu.depau.glasslayout.core.model.TextAlign
import eu.depau.glasslayout.core.model.TextEl
import eu.depau.glasslayout.core.render.RenderCommand
import eu.depau.glasslayout.core.text.TextMeasurer

/**
 * Flexbox-style (Clay-inspired) layout solver. Pure: given an [Element] tree, a viewport, and a
 * [TextMeasurer], it produces positioned [RenderCommand]s in logical coordinates. Five passes:
 * fit-widths → grow/shrink-widths → wrap-text + fit-heights → grow/shrink-heights → position+emit.
 */
class LayoutSolver(private val measurer: TextMeasurer) {

    fun solve(root: Element, viewport: LSize): List<RenderCommand> {
        val node = build(root)
        fitWidths(node)
        node.w = resolveAxis(node.width, node.prefW, node.minW, viewport.width, fill = viewport.width)
        growWidths(node)
        fitHeights(node)
        node.h = resolveAxis(node.height, node.contentH, node.contentH, viewport.height, fill = viewport.height)
        growHeights(node)
        val out = ArrayList<RenderCommand>()
        place(node, 0, 0, clip = null, suppressImages = false, out)
        return out
    }

    // --- node ---

    private class Node(val el: Element) {
        val width: Sizing = el.width
        val height: Sizing = el.height
        var x = 0
        var y = 0
        var w = 0
        var h = 0
        var minW = 0
        var prefW = 0
        var contentH = 0
        var lines: List<String> = emptyList()
        val children = ArrayList<Node>()
    }

    private fun build(el: Element): Node {
        val n = Node(el)
        if (el is Container) el.children.forEach { n.children += build(it) }
        return n
    }

    // --- pass 1: fit widths (bottom-up) ---

    private fun fitWidths(n: Node) {
        n.children.forEach { fitWidths(it) }
        val el = n.el
        val borderThick = el.border?.thickness ?: 0
        val extraW = el.margin.horizontal + el.padding.horizontal + 2 * borderThick
        when (el) {
            is TextEl -> {
                n.prefW = measurer.measureWidth(el.text, el.font) + extraW
                n.minW = (if (el.wrap) longestWordWidth(el) else n.prefW - extraW) + extraW
            }
            is ImageEl -> {
                n.prefW = el.wPx + extraW
                n.minW = el.wPx + extraW
            }
            is SpacerEl -> {
                n.prefW = extraW
                n.minW = extraW
            }
            is Container -> {
                val pad = el.margin.horizontal + el.padding.horizontal + 2 * borderThick
                if (el.dir == Dir.Row) {
                    val gaps = gapTotal(el.spacing, n.children.size)
                    n.prefW = n.children.sumOf { it.prefW } + gaps + pad
                    n.minW = n.children.sumOf { it.minW } + gaps + pad
                } else {
                    n.prefW = (n.children.maxOfOrNull { it.prefW } ?: 0) + pad
                    n.minW = (n.children.maxOfOrNull { it.minW } ?: 0) + pad
                }
            }
        }
    }

    private fun longestWordWidth(el: TextEl): Int =
        el.text.split(Regex("\\s+")).maxOfOrNull { measurer.measureWidth(it, el.font) } ?: 0

    // --- pass 2: grow/shrink widths (top-down) ---

    private fun growWidths(n: Node) {
        val el = n.el
        if (el !is Container) return
        val borderThick = el.border?.thickness ?: 0
        val contentW = (n.w - el.margin.horizontal - el.padding.horizontal - 2 * borderThick).coerceAtLeast(0)
        if (el.dir == Dir.Row) {
            // Base sizes.
            for (c in n.children) c.w = baseMain(c, contentW)
            val used = n.children.sumOf { it.w } + gapTotal(el.spacing, n.children.size)
            val remaining = contentW - used
            if (remaining > 0) {
                distributeGrow(n.children, remaining)
            } else if (remaining < 0) {
                shrink(n.children, -remaining)
            }
        } else {
            // Column: cross axis is width.
            for (c in n.children) {
                c.w = when (c.width) {
                    is Sizing.Fixed -> (c.width as Sizing.Fixed).px
                    is Sizing.Percent -> (contentW * (c.width as Sizing.Percent).frac).toInt()
                    is Sizing.Grow -> contentW
                    Sizing.Fit -> if (el.cross == CrossAlign.Stretch) contentW else minOf(c.prefW, contentW)
                }
            }
        }
        n.children.forEach { growWidths(it) }
    }

    private fun baseMain(c: Node, contentW: Int): Int = when (val s = c.width) {
        is Sizing.Fixed -> s.px
        is Sizing.Percent -> (contentW * s.frac).toInt()
        Sizing.Fit -> c.prefW
        is Sizing.Grow -> c.minW
    }

    private fun distributeGrow(children: List<Node>, remaining: Int) {
        val grows = children.filter { it.width is Sizing.Grow }
        if (grows.isEmpty()) return
        val totalWeight = grows.sumOf { (it.width as Sizing.Grow).weight }.coerceAtLeast(1)
        var left = remaining
        grows.forEachIndexed { i, c ->
            val weight = (c.width as Sizing.Grow).weight
            val share = if (i == grows.lastIndex) left else remaining * weight / totalWeight
            c.w += share
            left -= share
        }
    }

    private fun shrink(children: List<Node>, deficit: Int) {
        val shrinkables = children.filter { it.width is Sizing.Grow || it.width == Sizing.Fit }
        val capacity = shrinkables.sumOf { (it.w - it.minW).coerceAtLeast(0) }
        if (capacity <= 0) return
        val take = deficit.coerceAtMost(capacity)
        var left = take
        shrinkables.forEachIndexed { i, c ->
            val
            cap = (c.w - c.minW).coerceAtLeast(0)
            val cut = if (i == shrinkables.lastIndex) left.coerceAtMost(cap) else (take * cap / capacity).coerceAtMost(cap)
            c.w -= cut
            left -= cut
        }
    }

    // --- pass 3: wrap text + fit heights (bottom-up) ---

    private fun fitHeights(n: Node) {
        n.children.forEach { fitHeights(it) }
        val el = n.el
        val borderThick = el.border?.thickness ?: 0
        val extraH = el.margin.vertical + el.padding.vertical + 2 * borderThick
        val extraW = el.margin.horizontal + el.padding.horizontal + 2 * borderThick
        when (el) {
            is TextEl -> {
                val innerW = (n.w - extraW).coerceAtLeast(0)
                n.lines = layoutLines(el, innerW)
                val count = n.lines.size.coerceAtLeast(1)
                n.h = measurer.lineHeight(el.font) + measurer.linePitch(el.font) * (count - 1) + extraH
                n.contentH = n.h
            }
            is ImageEl -> {
                n.h = el.hPx + extraH
                n.contentH = el.hPx + extraH
            }
            is SpacerEl -> {
                n.h = (if (el.height is Sizing.Fixed) (el.height as Sizing.Fixed).px else 0) + extraH
                n.contentH = n.h
            }
            is Container -> {
                n.contentH = if (el.dir == Dir.Column) {
                    n.children.sumOf { it.h } + gapTotal(el.spacing, n.children.size) + extraH
                } else {
                    (n.children.maxOfOrNull { it.h } ?: 0) + extraH
                }
                n.h = when (val hs = el.height) {
                    is Sizing.Fixed -> hs.px
                    else -> n.contentH
                }
            }
        }
    }

    private fun layoutLines(el: TextEl, width: Int): List<String> {
        if (el.text.isEmpty()) return emptyList()
        val natural = measurer.measureWidth(el.text, el.font)
        if (!el.wrap) {
            return if (natural <= width || !el.ellipsize) listOf(el.text)
            else listOf(measurer.ellipsize(el.text, el.font, width))
        }
        val wrapped = measurer.wrap(el.text, el.font, width)
        if (wrapped.size <= el.maxLines) return wrapped
        val kept = wrapped.take(el.maxLines).toMutableList()
        if (el.ellipsize && kept.isNotEmpty()) {
            val remainder = wrapped.drop(el.maxLines - 1).joinToString(" ")
            kept[kept.lastIndex] = measurer.ellipsize(remainder, el.font, width)
        }
        return kept
    }

    // --- pass 4: grow/shrink heights (top-down) ---

    private fun growHeights(n: Node) {
        val el = n.el
        if (el !is Container) return
        val borderThick = el.border?.thickness ?: 0
        val contentH = (n.h - el.margin.vertical - el.padding.vertical - 2 * borderThick).coerceAtLeast(0)
        if (el.dir == Dir.Column) {
            val used = n.children.sumOf { it.h } + gapTotal(el.spacing, n.children.size)
            val remaining = contentH - used
            if (remaining > 0) {
                val grows = n.children.filter { it.height is Sizing.Grow }
                if (grows.isNotEmpty()) {
                    val totalWeight = grows.sumOf { (it.height as Sizing.Grow).weight }.coerceAtLeast(1)
                    var left = remaining
                    grows.forEachIndexed { i, c ->
                        val weight = (c.height as Sizing.Grow).weight
                        val share = if (i == grows.lastIndex) left else remaining * weight / totalWeight
                        c.h += share
                        left -= share
                    }
                }
            }
        } else {
            for (c in n.children) {
                c.h = when (c.height) {
                    is Sizing.Fixed -> (c.height as Sizing.Fixed).px
                    is Sizing.Percent -> (contentH * (c.height as Sizing.Percent).frac).toInt()
                    is Sizing.Grow -> contentH
                    Sizing.Fit -> if (el.cross == CrossAlign.Stretch) contentH else c.h
                }
            }
        }
        n.children.forEach { growHeights(it) }
    }

    // --- pass 5: position + emit ---

    private fun place(n: Node, x: Int, y: Int, clip: LRect?, suppressImages: Boolean, out: MutableList<RenderCommand>) {
        val el = n.el
        n.x = x
        n.y = y

        val drawX = n.x + el.margin.left
        val drawY = n.y + el.margin.top
        val drawW = n.w - el.margin.horizontal
        val drawH = n.h - el.margin.vertical

        // Draw visuals.
        if (drawW > 0 && drawH > 0) {
            val rect = LRect.xywh(drawX, drawY, drawW, drawH)
            el.background?.let { emit(RenderCommand.FillRect(rect, it), clip, out) }
            el.border?.let { emit(RenderCommand.BorderRect(rect, it.color, it.thickness), clip, out) }
        }

        when (el) {
            is ImageEl -> {
                val borderThick = el.border?.thickness ?: 0
                val contentX = drawX + borderThick + el.padding.left
                val contentY = drawY + borderThick + el.padding.top
                val contentW = drawW - 2 * borderThick - el.padding.horizontal
                val contentH = drawH - 2 * borderThick - el.padding.vertical
                if (contentW > 0 && contentH > 0 && el.draw && !suppressImages) {
                    emit(RenderCommand.Image(contentX, contentY, contentW, contentH, el.key, el.payload), clip, out)
                }
            }
            is TextEl -> emitText(n, el, clip, out)
            is Container, is SpacerEl -> {}
        }

        if (el !is Container || n.children.isEmpty()) return

        val box = LRect.xywh(drawX, drawY, drawW, drawH)
        val childClip = if (el.clip) (clip?.intersect(box) ?: box) else clip

        val borderThick = el.border?.thickness ?: 0
        val contentX = drawX + borderThick + el.padding.left
        val contentY = drawY + borderThick + el.padding.top - el.scrollY + el.translateY
        val contentW = (drawW - 2 * borderThick - el.padding.horizontal).coerceAtLeast(0)
        val contentH = (drawH - 2 * borderThick - el.padding.vertical).coerceAtLeast(0)
        val gaps = gapTotal(el.spacing, n.children.size)

        val childSuppress = suppressImages || el.suppressImages

        if (el.dir == Dir.Column) {
            val usedMain = n.children.sumOf { it.h } + gaps
            var cy = contentY + leading(el.main, contentH - usedMain)
            val between = el.spacing + betweenExtra(el.main, contentH - usedMain, n.children.size)
            for (c in n.children) {
                val cx = contentX + crossOffset(el.cross, contentW - c.w)
                place(c, cx, cy, childClip, childSuppress, out)
                cy += c.h + between
            }
        } else {
            val usedMain = n.children.sumOf { it.w } + gaps
            var cx = contentX + leading(el.main, contentW - usedMain)
            val between = el.spacing + betweenExtra(el.main, contentW - usedMain, n.children.size)
            for (c in n.children) {
                val cy = contentY + crossOffset(el.cross, contentH - c.h)
                place(c, cx, cy, childClip, childSuppress, out)
                cx += c.w + between
            }
        }
    }

    private fun emitText(n: Node, el: TextEl, clip: LRect?, out: MutableList<RenderCommand>) {
        val cell = measurer.lineHeight(el.font)
        val pitch = measurer.linePitch(el.font)
        val borderThick = el.border?.thickness ?: 0
        val drawX = n.x + el.margin.left
        val drawY = n.y + el.margin.top
        val drawW = n.w - el.margin.horizontal
        val contentX = drawX + borderThick + el.padding.left
        val contentY = drawY + borderThick + el.padding.top
        val contentW = (drawW - 2 * borderThick - el.padding.horizontal).coerceAtLeast(0)

        n.lines.forEachIndexed { i, line ->
            val lineW = measurer.measureWidth(line, el.font)
            val dx = when (el.align) {
                TextAlign.Start -> 0
                TextAlign.Center -> (contentW - lineW) / 2
                TextAlign.End -> contentW - lineW
            }
            val cmd = RenderCommand.Text(contentX + dx, contentY + i * pitch, line, el.font, el.color, lineW, cell)
            emit(cmd, clip, out)
        }
    }

    private fun emit(cmd: RenderCommand, clip: LRect?, out: MutableList<RenderCommand>) {
        if (clip != null) {
            if (!cmd.bounds.intersects(clip)) return
            if (cmd.bounds.top < clip.top) return
        }
        out += cmd
    }

    // --- helpers ---

    private fun gapTotal(gap: Int, count: Int): Int = if (count > 1) gap * (count - 1) else 0

    private fun leading(main: MainAlign, free: Int): Int = when (main) {
        MainAlign.Start, MainAlign.SpaceBetween -> 0
        MainAlign.Center -> (free / 2).coerceAtLeast(0)
        MainAlign.End -> free.coerceAtLeast(0)
    }

    private fun betweenExtra(main: MainAlign, free: Int, count: Int): Int =
        if (main == MainAlign.SpaceBetween && count > 1 && free > 0) free / (count - 1) else 0

    private fun crossOffset(cross: CrossAlign, free: Int): Int = when (cross) {
        CrossAlign.Start, CrossAlign.Stretch -> 0
        CrossAlign.Center -> (free / 2).coerceAtLeast(0)
        CrossAlign.End -> free.coerceAtLeast(0)
    }

    private fun resolveAxis(s: Sizing, pref: Int, min: Int, avail: Int, fill: Int): Int = when (s) {
        is Sizing.Fixed -> s.px
        is Sizing.Percent -> (avail * s.frac).toInt()
        is Sizing.Grow -> fill
        Sizing.Fit -> pref.coerceAtMost(avail).coerceAtLeast(min)
    }
}
