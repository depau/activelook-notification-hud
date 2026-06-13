package eu.depau.glasslayout.core.model

/**
 * The immutable element tree the layout engine solves. Pure data — no Android, no SDK, no app types.
 * Images are intentionally OPAQUE here ([ImageEl.payload]/[ImageEl.key]); the renderer backend
 * decides how to draw them (e.g. stream a Bitmap or display a saved-image id).
 */

/** Abstract font size class; a [eu.depau.glasslayout.core.text.TextMeasurer] maps it to real metrics. */
enum class FontToken { Small, Medium, Large }

/** How an element is sized along one axis. */
sealed interface Sizing {
    /** Exactly [px] logical pixels. */
    data class Fixed(val px: Int) : Sizing
    /** Take a share of leftover space along the parent's main axis, by [weight]. */
    data class Grow(val weight: Int = 1) : Sizing
    /** Wrap content. */
    data object Fit : Sizing
    /** A fraction (0..1) of the parent's content size. */
    data class Percent(val frac: Float) : Sizing
}

enum class Dir { Row, Column }
enum class MainAlign { Start, Center, End, SpaceBetween }
enum class CrossAlign { Start, Center, End, Stretch }
enum class TextAlign { Start, Center, End }

data class Padding(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom

    companion object {
        val NONE = Padding()
        fun all(p: Int) = Padding(p, p, p, p)
        fun symH(h: Int) = Padding(h, 0, h, 0)
        fun symV(v: Int) = Padding(0, v, 0, v)
        fun sym(h: Int, v: Int) = Padding(h, v, h, v)
    }
}

sealed interface Element {
    val width: Sizing
    val height: Sizing
}

/**
 * A flex container; also doubles as a "box" when [fill]/[borderColor] are set. Children lay out
 * along [dir]. [clip] + [scrollY] make it a scroll viewport (children translated up by scrollY,
 * those fully outside the container's box are pruned at whole-element granularity).
 */
data class Container(
    override val width: Sizing,
    override val height: Sizing,
    val dir: Dir,
    val padding: Padding,
    val gap: Int,
    val main: MainAlign,
    val cross: CrossAlign,
    val fill: Int? = null,
    val borderColor: Int? = null,
    val borderThick: Int = 0,
    val clip: Boolean = false,
    val scrollY: Int = 0,
    /** Logical Y translation applied to this subtree (used for slide animations). */
    val translateY: Int = 0,
    val children: List<Element> = emptyList(),
) : Element

data class TextEl(
    override val width: Sizing,
    override val height: Sizing,
    val text: String,
    val font: FontToken,
    val color: Int,
    val align: TextAlign,
    val wrap: Boolean,
    val maxLines: Int,
    val ellipsize: Boolean,
) : Element

data class ImageEl(
    override val width: Sizing,
    override val height: Sizing,
    val key: Any,
    val payload: Any,
    val wPx: Int,
    val hPx: Int,
) : Element

data class SpacerEl(
    override val width: Sizing,
    override val height: Sizing,
) : Element
