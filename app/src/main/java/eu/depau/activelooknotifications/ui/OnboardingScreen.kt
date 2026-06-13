package eu.depau.activelooknotifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Stepped permission onboarding. Runtime perms + notification access gate the app; phone state is optional. */
@Composable
fun OnboardingScreen(
    hasRuntimePerms: Boolean,
    hasNotifAccess: Boolean,
    hasPhoneState: Boolean,
    onRequestRuntimePerms: () -> Unit,
    onRequestNotifAccess: () -> Unit,
    onRequestPhoneState: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Text(
                "Welcome to ActiveLook Notification HUD",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Mirror your chosen apps' notifications to ActiveLook glasses. " +
                    "Grant a couple of permissions to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))

            PermissionStep(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth & notifications",
                description = "Required to find and connect to the glasses and to run in the background.",
                granted = hasRuntimePerms,
                onAction = onRequestRuntimePerms,
            )
            PermissionStep(
                icon = Icons.Default.Notifications,
                title = "Notification access",
                description = "Lets the app read your notifications so it can show them on the glasses.",
                granted = hasNotifAccess,
                onAction = onRequestNotifAccess,
                actionLabel = "Open settings",
            )
            PermissionStep(
                icon = Icons.Default.SignalCellularAlt,
                title = "Phone state (optional)",
                description = "Shows mobile signal and network type on the idle screen. Skippable.",
                granted = hasPhoneState,
                optional = true,
                onAction = onRequestPhoneState,
            )
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit,
    optional: Boolean = false,
    actionLabel: String = "Grant",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (granted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                if (optional) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                } else {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}
