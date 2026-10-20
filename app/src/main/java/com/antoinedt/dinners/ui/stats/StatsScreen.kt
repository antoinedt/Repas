package com.antoinedt.dinners.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antoinedt.dinners.DinnersViewModel
import com.antoinedt.dinners.data.Insights
import com.antoinedt.dinners.data.MonthBucket
import com.antoinedt.dinners.ui.OtherSeriesColor
import com.antoinedt.dinners.ui.seriesColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class Period(val label: String, val days: Long?, val months: Int) {
    Month("30 days", 30, 6), Quarter("3 months", 91, 6), Year("Year", 365, 12), All("All time", null, 12)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: DinnersViewModel) {
    val dinners by vm.dinners.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(Period.Quarter) }
    val today = LocalDate.now()
    val stats = Insights.stats(dinners, period.days?.let { today.minusDays(it - 1) }, today, period.months)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach {
                FilterChip(selected = period == it, onClick = { period = it }, label = { Text(it.label) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Dinners", stats.totalDinners.toString(), Modifier.weight(1f))
            StatTile("Recipes", stats.distinctRecipes.toString(), Modifier.weight(1f))
            StatTile(
                "Days logged",
                "${(stats.daysLogged * 100.0 / stats.daysInPeriod).roundToInt()}%",
                Modifier.weight(1f),
            )
        }

        if (stats.totalDinners == 0) {
            Text("No dinners logged in this period yet.", style = MaterialTheme.typography.bodyLarge)
        }

        if (stats.shares.isNotEmpty()) {
            Section("Share of dinners") {
                val max = stats.shares.first().count.toFloat()
                stats.shares.forEach { share ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Text(share.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${share.count}× · ${"%.0f".format(share.pct)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth(share.count / max).height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(seriesColors().first()),
                        )
                    }
                }
            }
        }

        Section("Monthly trend") {
            TrendChart(stats.months, dinners.associate { it.recipeId to it.recipeName })
        }

        if (stats.byWeekday.isNotEmpty()) {
            Section("Favourite by weekday") {
                DayOfWeek.entries.forEach { day ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault()), Modifier.width(110.dp))
                        val fav = stats.byWeekday[day]
                        Text(
                            fav?.let { "${it.first} (${it.second}×)" } ?: "—",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * Stacked monthly bars: the window's top 5 recipes keep a fixed colour each, the rest fold into "Other".
 * Tap a bar to see that month's breakdown.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TrendChart(months: List<MonthBucket>, names: Map<Long, String>) {
    val palette = seriesColors()
    val totals = months.flatMap { it.counts.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
    val top = totals.entries.sortedByDescending { it.value }.take(palette.size).map { it.key }
    val colorOf = top.withIndex().associate { (i, id) -> id to palette[i] }
    val maxTotal = months.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    var selected by rememberSaveable { mutableStateOf(-1) }
    val gap = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant

    if (totals.isEmpty()) {
        Text("Log some dinners to see trends.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Canvas(
        Modifier.fillMaxWidth().height(180.dp).pointerInput(months.size) {
            detectTapGestures { pos ->
                val i = (pos.x / (size.width.toFloat() / months.size)).toInt()
                selected = if (selected == i) -1 else i.coerceIn(0, months.size - 1)
            }
        }
    ) {
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        val slot = size.width / months.size
        val barW = slot * 0.6f
        months.forEachIndexed { i, m ->
            val x = i * slot + (slot - barW) / 2
            var y = size.height
            val segments = top.mapNotNull { id -> m.counts[id]?.let { it to colorOf.getValue(id) } } +
                listOfNotNull((m.total - top.sumOf { m.counts[it] ?: 0 }).takeIf { it > 0 }?.let { it to OtherSeriesColor })
            segments.forEachIndexed { s, (count, color) ->
                val h = count / maxTotal.toFloat() * (size.height - 4f)
                val isTop = s == segments.lastIndex
                drawSegment(color, x, y - h, barW, h, roundTop = isTop, alpha = if (selected == -1 || selected == i) 1f else 0.35f)
                // 2px surface gap between stacked segments.
                if (!isTop) drawRect(gap, Offset(x, y - h - 1f), Size(barW, 2f))
                y -= h
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        months.forEach {
            Text(
                it.month.format(DateTimeFormatter.ofPattern("MMM")),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        top.forEach { LegendItem(colorOf.getValue(it), names[it] ?: "?") }
        if (totals.size > top.size) LegendItem(OtherSeriesColor, "Other")
    }
    months.getOrNull(selected)?.let { m ->
        Spacer(Modifier.height(8.dp))
        Text(
            m.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + " · ${m.total} dinners",
            style = MaterialTheme.typography.titleSmall,
        )
        m.counts.entries.sortedByDescending { it.value }.forEach { (id, n) ->
            Text("${names[id] ?: "?"}: $n", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun DrawScope.drawSegment(color: Color, x: Float, y: Float, w: Float, h: Float, roundTop: Boolean, alpha: Float) {
    if (h <= 0f) return
    if (!roundTop) {
        drawRect(color, Offset(x, y), Size(w, h), alpha = alpha)
        return
    }
    val r = minOf(4.dp.toPx(), h, w / 2)
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = x, top = y, right = x + w, bottom = y + h,
                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, color, alpha = alpha)
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
