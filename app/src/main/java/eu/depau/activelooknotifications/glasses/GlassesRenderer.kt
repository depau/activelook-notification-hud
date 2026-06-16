package eu.depau.activelooknotifications.glasses

import android.content.Context
import android.graphics.Bitmap
import com.activelook.activelooksdk.Glasses
import com.activelook.activelooksdk.types.FontInfo
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.display.NotifItem
import eu.depau.activelooknotifications.phone.NetworkType
import eu.depau.activelooknotifications.phone.StatusInfo
import eu.depau.glasslayout.activelook.ActiveLookSink
import eu.depau.glasslayout.activelook.DeviceTransform
import eu.depau.glasslayout.activelook.FontResolver
import eu.depau.glasslayout.activelook.ResolvedFont
import eu.depau.glasslayout.core.diff.Differ
import eu.depau.glasslayout.core.geom.LRect
import eu.depau.glasslayout.core.geom.LSize
import eu.depau.glasslayout.core.layout.LayoutSolver
import eu.depau.glasslayout.core.model.Element
import eu.depau.glasslayout.core.model.FontToken
import eu.depau.glasslayout.core.render.RenderCommand

/**
 * Façade over the glasslayout engine. Keeps the imperative API the [eu.depau.activelooknotifications.display.DisplayController]
 * already calls (`renderIdle/AppPresent/Peek/NotifList`, `setFonts`,
 * `setBrightness`, `debugBorder`, `glasses`, `clearScreen`). Internally each render builds
 * a core [Element] tree, solves it, and presents it through the partial-redraw [ActiveLookSink].
 */
class GlassesRenderer(metrics: GlassesTextMetrics, context: Context) {

    private val fonts = FontResolver(
        desiredHeights = mapOf(
            FontToken.Small to Const.DESIRED_SMALL_H,
            FontToken.Medium to Const.DESIRED_MEDIUM_H,
            FontToken.Large to Const.DESIRED_LARGE_H,
        ),
        fallback = mapOf(
            FontToken.Small to ResolvedFont(Const.FALLBACK_SMALL, Const.FALLBACK_SMALL_H),
            FontToken.Medium to ResolvedFont(Const.FALLBACK_MEDIUM, Const.FALLBACK_MEDIUM_H),
            FontToken.Large to ResolvedFont(Const.FALLBACK_LARGE, Const.FALLBACK_LARGE_H),
        ),
    )
    private val measurer = GlassesTextMeasurer(metrics, fonts)
    private val shaper = TextSpanParserImpl(fonts, AndroidGlyphRasterizer(context))
    private val statusIcons = StatusIcons()
    private val solver = LayoutSolver(measurer, parser = shaper)
    private val sink = ActiveLookSink(
        differ = Differ(
            screen = LRect(0, 0, Const.SCREEN_W, Const.SCREEN_H),
            maxDirtyRects = Const.MAX_DIRTY_RECTS,
            mergeSlop = Const.MERGE_SLOP,
            fullRedrawThresholdPct = Const.FULL_REDRAW_PCT,
        ),
        transform = DeviceTransform(Const.SCREEN_W, Const.SCREEN_H),
        fonts = fonts,
    )

    @Volatile
    var debugBorder: Boolean = false

    /** Optional observer of every presented frame (the full logical command list) — for the on-phone preview. */
    @Volatile
    var frameSink: ((List<RenderCommand>) -> Unit)? = null

    var glasses: Glasses?
        get() = sink.glasses
        set(value) { sink.glasses = value }

    fun setFonts(list: List<FontInfo>) {
        fonts.setFonts(list)
        sink.invalidate()
    }

    fun setBrightness(level: Int) {
        runCatching { sink.glasses?.luma(level.coerceIn(0, 15).toByte()) }
    }

    /** Full hardware clear + reset diff cache (used on disconnect). */
    fun clearScreen() = sink.blank()

