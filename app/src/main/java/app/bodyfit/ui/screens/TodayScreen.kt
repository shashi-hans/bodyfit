package app.bodyfit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.insights.Insights
import app.bodyfit.ui.Metric
import app.bodyfit.ui.components.AppLogo
import app.bodyfit.ui.components.BreathingCard
import app.bodyfit.ui.components.BreathingDialog
import app.bodyfit.ui.components.GaugeArc
import app.bodyfit.ui.components.Glyph
import app.bodyfit.ui.components.HalfCircleGauge
import app.bodyfit.ui.components.StatCard
import app.bodyfit.ui.theme.LocalViz
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    record: DailyRecord,
    week: List<DailyRecord>,
    allDays: List<DailyRecord>,
    settings: UserSettings,
    activeDate: String,
    onLogWater: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    var breathing by rememberSaveable { mutableStateOf(false) }
    val stepProgress = progressOf(Metric.STEPS, record, settings)
    val weekSteps = week.sumOf { it.steps }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header() }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HalfCircleGauge(
                        arcs = Metric.GAUGE_ARCS.map { metric ->
                            GaugeArc(
                                emoji = metric.emoji,
                                iconRes = metric.iconRes,
                                label = metric.label,
                                value = metric.formatWithUnit(metric.value(record, settings)),
                                goalLabel = metric.formatWithUnit(metric.dailyGoal(settings)),
                                progress = progressOf(metric, record, settings),
                                color = metric.color(viz),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Glyph(
                            emoji = Metric.STEPS.emoji,
                            iconRes = Metric.STEPS.iconRes,
                            size = 22.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Metric.STEPS.format(record.steps.toDouble()),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "of ${Metric.STEPS.format(settings.stepGoal.toDouble())} steps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // The legend already prints each share of goal, so this line adds the
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
                    )
                }
            }
        }

        // The gauge above owns the only three data hues this screen may show at once.
        // Everything below is neutral: emoji and label carry identity, ink carries value.
        // Water and its add buttons share one row, so the action sits beside the number
        // it changes instead of a card's height below it.
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                StatCard(
                    emoji = Metric.WATER.emoji,
                    label = Metric.WATER.label,
                    value = Metric.WATER.format(record.waterMl.toDouble()),
                    unit = Metric.WATER.unitFor(record.waterMl.toDouble()),
                    caption = "Goal ${Volume.format(settings.waterGoalMl)}",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    listOf(settings.defaultCupMl, 500).distinct().forEach { amount ->
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
        }

        // Distance, move minutes, BMI and wellbeing read as one block, so they share a
        // single box. Inside it each figure is bare text on a 2x2 grid: four nested cards
        // would cost a frame and a gap per metric and say nothing extra.
        item {
            val score = remember(allDays, settings, activeDate) {
                Insights.healthScore(allDays, settings, Dates.parse(activeDate))
            }
            val ratingColor = when (score.rating) {
                Insights.Rating.GOOD -> viz.good
                Insights.Rating.WARNING -> viz.warning
                Insights.Rating.CRITICAL -> viz.critical
            }
            val bmiColor = when (Insights.bmiRating(score.bmi)) {
                Insights.Rating.GOOD -> viz.good
                Insights.Rating.WARNING -> viz.warning
                Insights.Rating.CRITICAL -> viz.critical
            }
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Intrinsic height keeps each pair level even when one caption wraps.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.height(IntrinsicSize.Min),
                    ) {
                        PlainStat(
                            emoji = Metric.DISTANCE.emoji,
                            label = Metric.DISTANCE.label,
                            value = Metric.DISTANCE.format(Metric.DISTANCE.value(record, settings)),
                            unit = Metric.DISTANCE.unit,
                            caption = "From ${Metric.STEPS.format(record.steps.toDouble())} steps",
                            modifier = Modifier.weight(1f),
                        )
                        PlainStat(
                            emoji = Metric.MOVE_MINUTES.emoji,
                            label = Metric.MOVE_MINUTES.label,
                            value = Metric.MOVE_MINUTES.format(record.moveMinutes.toDouble()),
                            unit = Metric.MOVE_MINUTES.unit,
                            caption = "Goal ${settings.moveMinuteGoal} min",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.height(IntrinsicSize.Min),
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
                    }
                    Text(
                        text = "Wellbeing is indicative only, not a medical assessment. It weighs BMI, " +
                            "your 14-day step average, age and smoking.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { BreathingCard(onStart = { breathing = true }) }

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (breathing) {
        BreathingDialog(onDismiss = { breathing = false })
    }
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
private fun Header() {
    val now = remember { LocalTime.now() }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning ☀️"
        in 12..16 -> "Good afternoon 🌤️"
        in 17..20 -> "Good evening 🌇"
        else -> "Good night 🌙"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Share of a metric's daily goal, clamped to the 0..1 a ring or meter can draw. */
internal fun progressOf(metric: Metric, record: DailyRecord, settings: UserSettings): Float {
    val goal = metric.dailyGoal(settings)
    if (goal <= 0) return 0f
    return (metric.value(record, settings) / goal).toFloat().coerceIn(0f, 1f)
}
