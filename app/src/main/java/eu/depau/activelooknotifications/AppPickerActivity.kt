package eu.depau.activelooknotifications

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.depau.activelooknotifications.data.InstalledAppsRepository
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.ui.AppPickerScreen
import eu.depau.activelooknotifications.ui.theme.NotificationHudTheme

class AppPickerActivity : ComponentActivity() {

    private val settings by lazy { SettingsRepository(applicationContext) }
    private val installedApps by lazy { InstalledAppsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificationHudTheme {
                AppPickerScreen(
                    settings = settings,
                    installedApps = installedApps,
                    onBack = ::finish,
                )
            }
        }
    }
}
