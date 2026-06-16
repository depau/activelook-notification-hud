package eu.depau.activelooknotifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import eu.depau.activelooknotifications.data.SettingsRepository
import eu.depau.activelooknotifications.ui.SettingsScreen
import eu.depau.activelooknotifications.ui.theme.NotificationHudTheme

class SettingsActivity : BoundServiceActivity() {

    private val settings by lazy { SettingsRepository(applicationContext) }
    private var hasPhoneState by mutableStateOf(false)

    private val requestPhoneState = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPhoneState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPhoneState()
        setContent {
            NotificationHudTheme {
                SettingsScreen(
                    settings = settings,
                    service = service,
                    hasPhoneState = hasPhoneState,
                    onRequestPhoneState = { requestPhoneState.launch(Manifest.permission.READ_PHONE_STATE) },
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPhoneState()
    }

    private fun refreshPhoneState() {
        hasPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
