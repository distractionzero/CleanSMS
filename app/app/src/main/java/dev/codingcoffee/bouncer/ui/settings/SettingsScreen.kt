package dev.codingcoffee.bouncer.ui.settings

import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codingcoffee.bouncer.BuildConfig
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.DebugSeeder
import dev.codingcoffee.bouncer.data.prefs.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var seeding by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Theme")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = mode == themeMode, role = Role.RadioButton, onClick = {
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
                        })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == themeMode, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        when (mode) {
                            ThemeMode.System -> "System default"
                            ThemeMode.Light -> "Light"
                            ThemeMode.Dark -> "Dark"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("General")
            SettingRow("Notifications", "Sound, vibration and pop-up") {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
            SettingRow("Default SMS app", "Bouncer") {
                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            if (BuildConfig.DEBUG) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Debug")
                SettingRow("Seed 50,000 test messages", seeding ?: "Local database only") {
                    if (seeding != null) return@SettingRow
                    seeding = "Seeding…"
                    scope.launch {
                        DebugSeeder.seed(container.db, container.sync)
                        seeding = "Done"
                    }
                }
                SettingRow("Remove test messages", null) {
                    scope.launch { DebugSeeder.clear(container.db) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
