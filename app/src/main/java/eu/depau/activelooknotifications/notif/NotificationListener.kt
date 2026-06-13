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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle notification", e)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        // Allowlist: show only listed apps. Denylist: show everything except listed apps.
        val listed = pkg in allowed
        if (if (denylistMode) listed else !listed) return

        val notification = sbn.notification ?: return
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (!Const.SHOW_ONGOING && flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extractBody(notification).trim()
        if (title.isEmpty() && body.isEmpty()) return

        // Dedup: ignore an identical re-post of the same key within the window.
        val contentHash = (31 * title.hashCode()) + body.hashCode()
        val now = System.currentTimeMillis()
        val previous = recent[sbn.key]
        if (previous != null && previous.first == contentHash && now - previous.second < Const.DEDUP_WINDOW_MS) {
            return
        }
        recent[sbn.key] = contentHash to now
        pruneRecent(now)

        val appName = appLabel(pkg)
        NotifRepository.publish(
            NotifItem(
                key = sbn.key ?: pkg,
                packageName = pkg,
                appName = appName,
                title = title.ifEmpty { appName },
                body = body,
                postTime = sbn.postTime,
                iconBitmap = resolveIcon(pkg, notification),
            )
        )
    }

    /**
     * Rasterise the notification's small icon (a monochrome silhouette designed for the status bar,
     * ideal for the glasses) to a mono bitmap, falling back to the app's launcher icon. Cached per
     * package since the small icon is stable per app.
     */
    private fun resolveIcon(pkg: String, notification: Notification): Bitmap? {
        iconCache[pkg]?.let { return it }
        val drawable = try {
            notification.smallIcon?.loadDrawable(this)
                ?: packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            return null
        } ?: return null
        return try {
            IconRasterizer.toMono(drawable).also { iconCache[pkg] = it }
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
