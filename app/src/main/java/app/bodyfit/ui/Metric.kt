package app.bodyfit.ui

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.bodyfit.R
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.HourlyRecord
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.sensor.Metrics
import app.bodyfit.ui.components.Emblem
import app.bodyfit.ui.theme.VizColors
import java.util.Locale

/**
 * The six numbers the app tracks, with everything the UI needs to draw one of them.
 *
 * Distance is derived rather than stored, from steps and height. Every other target is a
 * setting the user controls.
 */
enum class Metric(
    /** Used where only text can be shown, such as the notification. */
    val emoji: String,
    val label: String,
    val unit: String,
    /** Drawn instead of [emoji] wherever a composable can render it. */
    @DrawableRes val iconRes: Int? = null,
) {

    STEPS("👣", "Steps", "steps", R.drawable.ic_footsteps),
    CALORIES("🔥", "Calories", "kcal"),
    DISTANCE("📍", "Distance", "km"),
    MOVE_MINUTES("⏱️", "Move minutes", "min"),
    HEART_POINTS("🫀", "Heart points", "pts"),
    WATER("💧", "Water", "ml"),
    ;

    fun value(record: DailyRecord, settings: UserSettings): Double = when (this) {
        STEPS -> record.steps.toDouble()
        CALORIES -> record.activeKcal
        DISTANCE -> Metrics.distanceKm(record.steps, settings.heightCm)
        MOVE_MINUTES -> record.moveMinutes.toDouble()
        HEART_POINTS -> record.heartPoints.toDouble()
        WATER -> record.waterMl.toDouble()
    }

    /**
     * The same measure for a single hour of a day.
     *
     * Water is passed in rather than read from [record]: drinks carry their own
     * timestamps, so the hourly split comes from those rows and is not stored again.
     */
    fun value(record: HourlyRecord, settings: UserSettings, waterMl: Int): Double = when (this) {
        STEPS -> record.steps.toDouble()
        CALORIES -> record.activeKcal
        DISTANCE -> Metrics.distanceKm(record.steps, settings.heightCm)
        MOVE_MINUTES -> record.moveMinutes.toDouble()
        HEART_POINTS -> record.heartPoints.toDouble()
        WATER -> waterMl.toDouble()
    }

    fun dailyGoal(settings: UserSettings): Double = when (this) {
        STEPS -> settings.stepGoal.toDouble()
        CALORIES -> settings.calorieGoal.toDouble()
        DISTANCE -> Metrics.distanceKm(settings.stepGoal, settings.heightCm)
        MOVE_MINUTES -> settings.moveMinuteGoal.toDouble()
        HEART_POINTS -> settings.heartPointGoal.toDouble()
        WATER -> settings.waterGoalMl.toDouble()
    }

    fun weeklyGoal(settings: UserSettings): Double = when (this) {
        STEPS -> settings.weeklyStepGoal.toDouble()
        HEART_POINTS -> settings.weeklyHeartPointGoal.toDouble()
        else -> dailyGoal(settings) * 7
    }

    /**
     * The value on its own, without a unit: "7,432", "2.85", "214".
     *
     * Water is the one metric whose unit moves with the number, so its scale here has to
     * agree with [unitFor]: past a litre both switch together.
     */
    fun format(value: Double): String = when (this) {
        DISTANCE -> String.format(Locale.getDefault(), "%.2f", value)
        WATER -> Volume.amount(value)
        else -> String.format(Locale.getDefault(), "%,d", value.toInt())
    }

    /** The unit to print beside [format]. Fixed for every metric except water. */
    fun unitFor(value: Double): String = if (this == WATER) Volume.unit(value) else unit

    fun formatWithUnit(value: Double): String = "${format(value)} ${unitFor(value)}"

    /**
     * Bar label for the weekly chart. Bars are narrow, so the unit is normally left to the
     * heading above the chart. Water is the exception: its unit changes per bar, so "1.25"
     * beside "800" would be unreadable without it.
     */
    fun formatForChart(value: Double): String = if (this == WATER) formatWithUnit(value) else format(value)

    /**
     * A tintable mark, for the places that paint the icon in the metric's own colour.
     *
     * Steps keeps its drawable: the same asset serves the notification's status-bar icon,
     * where a Compose vector is not an option.
     */
    val vector: ImageVector?
        get() = when (this) {
            STEPS -> null
            CALORIES -> Icons.Filled.LocalFireDepartment
            DISTANCE -> Icons.Filled.Place
            MOVE_MINUTES -> Icons.Filled.Timer
            HEART_POINTS -> Icons.Filled.MonitorHeart
            WATER -> Icons.Filled.WaterDrop
        }

    /**
     * The shape that fills on the Today screen to show progress.
     *
     * Heart points keep the heart, where it says what it measures rather than standing in
     * for every metric at once. Steps get footprints and calories a flame.
     */
    val emblem: Emblem
        get() = when (this) {
            CALORIES -> Emblem.CALORIES
            HEART_POINTS -> Emblem.HEART_POINTS
            else -> Emblem.STEPS
        }

    fun color(viz: VizColors): Color = when (this) {
        STEPS -> viz.steps
        CALORIES -> viz.calories
        DISTANCE -> viz.distance
        MOVE_MINUTES -> viz.moveMinutes
        HEART_POINTS -> viz.heartPoints
        WATER -> viz.water
    }

    companion object {
        /**
         * The three metrics drawn as nested arcs on the Today gauge, outermost first.
         *
         * Capped at three deliberately: these hues are the only set that stays
         * distinguishable for colorblind readers in both light and dark when shown
         * side by side. See [app.bodyfit.ui.theme.VizColors].
         */
        val GAUGE_ARCS = listOf(STEPS, CALORIES, HEART_POINTS)
    }
}
