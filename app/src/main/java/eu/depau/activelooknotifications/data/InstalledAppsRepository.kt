package eu.depau.activelooknotifications.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable installed app shown in the picker. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
)

/** Queries the [PackageManager] for apps the user might want to mirror notifications from. */
class InstalledAppsRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    /**
     * Returns launchable apps sorted by label. Includes apps with a launcher entry plus any
     * already-allowed packages (so a selected app never silently vanishes from the list).
     */
    suspend fun getApps(includeSystem: Boolean, allowed: Set<String>): List<InstalledApp> = withContext(Dispatchers.IO) {
        val seen = HashSet<String>()
        val result = ArrayList<InstalledApp>()

        // 1. Add all launchable apps
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
        for (ri in resolved) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            val pkg = ai.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue
            result.add(
                InstalledApp(
                    packageName = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai),
                    isSystem = isSystem,
                )
            )
        }

        // 2. Add all already-allowed apps so they don't vanish
        for (pkg in allowed) {
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                result.add(
                    InstalledApp(
                        packageName = pkg,
                        label = pm.getApplicationLabel(ai).toString(),
                        icon = pm.getApplicationIcon(ai),
                        isSystem = isSystem,
                    )
                )
            } catch (e: Exception) {
                // Package not installed anymore
            }
        }

        // 3. If includeSystem is true, add all other installed applications
        if (includeSystem) {
            val allApps = pm.getInstalledApplications(0)
            for (ai in allApps) {
                val pkg = ai.packageName
                if (pkg == context.packageName) continue
                if (!seen.add(pkg)) continue
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                result.add(
                    InstalledApp(
                        packageName = pkg,
                        label = pm.getApplicationLabel(ai).toString(),
                        icon = pm.getApplicationIcon(ai),
                        isSystem = isSystem,
                    )
                )
            }
        }

        result.sortedBy { it.label.lowercase() }
    }
}
