package app.bodyfit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.Dates
import app.bodyfit.data.ExerciseSession
import app.bodyfit.data.ExerciseType
import app.bodyfit.data.UserSettings
import app.bodyfit.insights.Insights
import app.bodyfit.notification.NextReminder
import app.bodyfit.ui.Metric
import app.bodyfit.ui.components.AppLogo
import app.bodyfit.ui.components.BreathingDialog
import app.bodyfit.ui.components.GaugeArc
import app.bodyfit.ui.components.GaugeCenter
import app.bodyfit.ui.components.SectionCard
import app.bodyfit.ui.components.TodayGauge
import app.bodyfit.ui.components.Wellness
import app.bodyfit.ui.components.WellnessNote
import app.bodyfit.ui.theme.LocalViz
import app.bodyfit.ui.theme.color
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun TodayScreen(
    record: DailyRecord,
    week: List<DailyRecord>,
    allDays: List<DailyRecord>,
    /** Today's logged exercise, so the figures can say which part came from it. */
    sessions: List<ExerciseSession>,
    settings: UserSettings,
    activeDate: String,
    onLogWater: (Int) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenHealth: () -> Unit,
    /** Opens Trends on the given metric for the active day. */
    onOpenTrends: (Metric) -> Unit,
    /** When the next water reminder rings, shown under the BMI card. */
    nextReminder: NextReminder,
    onOpenWaterReminders: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    var breathing by rememberSaveable { mutableStateOf(false) }
    var calorieInfo by rememberSaveable { mutableStateOf(false) }
    var sourceOf by rememberSaveable { mutableStateOf<Metric?>(null) }
    val stepProgress = progressOf(Metric.STEPS, record, settings)
    val weekSteps = week.sumOf { it.steps }
    // Ticks once a minute, so figures that follow the clock rather than the data, the
    // resting share of the calories and the greeting, move while the phone sits still.
    val minute by produceState(System.currentTimeMillis() / 60_000L) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis() / 60_000L
        }
    }
    // Active plus the resting burn the day has accrued, which is the figure another tracker
    // shows as "calories".
    val totalKcal = remember(record, settings, activeDate, minute) {
        record.activeKcal + Insights.restingKcalSoFar(settings, Dates.elapsedFraction(activeDate))
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(name = settings.name, minute = minute, onOpenMenu = onOpenMenu) }

        item {
            SectionCard(
                corner = 28.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TodayGauge(
                        arcs = Metric.GAUGE_FIGURES.map { metric ->
                            // Calories is the one figure not measured against its goal: the
                            // goal is an activity target, and the figure here is the whole
                            // day's burn. The activity number it is scored on is in the well.
                            val isCalories = metric == Metric.CALORIES
                            GaugeArc(
                                emoji = metric.emoji,
                                iconRes = metric.iconRes,
                                vector = metric.vector,
                                label = metric.label,
                                // Bare: the caption under it carries the unit.
                                value = if (isCalories) {
                                    metric.format(totalKcal)
                                } else {
                                    metric.format(metric.value(record, settings))
                                },
                                caption = if (isCalories) {
                                    // Short enough to survive a third of a phone's width.
                                    // Read against "from activity" in the well, which is
                                    // what says where the rest of this number comes from.
                                    "kcal total"
                                } else {
                                    "of ${metric.formatWithUnit(metric.dailyGoal(settings))}"
                                },
                                progress = progressOf(metric, record, settings),
                                hasArc = metric in Metric.GAUGE_ARCS,
                                color = metric.color(viz),
                                textColor = metric.textColor(viz),
                                // The only figure here that is not what its label says at
                                // face value: "calories" usually means activity, and this
                                // one counts resting burn too.
                                onInfo = when {
                                    isCalories -> ({ calorieInfo = true })
                                    // Heart points are the other figure worth opening: they
                                    // are earned two ways, and which one is not obvious.
                                    metric == Metric.HEART_POINTS ->
                                        ({ sourceOf = Metric.HEART_POINTS })
                                    else -> null
                                },
                                onOpen = { onOpenTrends(metric) },
                            )
                        },
                        center = GaugeCenter(
                            value = Metric.CALORIES.format(record.activeKcal),
                            caption = "kcal from activity",
                            color = Metric.CALORIES.textColor(viz),
                            onInfo = { sourceOf = Metric.CALORIES },
                            onOpen = { onOpenTrends(Metric.CALORIES) },
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                    // The hearts show the share of each goal, so this line adds the
                    // celebration and the week, not a second copy of the percentage.
                    if (stepProgress >= 1f) {
                        Text(
                            text = "🎉 Step goal done. Keep walking.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = "📅 This week ${Metric.STEPS.format(weekSteps.toDouble())} steps · " +
                            "${week.sumOf { it.heartPoints }} of ${settings.weeklyHeartPointGoal} heart points",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Every measured number is an arc above, so nothing below repeats one. What is left
        // here is the action: the amounts that add a drink, which have no arc because they
        // change the day rather than report it.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                settings.cupSizesMl.forEach { amount ->
                    AssistChip(
                        onClick = { onLogWater(amount) },
                        label = { Text("💧 +$amount ml") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
        }

        item {
            val score = remember(allDays, settings, activeDate) {
                Insights.healthScore(allDays, settings, Dates.parse(activeDate))
            }
            val ratingColor = score.rating.color(viz)
            val bmiColor = Insights.bmiRating(score.bmi).color(viz)
            SectionCard(
                corner = 28.dp,
                modifier = Modifier.clickable(onClick = onOpenHealth),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainStat(
                        emoji = "⚖️",
                        label = "BMI",
                        value = String.format(Locale.getDefault(), "%.1f", score.bmi),
                        caption = Insights.bmiBand(score.bmi),
                        captionColor = bmiColor,
                        modifier = Modifier.weight(1f),
                    )
                    PlainStat(
                        emoji = "🩺",
                        label = "Wellbeing",
                        value = "${score.score}",
                        unit = "/ 100",
                        caption = score.band,
                        captionColor = ratingColor,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "How these are worked out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { NextReminderCard(nextReminder, minute, onOpenWaterReminders) }

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (breathing) {
        BreathingDialog(onDismiss = { breathing = false })
    }

    sourceOf?.let { metric ->
        SourceDialog(
            metric = metric,
            record = record,
            sessions = sessions,
            onDismiss = { sourceOf = null },
        )
    }

    if (calorieInfo) {
        CalorieInfoDialog(
            activeKcal = record.activeKcal,
            totalKcal = totalKcal,
            settings = settings,
            onDismiss = { calorieInfo = false },
        )
    }
}

/**
 * Where a day's activity calories or heart points came from, line by line.
 *
 * Everything the day holds is either a logged session or walking the tracker scored, so the
 * walking share is the day's total less the sessions rather than a figure of its own. That
 * also means the lines always add up to the number on the card, which a separately counted
 * walking total could not promise.
 */
@Composable
private fun SourceDialog(
    metric: Metric,
    record: DailyRecord,
    sessions: List<ExerciseSession>,
    onDismiss: () -> Unit,
) {
    val unit = if (metric == Metric.CALORIES) "kcal" else "pts"
    val total = if (metric == Metric.CALORIES) record.activeKcal else record.heartPoints.toDouble()
    val fromSessions = sessions.sumOf {
        if (metric == Metric.CALORIES) it.kcal else it.heartPoints.toDouble()
    }
    val fromWalking = (total - fromSessions).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${metric.emoji}  Where today's ${metric.label.lowercase()} came from") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceRow(
                    emoji = "👣",
                    label = "Walking",
                    value = "${metric.format(fromWalking)} $unit",
                )
                sessions.forEach { session ->
                    val type = ExerciseType.from(session.type)
                    val earned = if (metric == Metric.CALORIES) {
                        session.kcal
                    } else {
                        session.heartPoints.toDouble()
                    }
                    SourceRow(
                        emoji = type?.emoji ?: "🏃",
                        label = "${type?.label ?: session.type}, ${clock(session.seconds)}",
                        value = "${metric.format(earned)} $unit",
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SourceRow(
                    emoji = metric.emoji,
                    label = "Total today",
                    value = "${metric.format(total)} $unit",
                    strong = true,
                )
                if (sessions.isEmpty()) {
                    Text(
                        text = "No exercise logged today, so all of it is walking. A timed " +
                            "session on the Exercise tab appears here as its own line.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** One source and what it contributed. */
@Composable
private fun SourceRow(emoji: String, label: String, value: String, strong: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (strong) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * What the total calorie figure counts, and why it is not the one the goal is scored on.
 *
 * Written because the number is the app's most misreadable: a tracker that says "calories"
 * usually means activity alone, and a user who sees a thousand of them before lunch will
 * otherwise conclude the step counter is broken.
 */
@Composable
private fun CalorieInfoDialog(
    activeKcal: Double,
    totalKcal: Double,
    settings: UserSettings,
    onDismiss: () -> Unit,
) {
    val restingSoFar = totalKcal - activeKcal
    val restingPerDay = Insights.restingKcalPerDay(settings)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔥  Calories in total") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${Metric.CALORIES.format(totalKcal)} kcal is everything your " +
                        "body has spent today: ${Metric.CALORIES.format(activeKcal)} from " +
                        "moving, and ${Metric.CALORIES.format(restingSoFar)} at rest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Resting burn is what the body spends breathing, pumping blood " +
                        "and staying warm. It runs all day whether you move or not. Yours " +
                        "comes to about ${Metric.CALORIES.format(restingPerDay)} kcal a day, from your " +
                        "height, weight, age and sex. The figure above counts the share of " +
                        "that the day has reached so far.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Your ${settings.calorieGoal} kcal goal is an activity target, so " +
                        "it is scored against the ${Metric.CALORIES.format(activeKcal)} in " +
                        "the arc, not against this number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WellnessNote(
                    text = "Both figures are estimates. Nothing on the phone measures your " +
                        "metabolism; the resting figure is the Mifflin-St Jeor formula, an " +
                        "average for your build rather than a reading of you. " +
                        Wellness.SHORT,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * One measurement with no frame of its own, for use inside a section box.
 *
 * Emoji and label carry identity, ink carries the value. Spacing does the grouping that
 * a card border would otherwise do, which keeps four of these to about half the height.
 */
@Composable
private fun PlainStat(
    emoji: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    /** Blank for a unitless figure such as BMI, which then renders without a trailing gap. */
    unit: String = "",
    caption: String? = null,
    /** Used for a status word such as "Low risk". The word stays, so colour is never alone. */
    captionColor: Color? = null,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, style = MaterialTheme.typography.labelLarge)
            Text(
                text = "  $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = captionColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * App name on the left, greeting and date on the right.
 *
 * The name carries a soft colored glow rather than a hard drop shadow: the offset is
 * small and the blur wide, so it reads as depth in both themes instead of as a second,
 * misaligned copy of the text.
 */
@Composable
private fun Header(name: String, minute: Long, onOpenMenu: () -> Unit) {
    val now = remember(minute) { LocalTime.now() }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning ☀️"
        in 12..16 -> "Good afternoon 🌤️"
        in 17..20 -> "Good evening 🌇"
        else -> "Good night 🌙"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenMenu) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // The name is the one optional answer in setup, so a blank one is a choice
                // rather than a gap. "Guest" fills the same slot a name would, which keeps
                // the header the same shape either way.
                text = "Hi, ${name.ifBlank { "Guest" }}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        AppLogo(compact = true)
    }
}

/** Share of a metric's daily goal, clamped to the 0..1 a ring or meter can draw. */
internal fun progressOf(metric: Metric, record: DailyRecord, settings: UserSettings): Float {
    val goal = metric.dailyGoal(settings)
    if (goal <= 0) return 0f
    return (metric.value(record, settings) / goal).toFloat().coerceIn(0f, 1f)
}

/**
 * One line on when the next water reminder rings, opening the reminder page.
 *
 * [minute] is the screen's minute tick, so "today" and "tomorrow" stay right across midnight.
 */
@Composable
private fun NextReminderCard(next: NextReminder, minute: Long, onOpen: () -> Unit) {
    val (title, detail) = remember(next, minute) { nextReminderText(next) }
    SectionCard(corner = 20.dp, modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "⏰", style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Water reminder settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The card's two lines: what it is, and when. */
private fun nextReminderText(next: NextReminder): Pair<String, String> = when (next) {
    NextReminder.Off -> "Water reminders" to "Off · tap to turn on"
    NextReminder.Unknown -> "Next water reminder" to "Not scheduled yet"
    is NextReminder.At -> {
        val at = java.time.Instant.ofEpochMilli(next.atMillis).atZone(java.time.ZoneId.systemDefault())
        val time = at.toLocalTime().format(
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT),
        )
        val day = when (at.toLocalDate()) {
            LocalDate.now() -> time
            LocalDate.now().plusDays(1) -> "Tomorrow, $time"
            else -> Dates.dayLabel(at.toLocalDate().toString()) + ", " + time
        }
        if (next.goalReachedToday) "Goal reached · next water reminder" to day
        else "Next water reminder" to day
    }
}
