package dev.codingcoffee.cleansms.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.codingcoffee.cleansms.container
import dev.codingcoffee.cleansms.data.contacts.Contact
import dev.codingcoffee.cleansms.data.contacts.SenderCode
import dev.codingcoffee.cleansms.data.contacts.SenderEntity
import dev.codingcoffee.cleansms.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(address: String, onBack: () -> Unit) {
    val senders = LocalContext.current.container.senders
    val entity by produceState<SenderEntity?>(null, address) { value = senders.entityOf(address) }
    val code by produceState<SenderCode?>(null, address) { value = senders.codeOf(address) }
    val contacts = remember(address) { listOf(Contact(address, null, null)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sender details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(contacts, size = 72.dp)
                Text(entity?.name ?: address, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (entity != null) {
                    Text(address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            entity?.let { info ->
                DetailRow("Sender ID", info.header)
                info.address?.let { DetailRow("Registered address", it) }
            }
            code?.let { info ->
                info.type?.let { DetailRow("Message type", it) }
                info.operator?.let { DetailRow("Telecom operator", it) }
                info.serviceArea?.let { DetailRow("Service area", it) }
            }
        }
    }
}
