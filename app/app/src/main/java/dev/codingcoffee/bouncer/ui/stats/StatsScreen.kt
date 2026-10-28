package dev.codingcoffee.bouncer.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.db.DailyCount
import dev.codingcoffee.bouncer.data.db.MessageStats
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class Range(val label: String, val days: Long?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("All", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val container = LocalContext.current.container
    val statsFlow = remember { container.db.messages().stats() }
    val stats by statsFlow.collectAsStateWithLifecycle(MessageStats(0, 0, 0, 0))
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Stats") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBlock("Total messages", stats.total, Modifier.weight(1f))
                StatBlock("Conversations", stats.conversations, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBlock("Received", stats.received, Modifier.weight(1f))
                StatBlock("Sent", stats.sent, Modifier.weight(1f))
            }
            ReceivedPerDay()
        }
    }
}

@Composable
private fun StatBlock(label: String, value: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(NumberFormat.getIntegerInstance().format(value), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReceivedPerDay() {
    val container = LocalContext.current.container
    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    val today = remember { LocalDate.now() }
    val dailyFlow = remember(range) {
        val since = range.days
            ?.let { today.minusDays(it - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            ?: 0L
        container.db.messages().receivedPerDay(since)
    }
    val daily by dailyFlow.collectAsStateWithLifecycle(emptyList())
    val points = remember(daily, range) { fillDays(daily, range, today) }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Range.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = range == option,
                onClick = { range = option },
                shape = SegmentedButtonDefaults.itemShape(index, Range.entries.size),
            ) { Text(option.label) }
        }
    }
    Text(
        "Received per day",
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            DailyChart(points, Modifier.fillMaxWidth().height(180.dp))
        }
    }
}

private fun fillDays(daily: List<DailyCount>, range: Range, today: LocalDate): List<Pair<LocalDate, Int>> {
    val counts = daily.associate { LocalDate.parse(it.day) to it.count }
    val start = range.days?.let { today.minusDays(it - 1) } ?: counts.keys.minOrNull()?.coerceAtMost(today) ?: today
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { it to (counts[it] ?: 0) }
        .toList()
}

@Composable
private fun DailyChart(points: List<Pair<LocalDate, Int>>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val peak = points.maxOf { it.second }
    val top = maxOf(2, peak + peak % 2)
    val span = ChronoUnit.DAYS.between(points.first().first, points.last().first)
    val dateFormat = DateTimeFormatter.ofPattern(if (span > 365) "MMM yy" else "d MMM")
    val numberFormat = NumberFormat.getIntegerInstance()
    val xLabelIndices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()

    Canvas(modifier) {
        val yLabels = listOf(top, top / 2, 0).map { measurer.measure(numberFormat.format(it), labelStyle) }
        val xLabels = xLabelIndices.map { measurer.measure(points[it].first.format(dateFormat), labelStyle) }
        val gutter = yLabels.maxOf { it.size.width } + 12.dp.toPx()
        val chartTop = yLabels.first().size.height / 2f
        val chartBottom = size.height - xLabels.first().size.height - 8.dp.toPx()
        val plotLeft = gutter + 16.dp.toPx()
        val plotRight = size.width - 16.dp.toPx()
        val step = if (points.size > 1) (plotRight - plotLeft) / points.lastIndex else 0f
        fun x(index: Int) = if (points.size > 1) plotLeft + index * step else (plotLeft + plotRight) / 2
        fun y(value: Int) = chartBottom - value.toFloat() / top * (chartBottom - chartTop)

        listOf(top, top / 2, 0).forEachIndexed { i, value ->
            val label = yLabels[i]
            drawText(label, topLeft = Offset(0f, y(value) - label.size.height / 2f))
            drawLine(gridColor, Offset(gutter, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
        }
        xLabelIndices.forEachIndexed { i, index ->
            val label = xLabels[i]
            val left = (x(index) - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
            drawText(label, topLeft = Offset(left, size.height - label.size.height))
        }

        val path = Path()
        points.forEachIndexed { index, (_, count) ->
            if (index == 0) path.moveTo(x(index), y(count)) else path.lineTo(x(index), y(count))
        }
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (points.size <= 31) points.forEachIndexed { index, (_, count) ->
            drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x(index), y(count)))
        }
    }
}
