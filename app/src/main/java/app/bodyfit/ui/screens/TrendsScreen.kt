package app.bodyfit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.Dates
import app.bodyfit.data.HourlyRecord
import app.bodyfit.data.UserSettings
import app.bodyfit.data.WaterEntry
import app.bodyfit.insights.Insights
import app.bodyfit.ui.Metric
import app.bodyfit.ui.components.Glyph
import app.bodyfit.ui.components.ProgressMeter
import app.bodyfit.ui.components.SectionHeader
import app.bodyfit.ui.components.WeeklyBarChart
import app.bodyfit.ui.theme.LocalViz
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The three spans the screen can draw, each a whole calendar period rather than a rolling one. */
private enum class Window(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
}

/**
 * One metric at a time over a day, a week or a month.
 *
 * Day draws the 24 hours of the active date. Week is the calendar week starting Monday
 * and Month is the calendar month, both a bar per day, and tapping a bar opens that day
 * hour by hour underneath. Every span is filled end to end, so a day or hour with no
 * activity shows as an empty slot rather than closing the gap and changing the shape.
 *
 * The metric chips are a filter, not a legend: only the chosen metric is on screen, so
 * its hue is the only data color visible and no two hues ever have to be told apart. The
 * values table under the chart carries the same numbers as text.
 */
