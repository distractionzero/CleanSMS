package dev.codingcoffee.bouncer.ui.settings

import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codingcoffee.bouncer.BuildConfig
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.DebugSeeder
import dev.codingcoffee.bouncer.data.db.MessageStats
import dev.codingcoffee.bouncer.data.prefs.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val container = context.container
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var seeding by remember { mutableStateOf<String?>(null) }
    val statsFlow = remember { container.db.messages().stats() }
    val stats by statsFlow.collectAsStateWithLifecycle(MessageStats(0, 0, 0, 0))
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader("Appearance")
            SettingsCard {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowIcon(Icons.Filled.Palette)
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == themeMode,
                            onClick = {
                                container.settings.setThemeMode(mode)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
                                        when (mode) {
                                            ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                                            ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                                            ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                                        },
                                    )
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) { Text(mode.name) }
                    }
                }
            }

            SectionHeader("General")
            SettingsCard {
                SettingRow(Icons.Filled.Notifications, "Notifications", "Sound, vibration and pop-up") {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
                SettingRow(Icons.Filled.Sms, "Default SMS app", "Active. Bouncer is your default SMS app.") {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }

            SectionHeader("Data")
            SettingsCard {
                SettingRow(
                    Icons.Filled.DeleteForever,
                    "Delete all messages",
                    if (deleting) "Deleting…" else "${stats.total} messages in ${stats.conversations} conversations",
                    chevron = false,
                    destructive = true,
                ) {
                    if (!deleting && stats.conversations > 0) confirmDeleteAll = true
                }
            }

            if (BuildConfig.DEBUG) {
                SectionHeader("Debug")
                SettingsCard {
                    SettingRow(Icons.Filled.Storage, "Seed 50,000 test messages", seeding ?: "Local database only", chevron = false) {
                        if (seeding != null) return@SettingRow
                        seeding = "Seeding…"
                        scope.launch {
                            DebugSeeder.seed(container.db, container.sync)
                            seeding = "Done"
                        }
                    }
                    SettingRow(Icons.Filled.DeleteSweep, "Remove test messages", null, chevron = false) {
                        scope.launch { DebugSeeder.clear(container.db) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all messages?") },
            text = { Text("All conversations, including archived ones, will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    deleting = true
                    container.scope.launch {
                        container.messaging.deleteAll()
                        deleting = false
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, null, tint = tint)
    Spacer(Modifier.width(20.dp))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    chevron: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (destructive) RowIcon(icon, MaterialTheme.colorScheme.error) else RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
