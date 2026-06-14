package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.display.NotifItem
import eu.depau.glasslayout.core.dsl.ChildrenScope
import eu.depau.glasslayout.core.dsl.Fill
import eu.depau.glasslayout.core.dsl.Fixed
import eu.depau.glasslayout.core.dsl.Fit
import eu.depau.glasslayout.core.dsl.column
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.BoxInsets
import eu.depau.glasslayout.core.model.TextAlign
import eu.depau.glasslayout.core.model.ScrollOffset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun idle(
        status: StatusBarModel,
        clock: String,
        date: String,
        activeNotifs: List<NotifItem>,
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
                if (activeNotifs.isNotEmpty()) {
                    val uniqueNotifs = activeNotifs.distinctBy { it.packageName }.take(7)
                    row(
                        width = Fit,
                        main = MainAlign.Center,
                        cross = CrossAlign.Center,
                        padding = BoxInsets(top = Const.NOTIFICATION_ICONS_GAP),
                        spacing = 6
                    ) {
                        for (notif in uniqueNotifs) {
                            notif.listIconBitmap?.let { bmp ->
                                image(
                                    payload = bmp,
                                    w = Const.LIST_ICON_SIZE,
                                    h = Const.LIST_ICON_SIZE
                                )
                            }
                        }
                    }
                }
            }
        }

    fun appPresent(
        status: StatusBarModel,
        appName: String,
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
                if (icon != null) {
                    image(payload = icon, w = 48, h = 48)
                } else {
                    spacer(width = Fixed(48), height = Fixed(48))
                }
                text(appName, font = FontToken.Medium, align = TextAlign.Center)
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
        items: List<NotifItem>,
        bullet: Bitmap,
        pageIndex: Int,
        yOffset: Int,
        onPageCountResolved: (Int) -> Unit,
    ): Element = column(
        width = Fixed(W),
        height = Fixed(H),
        padding = rootPadding,
        spacing = Const.STATUS_CONTENT_GAP
    ) {
        statusBar(status)
        column(
            width = Fill, height = Fill, clip = true,
            scrollY = ScrollOffset.Dynamic { solved ->
                val pageHeight = solved.height
                val rawPageCount = ((solved.contentHeight + pageHeight - 1) / pageHeight).coerceAtLeast(1)
                val overflow = solved.contentHeight - (rawPageCount - 1) * pageHeight
                val pageCount = if (rawPageCount >= 2 && overflow <= 40) {
                    rawPageCount - 1
                } else {
                    rawPageCount
                }
                onPageCountResolved(pageCount)
                val standardScroll = pageIndex * pageHeight
                val maxScroll = (solved.contentHeight - pageHeight).coerceAtLeast(0)
                minOf(standardScroll, maxScroll)
            },
            spacing = Const.LIST_GAP, translateY = yOffset,
            suppressImages = (yOffset != 0),
        ) {
            separator()
            for (it in items) {
                val time = timeFmt.format(Date(it.postTime))
                row(width = Fill, spacing = Const.LIST_HEADER_GAP, cross = CrossAlign.Center) {
                    if (it.listIconBitmap != null) {
                        image(
                            payload = it.listIconBitmap,
                            w = Const.LIST_ICON_SIZE,
                            h = Const.LIST_ICON_SIZE
                        )
                    } else {
                        spacer(
                            width = Fixed(Const.LIST_ICON_SIZE),
                            height = Fixed(Const.LIST_ICON_SIZE)
                        )
                    }
                    // App name takes the leftover width (Fill) so the time stays pinned at the right.
                    text(
                        it.appName,
                        font = FontToken.Small,
                        align = TextAlign.Start,
                        width = Fill
                    )
                    text(
                        time,
                        font = FontToken.Small,
                        align = TextAlign.End
                    )
                }

                if (it.title.isNotEmpty()) {
                    text(
                        it.title,
                        font = FontToken.Small,
                        align = TextAlign.Start,
                        wrap = true,
                        maxLines = Const.LIST_MAX_TITLE_LINES
                    )
                }
                if (it.sanitizedBody.isNotEmpty()) {
                    text(
                        it.sanitizedBody,
                        font = FontToken.Small,
                        align = TextAlign.Start,
                        wrap = true,
                        maxLines = Const.LIST_MAX_BODY_LINES
                    )
                }
                separator()
            }
            centeredBullet(bullet)
            spacer(height = Fixed(Const.LIST_GAP))
        }
    }

    // --- helpers ---

    private fun ChildrenScope.separator() {
        box(width = Fill, height = Fixed(Const.SEP_H), background = Const.COLOR_WHITE.toInt())
    }

    private fun ChildrenScope.centeredBullet(bullet: Bitmap) {
        row(width = Fill, height = Fixed(Const.BULLET_SIZE), main = MainAlign.Center, cross = CrossAlign.Center) {
            image(payload = bullet, w = Const.BULLET_SIZE, h = Const.BULLET_SIZE)
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
                    image(payload = r.icon, w = m.fontPx, h = m.fontPx)

                is StatusRight.Cellular -> row(
                    spacing = 0,
                    cross = CrossAlign.Center
                ) {
                    image(payload = r.typeIcon, w = m.fontPx, h = m.fontPx)
                    r.roamingIcon?.let { image(payload = it, w = m.fontPx, h = m.fontPx) }
                    image(payload = r.signalIcon, w = m.fontPx, h = m.fontPx)
                }

                is StatusRight.Time -> text(r.text, font = FontToken.Small, align = TextAlign.End)
                StatusRight.None -> {}
            }
        }
    }

    private fun ChildrenScope.batteryViz(b: BatteryViz, fontPx: Int) {
        row(spacing = Const.STATUS_PCT_GAP, cross = CrossAlign.Center) {
            image(payload = b.bitmap, w = fontPx, h = fontPx)
            if (b.percent.isNotEmpty()) text(b.percent, font = FontToken.Small)
        }
    }
}
