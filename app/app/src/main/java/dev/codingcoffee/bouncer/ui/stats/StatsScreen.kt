package dev.codingcoffee.bouncer.ui.stats

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import dev.codingcoffee.bouncer.data.db.Label
import dev.codingcoffee.bouncer.ui.components.LabelFilter
import java.time.LocalTime
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt

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
            var range by rememberSaveable { mutableStateOf(Range.WEEK) }
            val today = remember { LocalDate.now() }
            val since = remember(range) {
                range.days?.let { today.minusDays(it - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: 0L
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Range.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = range == option,
                        onClick = { range = option },
                        shape = SegmentedButtonDefaults.itemShape(index, Range.entries.size),
                    ) { Text(option.label) }
                }
            }
            ReceivedPerDay(range, since, today)
            ReceivedPerHour(since)
            ReceivedPerLabel(since)
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
private fun ChartTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
    }
}

@Composable
private fun ReceivedPerDay(range: Range, since: Long, today: LocalDate) {
    val container = LocalContext.current.container
    val dailyFlow = remember(since) { container.db.messages().receivedPerDay(since) }
    val daily by dailyFlow.collectAsStateWithLifecycle(emptyList())
    val points = remember(daily, range) { fillDays(daily, range, today) }

    ChartTitle("Received per day")
    ChartCard { DailyChart(points, Modifier.fillMaxWidth().height(180.dp)) }
}

@Composable
private fun ReceivedPerHour(since: Long) {
    val container = LocalContext.current.container
    var label by rememberSaveable { mutableStateOf<Label?>(null) }
    val hourlyFlow = remember(since, label) { container.db.messages().receivedPerHour(since, label) }
    val hourly by hourlyFlow.collectAsStateWithLifecycle(emptyList())
    val counts = remember(hourly) { IntArray(24).also { array -> hourly.forEach { array[it.hour] = it.count } } }

    ChartTitle("Received by time of day")
    LabelFilter(selected = label, onToggle = { label = if (label == it) null else it }, contentPadding = PaddingValues(0.dp))
    ChartCard { HourlyChart(counts, Modifier.fillMaxWidth().height(180.dp)) }
}

