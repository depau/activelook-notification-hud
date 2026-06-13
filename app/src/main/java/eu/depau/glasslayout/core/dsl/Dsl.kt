package eu.depau.glasslayout.core.dsl

import eu.depau.glasslayout.core.model.Container
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Dir
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.ImageEl
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.Padding
import eu.depau.glasslayout.core.model.Sizing
import eu.depau.glasslayout.core.model.SpacerEl
import eu.depau.glasslayout.core.model.TextAlign
import eu.depau.glasslayout.core.model.TextEl

/** Sizing conveniences so the DSL reads naturally (`width = Fill`, `height = Grow`, `Fixed(40)`). */
val Fit: Sizing = Sizing.Fit
val Fill: Sizing = Sizing.Grow(1)
val Grow: Sizing = Sizing.Grow(1)
fun Fixed(px: Int): Sizing = Sizing.Fixed(px)
fun grow(weight: Int): Sizing = Sizing.Grow(weight)
fun Percent(frac: Float): Sizing = Sizing.Percent(frac)

@DslMarker
annotation class GlassLayoutDsl

/** Collects child elements inside a container builder lambda. */
@GlassLayoutDsl
class ChildrenScope {
    @PublishedApi
    internal val children = mutableListOf<Element>()

    fun column(
        width: Sizing = Fit,
        height: Sizing = Fit,
        padding: Padding = Padding.NONE,
        gap: Int = 0,
        main: MainAlign = MainAlign.Start,
        cross: CrossAlign = CrossAlign.Start,
        fill: Int? = null,
        borderColor: Int? = null,
        borderThick: Int = 0,
        clip: Boolean = false,
        scrollY: Int = 0,
        translateY: Int = 0,
        content: ChildrenScope.() -> Unit = {},
    ) {
        children += buildContainer(
            Dir.Column, width, height, padding, gap, main, cross,
            fill, borderColor, borderThick, clip, scrollY, translateY, content,
        )
    }

    fun row(
        width: Sizing = Fit,
        height: Sizing = Fit,
        padding: Padding = Padding.NONE,
        gap: Int = 0,
        main: MainAlign = MainAlign.Start,
        cross: CrossAlign = CrossAlign.Start,
        fill: Int? = null,
        borderColor: Int? = null,
        borderThick: Int = 0,
        clip: Boolean = false,
        scrollY: Int = 0,
        translateY: Int = 0,
        content: ChildrenScope.() -> Unit = {},
    ) {
        children += buildContainer(
            Dir.Row, width, height, padding, gap, main, cross,
            fill, borderColor, borderThick, clip, scrollY, translateY, content,
        )
    }

    /** A box: a column container that draws a background fill and/or border. */
    fun box(
        width: Sizing = Fit,
        height: Sizing = Fit,
        padding: Padding = Padding.NONE,
        gap: Int = 0,
        main: MainAlign = MainAlign.Start,
        cross: CrossAlign = CrossAlign.Start,
        fill: Int? = null,
        borderColor: Int? = null,
        borderThick: Int = 1,
        content: ChildrenScope.() -> Unit = {},
    ) = column(width, height, padding, gap, main, cross, fill, borderColor, borderThick, content = content)

    fun text(
        text: String,
        font: FontToken = FontToken.Small,
        color: Int = 15,
        width: Sizing = Fit,
        align: TextAlign = TextAlign.Start,
        wrap: Boolean = false,
        maxLines: Int = 1,
        ellipsize: Boolean = true,
    ) {
        children += TextEl(width, Fit, text, font, color, align, wrap, maxLines, ellipsize)
    }

    fun image(key: Any, payload: Any, w: Int, h: Int) {
        children += ImageEl(Fixed(w), Fixed(h), key, payload, w, h)
    }

    fun spacer(width: Sizing = Fit, height: Sizing = Fit) {
        children += SpacerEl(width, height)
    }
}

private fun buildContainer(
    dir: Dir, width: Sizing, height: Sizing, padding: Padding, gap: Int,
    main: MainAlign, cross: CrossAlign, fill: Int?, borderColor: Int?, borderThick: Int,
    clip: Boolean, scrollY: Int, translateY: Int, content: ChildrenScope.() -> Unit,
): Container {
    val scope = ChildrenScope().apply(content)
    return Container(
        width, height, dir, padding, gap, main, cross,
        fill, borderColor, borderThick, clip, scrollY, translateY, scope.children,
    )
}

/** Root builders. */
fun column(
    width: Sizing = Fit,
    height: Sizing = Fit,
    padding: Padding = Padding.NONE,
    gap: Int = 0,
    main: MainAlign = MainAlign.Start,
    cross: CrossAlign = CrossAlign.Start,
    fill: Int? = null,
    borderColor: Int? = null,
    borderThick: Int = 0,
    clip: Boolean = false,
    scrollY: Int = 0,
    translateY: Int = 0,
    content: ChildrenScope.() -> Unit = {},
): Element = buildContainer(
    Dir.Column, width, height, padding, gap, main, cross,
    fill, borderColor, borderThick, clip, scrollY, translateY, content,
)

fun row(
    width: Sizing = Fit,
    height: Sizing = Fit,
    padding: Padding = Padding.NONE,
    gap: Int = 0,
    main: MainAlign = MainAlign.Start,
    cross: CrossAlign = CrossAlign.Start,
    fill: Int? = null,
    borderColor: Int? = null,
    borderThick: Int = 0,
    clip: Boolean = false,
    scrollY: Int = 0,
    translateY: Int = 0,
    content: ChildrenScope.() -> Unit = {},
): Element = buildContainer(
    Dir.Row, width, height, padding, gap, main, cross,
    fill, borderColor, borderThick, clip, scrollY, translateY, content,
)
