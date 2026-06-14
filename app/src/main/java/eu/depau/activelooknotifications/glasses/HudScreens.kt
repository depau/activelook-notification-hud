package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import eu.depau.activelooknotifications.Const
import eu.depau.glasslayout.core.dsl.ChildrenScope
import eu.depau.glasslayout.core.dsl.Fill
import eu.depau.glasslayout.core.dsl.Fixed
import eu.depau.glasslayout.core.dsl.column
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.BoxInsets
import eu.depau.glasslayout.core.model.TextAlign
import kotlin.math.roundToInt

/**
 * Builds the four HUD states as flex element trees in logical coordinates. Text content is supplied
 * **pre-shaped** as [Inline] runs (ASCII text + glyph images) by [GlassesRenderer]; this object only
 * lays them out. The status bar stays plain ASCII. `drawGlyphs=false` (mid-animation) reserves glyph
 * width with a spacer instead of streaming the image, so layout doesn't jump between frames.
 */
object HudScreens {

    private const val W = Const.SCREEN_W
    private const val H = Const.SCREEN_H
    private val rootPadding =
        BoxInsets(Const.MARGIN_X, Const.TOP_MARGIN, Const.MARGIN_X, Const.BOTTOM_MARGIN)

    fun idle(
        status: StatusBarModel,
        clock: String,
        date: String,
        yOffset: Int
    ): Element =
        column(
            width = Fixed(W),
            height = Fixed(H),
            padding = rootPadding,
            spacing = Const.STATUS_CONTENT_GAP
        ) {
            statusBar(status)
            column(
                width = Fill, height = Fill, main = MainAlign.Start, cross = CrossAlign.Center,
                spacing = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
                suppressImages = (yOffset != 0),
            ) {
                text(clock, font = FontToken.Large, align = TextAlign.Center)
                if (date.isNotEmpty()) text(
                    date,
                    font = FontToken.Small,
                    align = TextAlign.Center
                )
            }
        }

    fun appPresent(
        status: StatusBarModel,
        name: String,
        icon: Bitmap?,
        yOffset: Int
    ): Element =
        column(
            width = Fixed(W),
            height = Fixed(H),
            padding = rootPadding,
            spacing = Const.STATUS_CONTENT_GAP
        ) {
            statusBar(status)
            column(
                width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
                spacing = Const.ICON_NAME_GAP, clip = true, translateY = yOffset,
                suppressImages = (yOffset != 0),
            ) {
                if (icon != null) image(
                    key = "icon",
                    payload = icon,
                    w = Const.ICON_SIZE,
                    h = Const.ICON_SIZE
                )
                text(name, font = FontToken.Medium, align = TextAlign.Center)
            }
        }

    fun peek(
        status: StatusBarModel,
        title: String,
        body: String,
        yOffset: Int
    ): Element =
        column(
            width = Fixed(W),
            height = Fixed(H),
            padding = rootPadding,
            spacing = Const.STATUS_CONTENT_GAP
        ) {
            statusBar(status)
            column(
                width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
                spacing = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
                suppressImages = (yOffset != 0),
            ) {
                text(title, font = FontToken.Medium, align = TextAlign.Center, wrap = true, maxLines = Const.PEEK_TITLE_LINES)
                text(body, font = FontToken.Small, align = TextAlign.Center, wrap = true, maxLines = Const.PEEK_BODY_LINES)
            }
        }

    /** Gesture-opened, paginated list of currently-posted notifications. */
    fun notifList(
        status: StatusBarModel,
        rows: List<ListRow>,
        bullet: Bitmap,
        scrollPx: Int,
        yOffset: Int,
    ): Element = column(
        width = Fixed(W),
        height = Fixed(H),
        padding = rootPadding,
        spacing = Const.STATUS_CONTENT_GAP
    ) {
        statusBar(status)
        column(
            width = Fill, height = Fill, clip = true, scrollY = scrollPx,
            spacing = Const.LIST_GAP, translateY = yOffset,
            suppressImages = (yOffset != 0),
        ) {
            for (r in rows) when (r) {
                ListRow.Sep -> separator()
                is ListRow.Header -> headerRow(r.icon, r.appName, r.time)
                is ListRow.Line -> text(r.text, font = r.font, align = TextAlign.Start)
                ListRow.Bullet -> centeredBullet(bullet)
            }
        }
    }

