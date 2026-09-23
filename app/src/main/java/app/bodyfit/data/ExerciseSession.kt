package app.bodyfit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.bodyfit.sensor.Metrics

/**
 * What the app can time and log, and what a minute of each costs.
 *
 * MET values are the compendium's, and are deliberately single figures per activity: the
 * app has no way to know how hard a ride was, so a moderate effort is assumed and the
 * screen says so rather than implying a precision that is not there.
 */
enum class ExerciseType(
    val label: String,
    val emoji: String,
    val met: Double,
    val description: String,
    /** True when the activity also produces steps, which the tracker is already counting. */
    val producesSteps: Boolean,
) {
    RUNNING("Running", "🏃", 8.0, "Assumes a steady run, about 8 km/h.", producesSteps = true),
    CYCLING("Cycling", "🚴", 7.0, "Assumes moderate effort, about 20 km/h.", producesSteps = false),
    SKIPPING("Skipping", "🤸", 11.0, "Assumes a steady rope pace.", producesSteps = false),
    ;

    /**
     * Activity calories for [minutes] at [weightKg], on the same `(MET - 1)` basis as the
     * step tracker, so a logged session and a walked minute mean the same thing.
     */
    fun kcal(minutes: Double, weightKg: Int): Double =
        (met - 1.0).coerceAtLeast(0.0) * 3.5 * weightKg / 200.0 * minutes

    /** Heart points for [minutes], on the same bands the tracker scores cadence against. */
    fun heartPoints(minutes: Double): Int = when {
        met >= 6.0 -> (minutes * 2).toInt()
        met >= 3.0 -> minutes.toInt()
        else -> 0
    }

    companion object {
        fun from(name: String): ExerciseType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One timed exercise, kept so the day's totals can be traced back to what produced them.
 *
 * Sessions are stored as well as folded into [DailyRecord] because a calorie figure with
 * no explanation is not checkable: a user who sees 300 kcal appear should be able to find
 * the 40-minute ride that caused it.
 */
@Entity(
    tableName = "exercise_session",
    indices = [Index("date")],
)
data class ExerciseSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    /** Stored as the enum name, so an unknown value from a future build is skipped, not fatal. */
    val type: String,
    val startedAt: Long,
    val seconds: Int,
    val kcal: Double,
    val heartPoints: Int,
)
