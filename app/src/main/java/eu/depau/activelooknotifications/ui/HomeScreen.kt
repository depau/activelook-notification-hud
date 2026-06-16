package eu.depau.activelooknotifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.depau.activelooknotifications.service.NotifGlassService
import eu.depau.activelooknotifications.service.NotifGlassService.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    service: NotifGlassService?,
    isBound: Boolean,
    settings: eu.depau.activelooknotifications.data.SettingsRepository,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("ActiveLook Notification HUD") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (service == null || !isBound) {
                Text("Starting service…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            val state by service.glassesState.collectAsState()
            val status by service.statusMessage.collectAsState()
            val standby by service.standby.collectAsState()

            ConnectionCard(
                state = state,
                status = status,
                standby = standby,
                onStandbyToggle = { if (standby) service.exitStandby() else service.enterStandby() },
            )

            NavRow(
                icon = Icons.Default.Apps,
                title = "App notifications",
                subtitle = "Choose which apps appear on the glasses",
                onClick = onOpenApps,
            )
            NavRow(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "Connection, display, notifications",
                onClick = onOpenSettings,
            )

            val showPreview by settings.debugPreview.collectAsState(initial = false)
            if (showPreview) {
                val frame by service.lastFrame.collectAsState()
                Text("Glasses preview", style = MaterialTheme.typography.titleSmall)
                GlassesPreview(frame, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: ConnectionState,
    status: String,
    standby: Boolean,
    onStandbyToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(state)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Glasses connection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (standby) "Paused" else stateLabel(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onStandbyToggle) {
                    if (standby) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    } else {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                }
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF2E7D32)
        ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFF9A825)
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
    Spacer(
        Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

private fun stateLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.CONNECTING -> "Connecting…"
    ConnectionState.SCANNING -> "Searching…"
    ConnectionState.ERROR -> "Error"
    ConnectionState.DISCONNECTED -> "Off"
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
