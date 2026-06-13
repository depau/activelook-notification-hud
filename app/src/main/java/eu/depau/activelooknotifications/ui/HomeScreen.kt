package eu.depau.activelooknotifications.ui

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

            val running by service.running.collectAsState()
            val state by service.glassesState.collectAsState()
            val status by service.statusMessage.collectAsState()
            val devices by service.availableDevices.collectAsState()

            // Reflect connectivity even if the user-intent flag and BLE state momentarily disagree.
            val connectionOn = running || state != ConnectionState.DISCONNECTED
            ConnectionCard(
                running = connectionOn,
                state = state,
                status = status,
                devices = devices,
                onToggle = { on -> if (on) service.connectGlasses() else service.disconnectGlasses() },
                onForget = { service.forgetGlasses() },
                onScanDevices = { service.scanForDevices() },
                onSelectDevice = { service.selectDevice(it) },
            )

            NavRow(
                icon = Icons.Default.Apps,
                title = "Apps to mirror",
                subtitle = "Choose which apps appear on the glasses",
                onClick = onOpenApps,
            )
            NavRow(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "Timeouts, brightness, animation",
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
    running: Boolean,
    state: ConnectionState,
    status: String,
    devices: List<NotifGlassService.GlassesDevice>,
    onToggle: (Boolean) -> Unit,
    onForget: () -> Unit,
    onScanDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
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
                        stateLabel(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = running, onCheckedChange = onToggle)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { menuOpen = true; onScanDevices() }) { Text("Choose device") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (devices.isEmpty()) {
                            DropdownMenuItem(text = { Text("Scanning…") }, onClick = {}, enabled = false)
                        } else {
                            devices.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d.name) },
                                    onClick = { menuOpen = false; onSelectDevice(d.address) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onForget) { Text("Forget device") }
            }
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
