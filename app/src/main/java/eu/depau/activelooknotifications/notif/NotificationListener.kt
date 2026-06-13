package eu.depau.activelooknotifications.notif

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import eu.depau.activelooknotifications.Const
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.display.NotifItem
import eu.depau.activelooknotifications.glasses.IconRasterizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens to system notifications, filters them against the user's allow-set, extracts a
 * glasses-friendly [NotifItem], de-duplicates re-posts and forwards to [NotifRepository].
 *
 * Runs in its own process component, so it keeps a private copy of the allow-set fresh by
 * collecting DataStore here — toggling an app in the picker then takes effect without a restart.
 */
class NotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsRepository

    @Volatile
    private var allowed: Set<String> = emptySet()

    @Volatile
    private var denylistMode: Boolean = false

    // Tiny dedup memory: key -> (contentHash, timestamp).
    private val recent = HashMap<String, Pair<Int, Long>>()

    // Cached monochrome icon per package (small icons are stable per app).
    private val iconCache = HashMap<String, Bitmap>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(applicationContext)
        scope.launch {
            settings.allowedPackages.collect { allowed = it }
        }
        scope.launch {
            settings.denylistMode.collect { denylistMode = it }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotifRepository.activeProvider = { snapshotActive() }
    }

    override fun onListenerDisconnected() {
        NotifRepository.activeProvider = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle notification", e)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val item = mapToItem(sbn, Const.ICON_SIZE) ?: return

        // Dedup: ignore an identical re-post of the same key within the window.
        val contentHash = (31 * item.title.hashCode()) + item.body.hashCode()
        val now = System.currentTimeMillis()
        val previous = recent[item.key]
        if (previous != null && previous.first == contentHash && now - previous.second < Const.DEDUP_WINDOW_MS) {
            return
        }
        recent[item.key] = contentHash to now
        pruneRecent(now)

        NotifRepository.publish(item)
    }

    /**
     * Live snapshot of every currently-posted notification, mapped + filtered like [handle] (minus
     * dedup, which is point-in-time meaningless), newest→oldest, capped. Returns empty if the
     * listener isn't connected ([activeNotifications] throws/null before connection).
     */
    private fun snapshotActive(): List<NotifItem> {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            return emptyList()
        } ?: return emptyList()
        return active.asSequence()
            .mapNotNull { runCatching { mapToItem(it, Const.LIST_ICON_SIZE) }.getOrNull() }
            .sortedByDescending { it.postTime }
            .take(Const.LIST_MAX_NOTIFS)
            .toList()
    }

    /**
     * Map a [StatusBarNotification] to a [NotifItem], applying the allow/deny + group-summary/ongoing
     * filters and title/body/icon extraction shared by the live (peek) and snapshot (list) paths.
     * [iconSize] selects which rasterized icon is populated ([Const.ICON_SIZE] splash vs
     * [Const.LIST_ICON_SIZE] header). Returns null if the notification should be dropped.
     */
    private fun mapToItem(sbn: StatusBarNotification, iconSize: Int): NotifItem? {
        val pkg = sbn.packageName ?: return null
        if (pkg == packageName) return null
        // Allowlist: show only listed apps. Denylist: show everything except listed apps.
        val listed = pkg in allowed
        if (if (denylistMode) listed else !listed) return null

        val notification = sbn.notification ?: return null
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (!Const.SHOW_ONGOING && flags and Notification.FLAG_ONGOING_EVENT != 0) return null

        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extractBody(notification).trim()
        if (title.isEmpty() && body.isEmpty()) return null

        val appName = appLabel(pkg)
        val icon = resolveIcon(pkg, notification, iconSize)
        return NotifItem(
            key = sbn.key ?: pkg,
            packageName = pkg,
            appName = appName,
            title = title.ifEmpty { appName },
            body = body,
            postTime = sbn.postTime,
            iconBitmap = if (iconSize == Const.ICON_SIZE) icon else null,
            listIconBitmap = if (iconSize == Const.LIST_ICON_SIZE) icon else null,
        )
    }

    /**
     * Rasterise the notification's small icon (a monochrome silhouette designed for the status bar,
     * ideal for the glasses) to a mono bitmap at [size], falling back to the app's launcher icon.
     * Cached per package+size (the splash uses 48px, the list header uses [Const.LIST_ICON_SIZE]).
     */
    private fun resolveIcon(pkg: String, notification: Notification, size: Int): Bitmap? {
        val ck = "$pkg@$size"
        iconCache[ck]?.let { return it }
        val drawable = try {
            notification.smallIcon?.loadDrawable(this)
                ?: packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            return null
        } ?: return null
        return try {
            IconRasterizer.toMono(drawable, size).also { iconCache[ck] = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractBody(notification: Notification): String {
        val extras = notification.extras
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { return it.toString() }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { return it.toString() }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) {
            return lines.joinToString("\n") { it.toString() }
        }
        return ""
    }

    private fun appLabel(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun pruneRecent(now: Long) {
        if (recent.size < 64) return
        val it = recent.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value.second > 10 * Const.DEDUP_WINDOW_MS) it.remove()
        }
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
