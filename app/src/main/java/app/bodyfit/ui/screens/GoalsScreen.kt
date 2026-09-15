package app.bodyfit.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bodyfit.data.Sex
import app.bodyfit.data.Volume
import app.bodyfit.data.UserSettings
import app.bodyfit.insights.Insights
import app.bodyfit.ui.components.SectionHeader
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    settings: UserSettings,
    onStepGoal: (Int) -> Unit,
    onWaterGoal: (Int) -> Unit,
    onCalorieGoal: (Int) -> Unit,
    onHeartPointGoal: (Int) -> Unit,
    onMoveMinuteGoal: (Int) -> Unit,
    onWeeklyStepGoal: (Int) -> Unit,
    onWeeklyHeartPointGoal: (Int) -> Unit,
    onHeight: (Int) -> Unit,
    onWeight: (Int) -> Unit,
    onDefaultCup: (Int) -> Unit,
    onTrackerEnabled: (Boolean) -> Unit,
    onAge: (Int) -> Unit,
    onSmoker: (Boolean) -> Unit,
    onSex: (Sex) -> Unit,
    onExport: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(emoji = "🎯", title = "Daily goals") }

        item {
            SettingsCard {
                GoalSlider(
                    emoji = "👣",
                    label = "Steps",
                    value = settings.stepGoal,
                    range = UserSettings.STEP_GOAL_RANGE,
                    step = 500,
                    format = { "${thousands(it)} steps" },
                    onCommit = onStepGoal,
                )
                GoalSlider(
                    emoji = "🔥",
                    label = "Calories",
                    value = settings.calorieGoal,
                    range = UserSettings.CALORIE_GOAL_RANGE,
                    step = 25,
                    format = { "${thousands(it)} kcal" },
                    onCommit = onCalorieGoal,
                )
                GoalSlider(
                    emoji = "💧",
                    label = "Water",
                    value = settings.waterGoalMl,
                    range = UserSettings.WATER_GOAL_RANGE,
                    step = 100,
                    format = { Volume.format(it) },
                    onCommit = onWaterGoal,
                )
                GoalSlider(
                    emoji = "🫀",
                    label = "Heart points",
                    value = settings.heartPointGoal,
                    range = UserSettings.HEART_POINT_GOAL_RANGE,
                    step = 1,
                    format = { "$it pts" },
                    onCommit = onHeartPointGoal,
                )
                GoalSlider(
                    emoji = "⏱️",
                    label = "Move minutes",
                    value = settings.moveMinuteGoal,
                    range = UserSettings.MOVE_MINUTE_GOAL_RANGE,
                    step = 5,
                    format = { "$it min" },
                    onCommit = onMoveMinuteGoal,
                )
            }
        }

        item { SectionHeader(emoji = "💡", title = "Recommended Goals for you") }

        item {
            val r = remember(settings) { Insights.recommended(settings) }
            SettingsCard {
                RecommendationRow("👣", "Steps", thousands(r.stepGoal), settings.stepGoal == r.stepGoal)
                RecommendationRow("🔥", "Calories", "${thousands(r.calorieGoal)} kcal", settings.calorieGoal == r.calorieGoal)
                RecommendationRow("💧", "Water", Volume.format(r.waterGoalMl), settings.waterGoalMl == r.waterGoalMl)
                RecommendationRow("🫀", "Heart points", "${r.heartPointGoal} pts", settings.heartPointGoal == r.heartPointGoal)
                RecommendationRow("⏱️", "Move minutes", "${r.moveMinuteGoal} min", settings.moveMinuteGoal == r.moveMinuteGoal)
                Text(
                    text = "From your age, weight and sex. Steps drop with age; heart points and " +
                        "move minutes come from the WHO's 150 moderate minutes a week; water is " +
                        "35 ml per kg. Height is not used, because none of these depend on it. " +
                        "General guidance, not medical advice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        onStepGoal(r.stepGoal)
                        onCalorieGoal(r.calorieGoal)
                        onWaterGoal(r.waterGoalMl)
                        onHeartPointGoal(r.heartPointGoal)
                        onMoveMinuteGoal(r.moveMinuteGoal)
                    },
                ) {
                    Text("Use these goals")
                }
            }
        }

        item { SectionHeader(emoji = "📅", title = "Weekly targets") }

        item {
            SettingsCard {
                GoalSlider(
                    emoji = "👣",
                    label = "Steps this week",
                    value = settings.weeklyStepGoal,
                    range = 10_000..200_000,
                    step = 5_000,
                    format = { "${thousands(it)} steps" },
                    onCommit = onWeeklyStepGoal,
                )
                GoalSlider(
                    emoji = "🫀",
                    label = "Heart points this week",
                    value = settings.weeklyHeartPointGoal,
                    range = 20..500,
                    step = 5,
                    format = { "$it pts" },
                    onCommit = onWeeklyHeartPointGoal,
                )
                Text(
                    text = "150 heart points a week is the WHO activity guideline for adults.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader(emoji = "🧍", title = "About you") }

        item {
            SettingsCard {
                GoalSlider(
                    emoji = "📏",
                    label = "Height",
                    value = settings.heightCm,
                    range = UserSettings.HEIGHT_RANGE,
                    step = 1,
                    format = { "$it cm" },
                    onCommit = onHeight,
                )
                GoalSlider(
                    emoji = "⚖️",
                    label = "Weight",
                    value = settings.weightKg,
                    range = UserSettings.WEIGHT_RANGE,
                    step = 1,
                    format = { "$it kg" },
                    onCommit = onWeight,
                )
                GoalSlider(
                    emoji = "🎂",
                    label = "Age",
                    value = settings.age,
                    range = UserSettings.AGE_RANGE,
                    step = 1,
                    format = { "$it years" },
                    onCommit = onAge,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Sex.entries.forEach { option ->
                        FilterChip(
                            selected = option == settings.sex,
                            onClick = { onSex(option) },
                            label = {
                                Text(
                                    when (option) {
                                        Sex.MALE -> "Male"
                                        Sex.FEMALE -> "Female"
                                        Sex.UNSPECIFIED -> "Prefer not to say"
                                    }
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🚬  Smoker",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(checked = settings.smoker, onCheckedChange = onSmoker)
                }
                Text(
                    text = "Distance uses your height for stride length, and calories use your " +
                        "weight. Age and sex are used only for the resting-burn estimate. " +
                        "All of it stays on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader(emoji = "💾", title = "Backup") }

        item {
            SettingsCard {
                Text(
                    text = "There is no sync, so a lost phone is a lost history. Export writes " +
                        "every day, drink and setting to a JSON file you choose the location of.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onExport) { Text("Export backup") }
            }
        }

        item { SectionHeader(emoji = "🥤", title = "Default cup size") }

        item {
            SettingsCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    UserSettings.CUP_SIZES_ML.forEach { size ->
                        FilterChip(
                            selected = size == settings.defaultCupMl,
                            onClick = { onDefaultCup(size) },
                            label = { Text("$size") },
                        )
                    }
                }
                Text(
                    text = "This size becomes the first water button on the lock-screen card.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader(emoji = "🔒", title = "Lock screen card") }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Count steps and show the card",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Android needs a visible card to let an app read the step sensor in " +
                                "the background, so this switch controls both.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.trackerEnabled, onCheckedChange = onTrackerEnabled)
                }
            }
        }

        item { SectionHeader(emoji = "ℹ️", title = "How the numbers work") }

        item {
            SettingsCard {
                InfoLine("👣", "Steps come from the phone's own step sensor. No Google account needed.")
                InfoLine("📍", "Distance is steps times your stride, estimated as 41.5% of your height.")
                InfoLine(
                    "🫀",
                    "Heart points score effort by pace: 1 point a minute above 100 steps a minute, " +
                        "2 above 130.",
                )
                InfoLine(
                    "⏱️",
                    "A move minute is any 60 seconds at 10 or more steps a minute, timed from " +
                        "when you start walking. Pace changes what the minute is worth, not " +
                        "whether it counts.",
                )
                InfoLine(
                    "🔥",
                    "Calories are the cost of active minutes only, from your pace, weight and " +
                        "height. Resting burn is not included. Walking faster always earns " +
                        "more, but a few steps across a room earn nothing.",
                )
                InfoLine("🔐", "Everything is stored on this phone. No account, no server, no analytics.")
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun GoalSlider(
    emoji: String,
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    // The slider drives a local value while the finger is down and commits on release,
    // so a drag does not write to disk on every frame.
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$emoji  $label",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = format(snap(draft, range, step)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(snap(draft, range, step)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
        )
    }
}

/** One suggested value, with a tick when the current goal already matches it. */
@Composable
private fun RecommendationRow(emoji: String, label: String, value: String, matches: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$emoji  $label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (matches) "$value ✓" else value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InfoLine(emoji: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "  $text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun snap(raw: Float, range: IntRange, step: Int): Int {
    val snapped = (raw / step).roundToInt() * step
    return snapped.coerceIn(range.first, range.last)
}

private fun thousands(value: Int): String = String.format(Locale.getDefault(), "%,d", value)