@Composable
fun TrendsScreen(
    allDays: List<DailyRecord>,
    settings: UserSettings,
    activeDate: String,
    /** The 24 hours of whichever day the screen is showing, supplied by the view model. */
    hours: List<HourlyRecord>,
    /** Drinks logged on that same day, the source of the water bars. */
    hourlyWater: List<WaterEntry>,
    /** Called with the day whose hours are needed, or null when none are. */
    onSelectDay: (String?) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    var metric by rememberSaveable { mutableStateOf(Metric.STEPS) }
    var window by rememberSaveable { mutableStateOf(Window.WEEK) }
    var selectedBar by rememberSaveable { mutableStateOf<Int?>(null) }
    var showTable by rememberSaveable { mutableStateOf(false) }

    /** How many spans back from today the screen is looking. 0 is the current one. */
    var offset by rememberSaveable { mutableIntStateOf(0) }

    // Changing span with an offset held would land somewhere arbitrary: four weeks back is
    // not four days back. Every change of span returns to the present.
    LaunchedEffect(window) { offset = 0 }

    val today = remember(activeDate) { Dates.parse(activeDate) }
    val anchor = remember(today, window, offset) {
        when (window) {
            Window.DAY -> today.minusDays(offset.toLong())
            Window.WEEK -> today.minusWeeks(offset.toLong())
            Window.MONTH -> today.minusMonths(offset.toLong())
        }
    }
    val viewDate = remember(anchor) { anchor.toString() }
    val days = remember(allDays, window, anchor) {
        if (window == Window.DAY) {
            emptyList()
        } else {
            val byDate = allDays.associateBy { it.date }
            val keys = if (window == Window.WEEK) Dates.weekKeys(anchor) else Dates.monthKeys(anchor)
            keys.map { byDate[it] ?: DailyRecord(date = it) }
        }
    }

    // In Day the screen needs the active date's hours; in the other spans only a tapped bar
    // does. Either way the rows live in the view model, so the screen asks for the day it is
    // showing rather than holding a second copy of the query.
    val tappedDate = selectedBar?.let { days.getOrNull(it)?.date }
    val hourlyDate = if (window == Window.DAY) viewDate else tappedDate
    LaunchedEffect(hourlyDate) { onSelectDay(hourlyDate) }

    val accent = metric.color(viz)
    val waterByHour = remember(hourlyWater) { WaterEntry.byHour(hourlyWater) }

    /** Whether the day itself holds anything, which is what an empty hourly chart has to explain. */
    val dayHasTotals = remember(allDays, viewDate, settings, metric) {
        allDays.firstOrNull { it.date == viewDate }?.let { metric.value(it, settings) > 0.0 } ?: false
    }

    // Stepping back is offered only where there is something to find. The forward arrow
    // needs no such test: it can only ever return toward today.
    val earliest = remember(allDays) { allDays.minOfOrNull { it.date } }
    val spanStart = when (window) {
        Window.DAY -> viewDate
        Window.WEEK -> Dates.weekKeys(anchor).first()
        Window.MONTH -> Dates.monthKeys(anchor).first()
    }
    val canGoBack = earliest != null && earliest < spanStart
    val canGoForward = offset > 0

    val values = remember(window, days, hours, waterByHour, metric, settings, viewDate) {
        if (window == Window.DAY) {
            hourlyValues(viewDate, hours, waterByHour, metric, settings)
        } else {
            days.map { metric.value(it, settings) }
        }
    }
    val labels = remember(window, days) {
        when (window) {
            Window.DAY -> (0..23).map { Dates.hourLabel(it) }
            Window.WEEK -> days.map { Dates.shortWeekdayLabel(it.date) }
            Window.MONTH -> days.map { Dates.dayOfMonthLabel(it.date) }
        }
    }
    val nowIndex = when (window) {
        Window.DAY -> if (viewDate == activeDate) Dates.currentHour() else -1
        else -> days.indexOfFirst { it.date == activeDate }
    }
    val labelEvery = when (window) {
        Window.DAY -> 3
        Window.WEEK -> 1
        Window.MONTH -> 5
    }

    val total = values.sum()
    val filled = values.count { it > 0 }
    val average = if (filled > 0) total / filled else 0.0
    val bestIndex = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0.0 }
    // A daily goal drawn against a single hour would read as a failure every hour of a day
    // that met it, so the goal line belongs to the day and month spans only.
    val barGoal = if (window == Window.DAY) 0.0 else metric.dailyGoal(settings)
    val spanGoal = when (window) {
        Window.DAY -> metric.dailyGoal(settings)
        Window.WEEK -> metric.weeklyGoal(settings)
        Window.MONTH -> metric.weeklyGoal(settings) * days.size / 7.0
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                emoji = "📈",
                title = when (window) {
                    Window.DAY -> if (offset == 0) "Today, hour by hour" else "Hour by hour"
                    Window.WEEK -> if (offset == 0) "This week" else "A week"
                    Window.MONTH -> if (offset == 0) "This month" else "A month"
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Window.entries.forEach { option ->
                    FilterChip(
                        selected = option == window,
                        onClick = {
                            window = option
                            selectedBar = null
                        },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { offset += 1 },
                    enabled = canGoBack,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Earlier",
                    )
                }
                Text(
                    text = when (window) {
                        Window.DAY -> Dates.dayLabel(viewDate)
                        Window.WEEK -> dayRangeLabel(days)
                        Window.MONTH -> Dates.monthLabel(anchor)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = { offset -= 1 },
                    enabled = canGoForward,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Later",
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Metric.entries.forEach { entry ->
                    val selected = entry == metric
                    FilterChip(
                        selected = selected,
                        onClick = {
                            metric = entry
                            selectedBar = null
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Glyph(emoji = entry.emoji, iconRes = entry.iconRes, size = 15.dp)
                                Text("  ${entry.label}")
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = entry.color(viz).copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        leadingIcon = if (selected) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(entry.color(viz), CircleShape)
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (window == Window.DAY) {
                            "${metric.emoji} ${metric.label} per hour"
                        } else {
                            "${metric.emoji} ${metric.label} per day"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (barGoal > 0) {
                            "Dashed line: daily goal ${metric.formatWithUnit(barGoal)}"
                        } else {
                            "No goal line: a daily goal means nothing against a single hour"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    WeeklyBarChart(
                        values = values,
                        labels = labels,
                        todayIndex = nowIndex,
                        goal = barGoal,
                        barColor = accent,
                        formatValue = { metric.formatForChart(it) },
                        selectedIndex = selectedBar,
                        onSelect = { selectedBar = it },
                        labelEvery = labelEvery,
                    )
                    val index = selectedBar
                    Text(
                        text = when {
                            index != null && window == Window.DAY ->
                                "${Dates.hourRangeLabel(index)}: ${metric.formatWithUnit(values[index])}"
                            index != null && days.getOrNull(index) != null ->
                                "${Dates.weekdayLabel(days[index].date)}: ${metric.formatWithUnit(values[index])}"
                            window == Window.DAY -> "Tap a bar to read one hour."
                            else -> "Tap a bar to see that day hour by hour."
                        },
                        style = if (index != null) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        color = if (index != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    // An empty day is drawn as 24 empty slots rather than replaced by a
                    // message: the shape of the axis is what tells a reader the chart is
                    // working. The reason it is flat goes underneath, because a day can be
                    // flat for two reasons and picking the wrong one would mislead.
                    if (window == Window.DAY && values.all { it == 0.0 }) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (dayHasTotals) {
                                "This day has totals but no hourly breakdown. The breakdown is " +
                                    "recorded from the day the app started keeping it, so " +
                                    "earlier days show totals only."
                            } else {
                                "Nothing recorded for this day yet. Bars fill in as the tracker " +
                                    "counts each hour."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (window != Window.DAY && tappedDate != null) {
            item {
                HourlyCard(
                    date = tappedDate,
                    metric = metric,
                    hours = hours,
                    waterByHour = waterByHour,
                    settings = settings,
                    accent = accent,
                    isToday = tappedDate == activeDate,
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (window) {
                            Window.DAY -> "🎯 Daily goal"
                            Window.WEEK -> "🎯 Weekly target"
                            Window.MONTH -> "🎯 Monthly target"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${metric.format(total)} of ${metric.formatWithUnit(spanGoal)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressMeter(
                        progress = if (spanGoal > 0) (total / spanGoal).toFloat() else 0f,
                        color = accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (window == Window.DAY) {
                        SummaryRow("📊 Average per active hour", metric.formatWithUnit(average))
                        SummaryRow(
                            label = "🏅 Busiest hour",
                            value = bestIndex?.let {
                                "${Dates.hourRangeLabel(it)} · ${metric.formatWithUnit(values[it])}"
                            } ?: "No activity yet",
                        )
                        SummaryRow("⏰ Hours with activity", "$filled of 24")
                    } else {
                        SummaryRow("📊 Daily average (active days)", metric.formatWithUnit(average))
                        SummaryRow(
                            label = "🏅 Best day",
                            value = bestIndex?.let {
                                "${Dates.weekdayLabel(days[it].date)} · ${metric.formatWithUnit(values[it])}"
                            } ?: "No activity yet",
                        )
                        SummaryRow(
                            "✅ Days at goal",
                            "${values.count { it >= metric.dailyGoal(settings) }} of ${days.size}",
                        )
                    }
                    if (metric == Metric.CALORIES) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SummaryRow(
                            "🛏️ Resting burn (estimated)",
                            "${Insights.restingKcalPerDay(settings).toInt()} kcal a day",
                        )
                        Text(
                            text = "The chart and the target above count active calories only. " +
                                "Resting burn is what the body spends doing nothing, estimated " +
                                "from your height, weight, age and sex.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            TextButton(onClick = { showTable = !showTable }) {
                Text(if (showTable) "Hide values" else "Show values")
            }
        }

        if (showTable) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        values.forEachIndexed { index, value ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (window == Window.DAY) {
                                        Dates.hourRangeLabel(index)
                                    } else {
                                        Dates.weekdayLabel(days[index].date)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (index == nowIndex) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = metric.formatWithUnit(value),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (index != values.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

/**
 * The chosen metric for each of the 24 hours of [date], gaps filled.
 *
 * Water is read from [waterByHour] rather than the hourly rows: drinks carry their own
 * timestamps, so the split comes from those and is not stored twice.
 */
private fun hourlyValues(
    date: String,
    hours: List<HourlyRecord>,
    waterByHour: IntArray,
    metric: Metric,
    settings: UserSettings,
): List<Double> = (0..23).map { hour ->
    val row = hours.getOrNull(hour) ?: HourlyRecord(date = date, hour = hour)
    metric.value(row, settings, waterByHour[hour])
}

/**
 * One tapped day of the chosen metric, split into 24 hours.
 *
 * Hourly rows only exist from the day the breakdown was added to the app, so a day with
 * no rows says so rather than drawing a flat, empty chart that looks like a day of rest.
 */
@Composable
private fun HourlyCard(
    date: String,
    metric: Metric,
    hours: List<HourlyRecord>,
    waterByHour: IntArray,
    settings: UserSettings,
    accent: Color,
    isToday: Boolean,
) {
    var selectedHour by rememberSaveable(date, metric) { mutableStateOf<Int?>(null) }

    val values = remember(date, hours, waterByHour, metric, settings) {
        hourlyValues(date, hours, waterByHour, metric, settings)
    }
    val busiest = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0.0 }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🕐 ${Dates.dayLabel(date)} hour by hour",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${metric.emoji} ${metric.label} in each hour of the day",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            WeeklyBarChart(
                values = values,
                labels = (0..23).map { Dates.hourLabel(it) },
                todayIndex = if (isToday) Dates.currentHour() else -1,
                goal = 0.0,
                barColor = accent,
                formatValue = { metric.formatForChart(it) },
                selectedIndex = selectedHour,
                onSelect = { selectedHour = it },
                height = 160.dp,
                labelEvery = 3,
            )
            val hour = selectedHour
            Text(
                text = when {
                    hour != null -> "${Dates.hourRangeLabel(hour)}: ${metric.formatWithUnit(values[hour])}"
                    busiest != null ->
                        "🏅 Busiest hour ${Dates.hourRangeLabel(busiest)} · ${metric.formatWithUnit(values[busiest])}"
                    // Drawn as 24 empty slots rather than replaced by a message, so the day
                    // still reads as a day. The line below says why it is flat.
                    else -> "No hourly detail for this day. The breakdown is recorded from the " +
                        "day the app started keeping it, so earlier days show totals only."
                },
                style = if (busiest == null && hour == null) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = if (busiest == null && hour == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun dayRangeLabel(days: List<DailyRecord>): String {
    if (days.isEmpty()) return ""
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val start = Dates.parse(days.first().date).format(formatter)
    val end = Dates.parse(days.last().date).format(formatter)
    return "$start – $end"
}
