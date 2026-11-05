package dev.codingcoffee.bouncer.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

private val sections = listOf(
    "Everything stays on this device" to
        "Clean SMS has no internet permission. It cannot send your messages, contacts or any other data to us or to anyone else. " +
        "There are no accounts, ads, analytics or crash reporting.",
    "What Clean SMS reads" to
        "SMS and MMS messages, so it can show, send and receive them as your default SMS app. " +
        "Contacts, to show names and photos next to phone numbers. " +
        "Phone state, to find your own numbers and the SIM a message used.",
    "What Clean SMS stores" to
        "A copy of your messages is kept in a private database inside the app so the inbox and search are fast. " +
        "Your settings, such as the theme, are stored alongside it. Only Clean SMS can read this data.",
    "Sending messages" to
        "Messages you send go through your mobile carrier, just like any other SMS app. Your carrier's own privacy policy applies to them.",
    "Backups" to
        "If Android backup is turned on for your device, the system may include Clean SMS data in your device backup. " +
        "You control this in your phone's backup settings.",
    "Deleting your data" to
        "You can delete conversations, single messages or everything from Settings. Uninstalling Clean SMS removes its private database. " +
        "Messages in Android's shared SMS storage stay there until you delete them.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Privacy policy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Last updated 26 Oct 2026", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sections.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
