package eu.depau.activelooknotifications.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.depau.activelooknotifications.data.InstalledApp
import eu.depau.activelooknotifications.data.InstalledAppsRepository
import eu.depau.activelooknotifications.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    settings: SettingsRepository,
    installedApps: InstalledAppsRepository,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val allowed by settings.allowedPackages.collectAsState(initial = emptySet())
    val denylist by settings.denylistMode.collectAsState(initial = false)
    val appsState = produceState<List<InstalledApp>?>(initialValue = null, showSystem) {
        value = installedApps.getApps(showSystem, allowed)
    }
    val apps = appsState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps to mirror") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showSystem) "Hide system apps" else "Show system apps") },
                            onClick = {
                                showSystem = !showSystem
                                menuOpen = false
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter mode: allowlist (only listed) vs denylist (all except listed).
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = !denylist,
                    onClick = { coroutineScope.launch { settings.setDenylistMode(false) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Only selected") }
                SegmentedButton(
                    selected = denylist,
                    onClick = { coroutineScope.launch { settings.setDenylistMode(true) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("All except selected") }
            }
            Text(
                if (denylist) "Mirroring all notifications except the apps selected below."
                else "Mirroring notifications only from the apps selected below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val filtered = remember(apps, query) {
                    if (query.isBlank()) apps
                    else apps.filter { it.label.contains(query, ignoreCase = true) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in allowed
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { AppIcon(app) },
                            trailingContent = {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        coroutineScope.launch { settings.setPackageAllowed(app.packageName, on) }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    val painter = remember(app.packageName) {
        BitmapPainter(app.icon.toBitmap(48, 48).asImageBitmap())
    }
    Icon(
        painter = painter,
        contentDescription = null,
        tint = androidx.compose.ui.graphics.Color.Unspecified,
        modifier = Modifier.size(40.dp),
    )
}
