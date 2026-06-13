package eu.depau.activelooknotifications.glasses

import android.graphics.Bitmap
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.display.NotifItem
import eu.depau.activelooknotifications.phone.NetworkType
import eu.depau.activelooknotifications.phone.StatusInfo
import eu.depau.glasslayout.core.dsl.ChildrenScope
import eu.depau.glasslayout.core.dsl.Fill
import eu.depau.glasslayout.core.dsl.Fit
import eu.depau.glasslayout.core.dsl.Fixed
import eu.depau.glasslayout.core.dsl.column
import eu.depau.glasslayout.core.dsl.row
import eu.depau.glasslayout.core.model.CrossAlign
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.model.MainAlign
import eu.depau.glasslayout.core.model.Padding
import eu.depau.glasslayout.core.model.TextAlign
import eu.depau.glasslayout.core.text.AsciiTextShaper

/**
 * Builds the four HUD states as flex element trees in logical coordinates (origin top-left). The
 * backend flips to device coords, so logical-left = visual-left here. All user text is shaped to the
 * ROM fonts' ASCII range first.
 */
object HudScreens {

    private const val W = Const.SCREEN_W
    private const val H = Const.SCREEN_H
    private val rootPadding = Padding(Const.MARGIN_X, Const.TOP_MARGIN, Const.MARGIN_X, Const.BOTTOM_MARGIN)

    fun idle(status: StatusInfo, yOffset: Int): Element = column(
        width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP,
    ) {
        statusBar(batteryText(status), signalText(status))
        column(
            width = Fill, height = Fill, main = MainAlign.Start, cross = CrossAlign.Center,
            gap = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
        ) {
            text(shape(status.time), font = FontToken.Large, width = Fill, align = TextAlign.Center)
            val date = shape(status.date)
            if (date.isNotEmpty()) text(date, font = FontToken.Small, width = Fill, align = TextAlign.Center)
        }
    }

    fun appPresent(notif: NotifItem, icon: Bitmap?, status: StatusInfo, yOffset: Int): Element = column(
        width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP,
    ) {
        statusBar(batteryText(status), shape(status.time))
        column(
            width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
            gap = Const.ICON_NAME_GAP, clip = true, translateY = yOffset,
        ) {
            if (icon != null) image(key = notif.key, payload = icon, w = Const.ICON_SIZE, h = Const.ICON_SIZE)
            text(shape(notif.appName), font = FontToken.Medium, width = Fill, align = TextAlign.Center, maxLines = 1)
        }
    }

    fun peek(notif: NotifItem, status: StatusInfo, yOffset: Int): Element = column(
        width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP,
    ) {
        statusBar(batteryText(status), shape(status.time))
        column(
            width = Fill, height = Fill, main = MainAlign.Center, cross = CrossAlign.Center,
            gap = Const.TITLE_BODY_GAP, clip = true, translateY = yOffset,
        ) {
            text(shape(notif.title), font = FontToken.Medium, width = Fill, align = TextAlign.Center, maxLines = 1)
            val line = shape(notif.bodyFirstLine)
            if (line.isNotEmpty()) text(line, font = FontToken.Small, width = Fill, align = TextAlign.Center, maxLines = 1)
        }
    }

    fun open(
        notif: NotifItem,
        lines: List<String>,
        scrollPx: Int,
        showScrollbar: Boolean,
        yOffset: Int,
        status: StatusInfo,
    ): Element = column(
        width = Fixed(W), height = Fixed(H), padding = rootPadding, gap = Const.STATUS_CONTENT_GAP,
    ) {
        statusBar(batteryText(status), shape(status.time))
        text(shape(notif.title), font = FontToken.Medium, width = Fill, align = TextAlign.Center, maxLines = 1)
        row(width = Fill, height = Fill, gap = Const.SCROLLBAR_GAP) {
            column(
                width = Fill, height = Fill, clip = true, scrollY = scrollPx,
                gap = Const.LINE_GAP, translateY = yOffset,
            ) {
                for (line in lines) {
                    text(line, font = FontToken.Small, width = Fill, align = TextAlign.Start, maxLines = 1)
                }
            }
            if (showScrollbar) {
                box(width = Fixed(Const.SCROLLBAR_W), height = Fill, fill = Const.COLOR_WHITE.toInt())
            }
        }
    }

    // --- helpers ---

    private fun ChildrenScope.statusBar(left: String, right: String) {
        row(width = Fill, main = MainAlign.SpaceBetween) {
            text(left, font = FontToken.Small)
            if (right.isNotEmpty()) text(right, font = FontToken.Small, align = TextAlign.End)
        }
    }

    private fun batteryText(s: StatusInfo): String =
        "G:" + (s.glassesBattery?.let { "$it%" } ?: "--") + " P:${s.phoneBattery}%"

    private fun signalText(s: StatusInfo): String {
        val sig = s.signal ?: return ""
        if (sig.networkType == NetworkType.WIFI) return sig.networkType.label
        val bars = if (sig.bars > 0) ".".repeat(sig.bars.coerceIn(0, 4)) else ""
        return listOf(sig.networkType.label, bars).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun shape(text: String): String = AsciiTextShaper.toAscii(text)
}
