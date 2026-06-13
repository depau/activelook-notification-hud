package eu.depau.activelooknotifications.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eu.depau.activelooknotifications.data.InstalledAppsRepository
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.service.NotifGlassService

/** Top-level composable: gates on permissions, then hosts the three main screens. */
@Composable
fun AppRoot(
    service: NotifGlassService?,
    isBound: Boolean,
    settings: SettingsRepository,
    installedApps: InstalledAppsRepository,
    hasRuntimePerms: Boolean,
    hasNotifAccess: Boolean,
    hasPhoneState: Boolean,
    onRequestRuntimePerms: () -> Unit,
    onRequestNotifAccess: () -> Unit,
    onRequestPhoneState: () -> Unit,
) {
    if (!hasRuntimePerms || !hasNotifAccess) {
        OnboardingScreen(
            hasRuntimePerms = hasRuntimePerms,
            hasNotifAccess = hasNotifAccess,
            hasPhoneState = hasPhoneState,
            onRequestRuntimePerms = onRequestRuntimePerms,
            onRequestNotifAccess = onRequestNotifAccess,
            onRequestPhoneState = onRequestPhoneState,
        )
        return
    }

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                service = service,
                isBound = isBound,
                settings = settings,
                onOpenApps = { nav.navigate("apps") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("apps") {
            AppPickerScreen(
                settings = settings,
                installedApps = installedApps,
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                service = service,
                hasPhoneState = hasPhoneState,
                onRequestPhoneState = onRequestPhoneState,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