    // --- helpers ---

    private fun ChildrenScope.separator() {
        box(width = Fill, height = Fixed(Const.SEP_H), background = Const.COLOR_WHITE.toInt())
    }

    private fun ChildrenScope.headerRow(icon: Bitmap?, appName: String, time: String) {
        row(width = Fill, spacing = Const.LIST_HEADER_GAP, cross = CrossAlign.Center) {
            if (icon != null) image(key = "listicon", payload = icon, w = Const.LIST_ICON_SIZE, h = Const.LIST_ICON_SIZE)
            else spacer(width = Fixed(Const.LIST_ICON_SIZE), height = Fixed(Const.LIST_ICON_SIZE))
            // App name takes the leftover width (Fill) so the time stays pinned at the right.
            text(appName, font = FontToken.Small, align = TextAlign.Start, width = Fill)
            text(time, font = FontToken.Small, align = TextAlign.End)
        }
    }

    private fun ChildrenScope.centeredBullet(bullet: Bitmap) {
        row(width = Fill, height = Fixed(Const.BULLET_SIZE), main = MainAlign.Center, cross = CrossAlign.Center) {
            image(key = "bullet", payload = bullet, w = Const.BULLET_SIZE, h = Const.BULLET_SIZE)
        }
    }

    private fun mainAlignFor(a: TextAlign) = when (a) {
        TextAlign.Start -> MainAlign.Start
        TextAlign.Center -> MainAlign.Center
        TextAlign.End -> MainAlign.End
    }

    private fun ChildrenScope.statusBar(m: StatusBarModel) {
        row(width = Fill, main = MainAlign.SpaceBetween, cross = CrossAlign.Center) {
            row(spacing = Const.STATUS_ITEM_GAP, cross = CrossAlign.Center) {        // left: batteries
                m.glasses?.let { batteryViz(it, m.fontPx) }
                batteryViz(m.phone, m.fontPx)
            }
            when (val r =
                m.right) {                                            // right: signal / time
                is StatusRight.Wifi ->
                    image(key = r.key, payload = r.icon, w = m.fontPx, h = m.fontPx)

                is StatusRight.Cellular -> row(
                    spacing = Const.STATUS_LABEL_GAP,
                    cross = CrossAlign.Center
                ) {
                    text(r.typeLabel, font = FontToken.Small)
                    signalBars(r.level, m.fontPx)
                }

                is StatusRight.Time -> text(r.text, font = FontToken.Small, align = TextAlign.End)
                StatusRight.None -> {}
            }
        }
    }

    private fun ChildrenScope.batteryViz(b: BatteryViz, fontPx: Int) {
        row(spacing = Const.STATUS_PCT_GAP, cross = CrossAlign.Center) {
            image(key = b.key, payload = b.bitmap, w = fontPx, h = fontPx)
            if (b.percent.isNotEmpty()) text(b.percent, font = FontToken.Small)
        }
    }

    /**
     * Cellular signal as chunky solid ascending bars (thin outlines were unreadable on the glasses):
     * only the active [level] bars are drawn, each a bold solid block of increasing height,
     * bottom-aligned. [level] in 0..4. The "5G"/"LTE" label beside it conveys the rest.
     */
    private fun ChildrenScope.signalBars(level: Int, fontPx: Int) {
        val white = Const.COLOR_WHITE.toInt()
        val barW = (fontPx * 0.22f).roundToInt().coerceAtLeast(4)
        val spacing = (fontPx * 0.13f).roundToInt().coerceAtLeast(2)
        val maxH = fontPx
        row(height = Fixed(maxH), spacing = spacing, cross = CrossAlign.End) {
            for (i in 0 until level.coerceIn(0, 4)) {
                val barH = (maxH * (0.4f + 0.6f * (i + 1) / 4f)).roundToInt().coerceAtLeast(4)
                box(width = Fixed(barW), height = Fixed(barH), background = white)
            }
        }
    }
}
