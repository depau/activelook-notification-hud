package eu.depau.activelooknotifications

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.ui.HomeScreen
import eu.depau.activelooknotifications.ui.OnboardingScreen
import eu.depau.activelooknotifications.ui.theme.NotificationHudTheme

class MainActivity : BoundServiceActivity() {

    private var hasRuntimePerms by mutableStateOf(false)
    private var hasNotifAccess by mutableStateOf(false)

    private val settings by lazy { SettingsRepository(applicationContext) }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshPermissionState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissionState()

        setContent {
            NotificationHudTheme {
                if (!hasRuntimePerms || !hasNotifAccess) {
                    OnboardingScreen(
                        hasRuntimePerms = hasRuntimePerms,
                        hasNotifAccess = hasNotifAccess,
                        hasPhoneState = hasPhoneState(),
                        onRequestRuntimePerms = { requestPermissions.launch(requiredPermissions()) },
                        onRequestNotifAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onRequestPhoneState = {
                            requestPermissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
                        },
                    )
                } else {
                    HomeScreen(
                        service = service,
                        isBound = isBound,
                        settings = settings,
                        onOpenApps = { startActivity(Intent(this, AppPickerActivity::class.java)) },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        hasRuntimePerms = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        hasNotifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun hasPhoneState(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }
}