    // --- States (same signatures the controller already calls; glyphs only on the settled frame) ---

    fun renderIdle(status: StatusInfo, activeNotifs: List<NotifItem>, contentYOffset: Int = 0) {
        present(
            HudScreens.idle(
                statusModel(status, idle = true),
                status.time,
                status.date,
                activeNotifs,
                contentYOffset
            )
        )
    }

    fun renderAppPresent(notif: NotifItem, iconBitmap: Bitmap?, status: StatusInfo, contentYOffset: Int = 0) {
        present(
            HudScreens.appPresent(
                statusModel(status, idle = false),
                notif.appName,
                iconBitmap,
                contentYOffset
            )
        )
    }

    fun renderPeek(notif: NotifItem, status: StatusInfo, contentYOffset: Int = 0) {
        present(HudScreens.peek(statusModel(status, idle = false), notif.title, notif.sanitizedBody, contentYOffset))
    }

    /** Render page [page] of the gesture-opened notification list. */
    fun renderNotifList(
        items: List<NotifItem>,
        page: Int,
        status: StatusInfo,
        contentYOffset: Int = 0,
        onPageCountResolved: (Int) -> Unit
    ) {
        present(
            HudScreens.notifList(
                statusModel(status, idle = false),
                items,
                statusIcons.bullet(Const.BULLET_SIZE),
                page,
                contentYOffset,
                onPageCountResolved
            )
        )
    }

    /** Glance shown when a gesture fires with nothing posted. */
    fun renderNoNotifs(status: StatusInfo, contentYOffset: Int = 0) {
        present(HudScreens.peek(statusModel(status, idle = false), "All caught up", "No notifications", contentYOffset))
    }

    /** Resolve the phone/glasses [StatusInfo] into icon bitmaps + a primitive battery for [HudScreens]. */
    private fun statusModel(status: StatusInfo, idle: Boolean): StatusBarModel {
        val px = fontPx(FontToken.Small)
        val glasses =
            status.glassesBattery?.let { BatteryViz("bt", statusIcons.bluetooth(px), "$it%") }
        val phone = BatteryViz("phone", statusIcons.smartphone(px), "${status.phoneBattery}%")
        val right: StatusRight = if (!idle) {
            StatusRight.Time(status.time)
        } else {
            val sig = status.signal
            when {
                sig == null || sig.networkType == NetworkType.NONE -> StatusRight.None
                sig.networkType == NetworkType.WIFI ->
                    StatusRight.Wifi("wifi${sig.bars}", statusIcons.wifi(sig.bars, px))

                else -> {
                    val roamingIcon = if (sig.roaming) statusIcons.roaming(px) else null
                    StatusRight.Cellular(
                        key = "cellular-${sig.bars}-${sig.networkType}-${sig.noInternet}-${sig.roaming}",
                        typeIcon = statusIcons.cellularType(sig.networkType, px),
                        roamingIcon = roamingIcon,
                        signalIcon = statusIcons.cellularSignal(sig.bars, sig.noInternet, px)
                    )
                }
            }
        }
        return StatusBarModel(glasses, phone, right, px)
    }

    private fun fontPx(font: FontToken): Int = fonts.resolve(font).heightPx

    private fun present(root: Element) {
        var cmds = solver.solve(root, LSize(Const.SCREEN_W, Const.SCREEN_H))
        if (debugBorder) cmds = cmds + debugBorderCommands()
        sink.present(cmds)
        frameSink?.invoke(cmds)
    }

    private fun debugBorderCommands(): List<RenderCommand> {
        val w = Const.SCREEN_W
        val h = Const.SCREEN_H
        val c = Const.COLOR_WHITE.toInt()
        return listOf(
            RenderCommand.BorderRect(LRect(0, 0, w, h), c, 1),
            RenderCommand.Line(w / 2, 0, w / 2, h, c),
            RenderCommand.Line(0, h / 2, w, h / 2, c),
        )
    }
}
