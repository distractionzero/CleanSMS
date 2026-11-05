package dev.codingcoffee.cleansms.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.codingcoffee.cleansms.ui.conversations.ConversationsScreen
import dev.codingcoffee.cleansms.ui.settings.SettingsScreen
import dev.codingcoffee.cleansms.ui.stats.StatsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Filled.Inbox),
    Stats("Stats", Icons.Filled.BarChart),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun HomeScreen(
    onOpenThread: (Long) -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit,
    onArchived: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onLicenses: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Inbox) }

    BackHandler(enabled = tab != Tab.Inbox) { tab = Tab.Inbox }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = it == tab,
                        onClick = { tab = it },
                        icon = { Icon(it.icon, null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Inbox -> ConversationsScreen(
                    archived = false,
                    onOpenThread = onOpenThread,
                    onNewChat = onNewChat,
                    onSearch = onSearch,
                    onArchived = onArchived,
                    onBack = {},
                )
                Tab.Stats -> StatsScreen()
                Tab.Settings -> SettingsScreen(onPrivacyPolicy, onLicenses)
            }
        }
    }
}
