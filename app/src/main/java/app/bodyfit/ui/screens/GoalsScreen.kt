package app.bodyfit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.insights.Insights
import app.bodyfit.ui.components.GoalSlider
import app.bodyfit.ui.components.RecommendationRow
import app.bodyfit.ui.components.SectionHeader
import app.bodyfit.ui.components.SettingsCard
import app.bodyfit.ui.components.thousands

/**
 * Goals only: what you are aiming at daily and weekly.
 *
 * Everything that is not a goal now lives behind the menu on the Today screen, so this tab
 * stays short enough to change a target without scrolling past five other subjects. The
 * suggested goals are a dialog rather than a permanent card for the same reason: they are
 * read once in a while, not every visit.
 */
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
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showRecommendations by rememberSaveable { mutableStateOf(false) }

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
                OutlinedButton(onClick = { showRecommendations = true }) {
                    Text("💡  Recommendation")
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

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (showRecommendations) {
        RecommendationDialog(
            settings = settings,
            onDismiss = { showRecommendations = false },
            onApply = { r ->
                onStepGoal(r.stepGoal)
                onCalorieGoal(r.calorieGoal)
                onWaterGoal(r.waterGoalMl)
                onHeartPointGoal(r.heartPointGoal)
                onMoveMinuteGoal(r.moveMinuteGoal)
                showRecommendations = false
            },
        )
    }
}

/**
 * The suggested goals, with a tick beside any the user is already using.
 *
 * Applying writes all five at once, because the set is derived together and mixing three
 * suggested numbers with two old ones gives a target nobody chose.
 */
@Composable
private fun RecommendationDialog(
    settings: UserSettings,
    onDismiss: () -> Unit,
    onApply: (Insights.Recommended) -> Unit,
) {
    val r = remember(settings) { Insights.recommended(settings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💡  Recommended for you") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecommendationRow("👣", "Steps", thousands(r.stepGoal), settings.stepGoal == r.stepGoal)
                RecommendationRow(
                    "🔥",
                    "Calories",
                    "${thousands(r.calorieGoal)} kcal",
                    settings.calorieGoal == r.calorieGoal,
                )
                RecommendationRow(
                    "💧",
                    "Water",
                    Volume.format(r.waterGoalMl),
                    settings.waterGoalMl == r.waterGoalMl,
                )
                RecommendationRow(
                    "🫀",
                    "Heart points",
                    "${r.heartPointGoal} pts",
                    settings.heartPointGoal == r.heartPointGoal,
                )
                RecommendationRow(
                    "⏱️",
                    "Move minutes",
                    "${r.moveMinuteGoal} min",
                    settings.moveMinuteGoal == r.moveMinuteGoal,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "From your age, weight and sex. Steps drop with age; heart points and " +
                        "move minutes come from the WHO's 150 moderate minutes a week; water is " +
                        "35 ml per kg. General guidance, not medical advice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(r) }) { Text("Use these goals") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
