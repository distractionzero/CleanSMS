package dev.codingcoffee.bouncer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.codingcoffee.bouncer.data.db.Label

@Composable
fun LabelFilter(selected: Set<Label>, onToggle: (Label) -> Unit, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Label.entries) { label ->
            FilterChip(
                selected = label in selected,
                onClick = { onToggle(label) },
                label = { Text(label.name) },
            )
        }
    }
}
