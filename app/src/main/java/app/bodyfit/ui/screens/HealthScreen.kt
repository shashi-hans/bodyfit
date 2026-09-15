package app.bodyfit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.Dates
import app.bodyfit.data.UserSettings
import app.bodyfit.insights.Insights
import app.bodyfit.ui.components.ProgressMeter
import app.bodyfit.ui.components.SectionHeader
import app.bodyfit.ui.theme.LocalViz
import java.util.Locale

/**
 * BMI and the wellbeing score in full, with the arithmetic that produced them.
 *
 * Today carries the same two numbers as small cards; this is the detail behind them. The
 * rating word is coloured, but the word is always there: colour is a second encoding of
 * "low / moderate / high", never the only one. The working is shown line by line because
 * a single number with no explanation invites more trust than this heuristic deserves.
 */
@Composable
fun HealthScreen(
    allDays: List<DailyRecord>,
    settings: UserSettings,
    activeDate: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    val score = remember(allDays, settings, activeDate) {
        Insights.healthScore(allDays, settings, Dates.parse(activeDate))
    }
    val bmiRating = Insights.bmiRating(score.bmi)

    fun colorFor(rating: Insights.Rating): Color = when (rating) {
        Insights.Rating.GOOD -> viz.good
        Insights.Rating.WARNING -> viz.warning
        Insights.Rating.CRITICAL -> viz.critical
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(emoji = "🩺", title = "Wellbeing") }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${score.score}",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = score.band,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorFor(score.rating),
                    )
                    Spacer(Modifier.height(12.dp))
                    ProgressMeter(
                        progress = score.score / 100f,
                        color = colorFor(score.rating),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "80 and above is low risk, 60 to 79 moderate, below 60 high",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader(emoji = "⚖️", title = "Body mass index") }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f", score.bmi),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "  ${Insights.bmiBand(score.bmi)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorFor(bmiRating),
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        text = "${settings.weightKg} kg at ${settings.heightCm} cm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Healthy is 18.5 to 25. BMI takes no account of muscle, so it reads " +
                            "high for a heavily built person who is not carrying excess fat.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader(emoji = "🧮", title = "How this score was reached") }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    score.factors.forEachIndexed { index, factor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = factor.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = when {
                                    index == 0 -> "${factor.delta}"
                                    factor.delta >= 0 -> "+${factor.delta}"
                                    else -> "${factor.delta}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    index == 0 -> MaterialTheme.colorScheme.onSurface
                                    factor.delta >= 0 -> viz.good
                                    else -> viz.critical
                                },
                            )
                        }
                        if (index != score.factors.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🛏️ Resting burn ${Insights.restingKcalPerDay(settings).toInt()} kcal a day",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "👣 ${score.averageSteps} steps a day, averaged over the last 14 days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The step average divides by all 14 days, so a new install scores " +
                            "low simply for having no history yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "Indicative only. This is not a medical assessment and not an underwriting " +
                    "decision. It is a heuristic with round numbers, useful as a nudge and nothing " +
                    "more. Everything it uses stays on this phone.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}