@Composable
private fun ReceivedPerLabel(since: Long) {
    val container = LocalContext.current.container
    val perLabelFlow = remember(since) { container.db.messages().receivedPerLabel(since) }
    val perLabel by perLabelFlow.collectAsStateWithLifecycle(emptyList())
    val rows = remember(perLabel) {
        val counts = perLabel.associate { it.label to it.count }
        Label.entries.map { it to (counts[it] ?: 0) }.sortedByDescending { it.second }
    }
    val peak = rows.maxOf { it.second }
    val numberFormat = NumberFormat.getIntegerInstance()

    ChartTitle("Received per label")
    ChartCard {
        if (peak == 0) {
            Text("No labelled messages yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label.name, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(count.toFloat() / peak)
                                .height(12.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text(
                        numberFormat.format(count),
                        modifier = Modifier.width(56.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawTooltip(
    anchor: Offset,
    title: TextLayoutResult,
    value: TextLayoutResult,
    dotColor: Color,
    background: Color,
    border: Color,
) {
    val padding = 10.dp.toPx()
    val dotRadius = 4.dp.toPx()
    val dotSpace = dotRadius * 2 + 6.dp.toPx()
    val box = Size(
        maxOf(title.size.width.toFloat(), dotSpace + value.size.width) + padding * 2,
        title.size.height + value.size.height + padding * 2,
    )
    val gap = 10.dp.toPx()
    val left = (anchor.x - box.width / 2).coerceIn(0f, size.width - box.width)
    val boxTop = (anchor.y - gap - box.height).takeIf { it >= 0f } ?: (anchor.y + gap)
    val corner = CornerRadius(8.dp.toPx())
    drawRoundRect(background, Offset(left, boxTop), box, corner)
    drawRoundRect(border, Offset(left, boxTop), box, corner, style = Stroke(1.dp.toPx()))
    drawText(title, topLeft = Offset(left + padding, boxTop + padding))
    val valueTop = boxTop + padding + title.size.height
    drawCircle(dotColor, radius = dotRadius, center = Offset(left + padding + dotRadius, valueTop + value.size.height / 2f))
    drawText(value, topLeft = Offset(left + padding + dotSpace, valueTop))
}

private fun Modifier.touchX(onChange: (Float?) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        onChange(awaitFirstDown().position.x)
        do {
            val change = awaitPointerEvent().changes.first()
            if (change.pressed) onChange(change.position.x)
        } while (change.pressed)
        onChange(null)
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
    val tooltipDateStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipValueStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val tooltipColor = MaterialTheme.colorScheme.surface
    val tooltipBorder = MaterialTheme.colorScheme.outline
    var touchX by remember { mutableStateOf<Float?>(null) }

    Canvas(modifier.touchX { touchX = it }) {
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

        val selected = touchX?.let { if (step > 0f) ((it - plotLeft) / step).roundToInt().coerceIn(0, points.lastIndex) else 0 }
            ?: return@Canvas
        val (date, count) = points[selected]
        val point = Offset(x(selected), y(count))
        drawCircle(lineColor, radius = 5.dp.toPx(), center = point)

        drawTooltip(
            point,
            measurer.measure(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), tooltipDateStyle),
            measurer.measure("${numberFormat.format(count)} received", tooltipValueStyle),
            lineColor, tooltipColor, tooltipBorder,
        )
    }
}

@Composable
private fun HourlyChart(counts: IntArray, modifier: Modifier) {
    val context = LocalContext.current
    val hourFormat = remember { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h a") }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = MaterialTheme.colorScheme.primary
    val tooltipTitleStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipValueStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val tooltipColor = MaterialTheme.colorScheme.surface
    val tooltipBorder = MaterialTheme.colorScheme.outline
    val numberFormat = NumberFormat.getIntegerInstance()
    val peak = counts.max()
    val top = maxOf(2, peak + peak % 2)
    var touchX by remember { mutableStateOf<Float?>(null) }

    fun hour(h: Int) = LocalTime.of(h % 24, 0).format(hourFormat)

    Canvas(modifier.touchX { touchX = it }) {
        val yLabels = listOf(top, top / 2, 0).map { measurer.measure(numberFormat.format(it), labelStyle) }
        val xTicks = listOf(0, 6, 12, 18)
        val xLabels = xTicks.map { measurer.measure(hour(it), labelStyle) }
        val gutter = yLabels.maxOf { it.size.width } + 12.dp.toPx()
        val chartTop = yLabels.first().size.height / 2f
        val chartBottom = size.height - xLabels.first().size.height - 8.dp.toPx()
        val plotLeft = gutter + 4.dp.toPx()
        val slot = (size.width - plotLeft) / 24
        val gap = 2.dp.toPx()
        fun y(value: Int) = chartBottom - value.toFloat() / top * (chartBottom - chartTop)

        listOf(top, top / 2, 0).forEachIndexed { i, value ->
            val label = yLabels[i]
            drawText(label, topLeft = Offset(0f, y(value) - label.size.height / 2f))
            drawLine(gridColor, Offset(gutter, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
        }
        xTicks.forEachIndexed { i, h ->
            val label = xLabels[i]
            val left = (plotLeft + h * slot).coerceIn(gutter, size.width - label.size.width)
            drawText(label, topLeft = Offset(left, size.height - label.size.height))
        }

        val selected = touchX?.let { ((it - plotLeft) / slot).toInt().coerceIn(0, 23) }
        counts.forEachIndexed { h, count ->
            if (count == 0) return@forEachIndexed
            val rect = Rect(plotLeft + h * slot + gap / 2, y(count), plotLeft + (h + 1) * slot - gap / 2, chartBottom)
            val radius = CornerRadius(minOf(4.dp.toPx(), rect.width / 2, rect.height))
            val path = Path().apply { addRoundRect(RoundRect(rect, topLeft = radius, topRight = radius)) }
            drawPath(path, if (selected == null || selected == h) barColor else barColor.copy(alpha = 0.4f))
        }

        selected ?: return@Canvas
        drawTooltip(
            Offset(plotLeft + (selected + 0.5f) * slot, y(counts[selected])),
            measurer.measure("${hour(selected)} – ${hour(selected + 1)}", tooltipTitleStyle),
            measurer.measure("${numberFormat.format(counts[selected])} received", tooltipValueStyle),
            barColor, tooltipColor, tooltipBorder,
        )
    }
}
