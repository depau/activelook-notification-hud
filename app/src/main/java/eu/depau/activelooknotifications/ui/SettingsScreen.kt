package eu.depau.activelooknotifications.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import eu.depau.activelooknotifications.BuildConfig
import eu.depau.activelooknotifications.service.GarminBridge
import eu.depau.activelooknotifications.service.NotifGlassService
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.depau.activelooknotifications.data.SettingsRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    service: NotifGlassService?,
    hasPhoneState: Boolean,
    onRequestPhoneState: () -> Unit,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val appNameMs by settings.appNameDurationMs.collectAsState(initial = 1500L)
    val peekMs by settings.peekTimeoutMs.collectAsState(initial = 5000L)
    val openMs by settings.openTimeoutMs.collectAsState(initial = 10000L)
    val brightness by settings.brightness.collectAsState(initial = 12)
    val animate by settings.animateTransitions.collectAsState(initial = true)
    val autoStartOnBoot by settings.autoStartOnBoot.collectAsState(initial = true)
    val als by settings.ambientLightSensor.collectAsState(initial = true)
    val debugBorder by settings.debugScreenBorder.collectAsState(initial = false)
    val hideMinimized by settings.hideMinimized.collectAsState(initial = false)
    val allowExternalStandby by settings.allowExternalStandby.collectAsState(initial = false)
    val autoPauseForGarmin by settings.autoPauseForGarmin.collectAsState(initial = true)
    val devices by (service?.availableDevices ?: MutableStateFlow(emptyList<NotifGlassService.GlassesDevice>()))
        .collectAsState(initial = emptyList())

    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    var tapCount by remember { mutableIntStateOf(0) }
    var debugUnlocked by remember { mutableStateOf(false) }
    var showTimingDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Connection")
            run {
                var deviceMenuOpen by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Glasses device") },
                    supportingContent = { Text("Pick which glasses to connect to") },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { deviceMenuOpen = true; service?.scanForDevices() }) { Text("Choose") }
                            DropdownMenu(expanded = deviceMenuOpen, onDismissRequest = { deviceMenuOpen = false }) {
                                if (devices.isEmpty()) {
                                    DropdownMenuItem(text = { Text("Scanning…") }, onClick = {}, enabled = false)
                                } else {
                                    devices.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d.name) },
                                            onClick = { deviceMenuOpen = false; service?.selectDevice(d.address) },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            ListItem(
                headlineContent = { Text("Forget device") },
                supportingContent = { Text("Clear the saved glasses and scan fresh") },
                trailingContent = {
                    TextButton(onClick = { service?.forgetGlasses() }) { Text("Forget") }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            SwitchRow("Start on boot", "Launch and reconnect automatically after the phone restarts", autoStartOnBoot) {
                coroutineScope.launch { settings.setAutoStartOnBoot(it) }
            }
            if (GarminBridge.SUPPORTED) {
                SwitchRow("Auto-pause for Garmin", "Release the glasses while a Garmin workout records (needs the ActiveLook Pause data field + Garmin Connect Mobile)", autoPauseForGarmin) {
                    coroutineScope.launch { settings.setAutoPauseForGarmin(it) }
                }
            }
            SwitchRow("Allow other apps to pause", "Let external apps (e.g. Tasker) pause/resume the HUD via broadcast", allowExternalStandby) {
                coroutineScope.launch { settings.setAllowExternalStandby(it) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Display")
            SwitchRow(
                "Auto-brightness (ALS)",
                "Glasses adjust brightness to ambient light. Turn off to use the slider below.",
                als,
            ) {
                coroutineScope.launch { settings.setAmbientLightSensor(it) }
            }
            SliderRow(
                label = "Brightness",
                valueText = if (als) "Auto" else "$brightness / 15",
                value = brightness.toFloat(),
                range = 0f..15f,
                steps = 14,
                onChange = { coroutineScope.launch { settings.setBrightness(it.roundToInt()) } },
            )
            ListItem(
                headlineContent = { Text("Timing") },
                supportingContent = { Text("Splash, peek & open durations") },
                trailingContent = {
                    TextButton(onClick = { showTimingDialog = true }) { Text("Adjust") }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            SwitchRow("Animate transitions", "Slide between screens on the glasses", animate) {
                coroutineScope.launch { settings.setAnimateTransitions(it) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Notifications")
            SwitchRow("Hide minimized notifications", "Do not mirror notifications with minimized or low importance", hideMinimized) {
                coroutineScope.launch { settings.setHideMinimized(it) }
            }

            if (!hasPhoneState) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Phone status")
                Text(
                    "Grant phone state to show mobile signal and network type on the idle screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestPhoneState, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Grant phone state")
                }
            }

            if (debugUnlocked) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Debug")
                SwitchRow(
                    "Screen border",
                    "Outline the full glasses buffer + center cross to calibrate the visible area.",
                    debugBorder,
                ) {
                    coroutineScope.launch { settings.setDebugScreenBorder(it) }
                }
                val preview by settings.debugPreview.collectAsState(initial = false)
                SwitchRow(
                    "Screen preview",
                    "Mirror the rendered glasses screen at the bottom of the home page.",
                    preview,
                ) {
                    coroutineScope.launch { settings.setDebugPreview(it) }
                }
                // Glasses configuration selector (pinned with cfgSet on connect).
                val selectedConfig by settings.glassesConfig.collectAsState(initial = "ALooK")
                val configNames by (service?.configNames ?: MutableStateFlow(emptyList()))
                    .collectAsState(initial = emptyList())
                val configOptions = remember(configNames, selectedConfig) {
                    (configNames + selectedConfig + "ALooK").distinct()
                }
                var configMenuOpen by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Glasses config") },
                    supportingContent = { Text("Activated on connect (cfgSet). ALooK is the documented default.") },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { configMenuOpen = true }) { Text(selectedConfig) }
                            DropdownMenu(expanded = configMenuOpen, onDismissRequest = { configMenuOpen = false }) {
                                configOptions.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            configMenuOpen = false
                                            coroutineScope.launch { settings.setGlassesConfig(name) }
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                val glassesInfo by (service?.glassesInfo ?: MutableStateFlow("Service not bound."))
                    .collectAsState(initial = "…")
                Text("Glasses info", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text(
                    glassesInfo,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            // Tap the version 7× to reveal debug options.
            Text(
                "Version $versionName (${BuildConfig.FLAVOR})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!debugUnlocked) {
                            tapCount++
                            if (tapCount >= 7) {
                                debugUnlocked = true
                                Toast.makeText(context, "Debug options unlocked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
            )
        }

        if (showTimingDialog) {
            AlertDialog(
                onDismissRequest = { showTimingDialog = false },
                confirmButton = { TextButton(onClick = { showTimingDialog = false }) { Text("Done") } },
                title = { Text("Timing") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SliderRow(
                            label = "App name splash",
                            valueText = "%.1f s".format(appNameMs / 1000f),
                            value = appNameMs.toFloat(),
                            range = 500f..5000f,
                            onChange = { coroutineScope.launch { settings.setAppNameDurationMs(it.roundToLong(250)) } },
                        )
                        SliderRow(
                            label = "Peek timeout",
                            valueText = "%.0f s".format(peekMs / 1000f),
                            value = peekMs.toFloat(),
                            range = 2000f..15000f,
                            onChange = { coroutineScope.launch { settings.setPeekTimeoutMs(it.roundToLong(500)) } },
                        )
                        SliderRow(
                            label = "Open timeout",
                            valueText = "%.0f s".format(openMs / 1000f),
                            value = openMs.toFloat(),
                            range = 5000f..30000f,
                            onChange = { coroutineScope.launch { settings.setOpenTimeoutMs(it.roundToLong(1000)) } },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun Float.roundToLong(stepMs: Int): Long {
    val v = this.roundToInt()
    return ((v + stepMs / 2) / stepMs * stepMs).toLong()
}
