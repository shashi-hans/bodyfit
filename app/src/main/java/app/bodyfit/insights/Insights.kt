package app.bodyfit.insights

import app.bodyfit.data.DailyRecord
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettings
import app.bodyfit.sensor.Metrics
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Numbers derived from days already stored. Nothing here is persisted.
 *
 * Every function takes the day rows and the settings and returns a result, so the whole
 * file is pure and testable without a database, a context or a clock. `today` is a
 * parameter for the same reason: a figure that only works on the machine's current date is
 * one nobody can test.
 */
object Insights {

    // ---- body and health -----------------------------------------------------------

    fun bmi(settings: UserSettings): Double {
        val metres = settings.heightCm / 100.0
        return settings.weightKg / (metres * metres)
    }

    fun bmiBand(bmi: Double): String = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Healthy"
        bmi < 30.0 -> "Overweight"
        else -> "Obese"
    }

    fun averageSteps(days: List<DailyRecord>, window: Int, today: LocalDate = LocalDate.now()): Int {
        if (window <= 0) return 0
        val keys = (0 until window).map { today.minusDays(it.toLong()).toString() }.toSet()
        val total = days.filter { it.date in keys }.sumOf { it.steps }
        return total / window
    }

    /**
     * Days inside the window that the tracker actually recorded something for.
     *
     * [averageSteps] divides by the whole window, which is what a daily average means. The
     * score needs a different question first: whether there is enough history to average
     * at all. Today is excluded because it is still in progress, and judging a person on a
     * morning would read every install as sedentary.
     */
    fun trackedDays(days: List<DailyRecord>, window: Int, today: LocalDate = LocalDate.now()): Int {
        if (window <= 0) return 0
        val keys = (1 until window).map { today.minusDays(it.toLong()).toString() }.toSet()
        return days.count { it.date in keys && it.steps > 0 }
    }

    /** Full days of history the step term needs before it is allowed to move the score. */
    const val MIN_DAYS_FOR_STEP_TERM = 3

    /** One line of the score's working, so a screen can show why the number is what it is. */
    data class ScoreFactor(val label: String, val delta: Int)

    enum class Rating { GOOD, WARNING, CRITICAL }

    data class HealthScore(
        val score: Int,
        val band: String,
        val rating: Rating,
        val bmi: Double,
        val averageSteps: Int,
        val factors: List<ScoreFactor>,
    )

    fun bmiRating(bmi: Double): Rating = when {
        bmi >= 30.0 -> Rating.CRITICAL
        bmi >= 25.0 || bmi < 18.5 -> Rating.WARNING
        else -> Rating.GOOD
    }

    /**
     * An indicative wellbeing score, ported from the Body-fit web build.
     *
     * It is not a medical assessment and not an underwriting decision, and any screen
     * showing it has to say so. Sleep is part of the original formula and is skipped here
     * because this app does not record it; the score is correspondingly less informed.
     */
    fun healthScore(
        days: List<DailyRecord>,
        settings: UserSettings,
        today: LocalDate = LocalDate.now(),
    ): HealthScore {
        val bmi = bmi(settings)
        val steps = averageSteps(days, 14, today)
        val tracked = trackedDays(days, 14, today)
        val factors = buildList {
            add(ScoreFactor("Starting score", 100))
            when {
                bmi >= 30.0 -> add(ScoreFactor("BMI 30 or above", -20))
                bmi >= 25.0 -> add(ScoreFactor("BMI 25 to 30", -10))
                bmi < 18.5 -> add(ScoreFactor("BMI under 18.5", -8))
            }
            // Absence of data is not absence of activity. Before there is enough history
            // to average, the step term is named and skipped rather than scored, so a new
            // install is not told it is sedentary on the strength of days nobody tracked.
            if (tracked < MIN_DAYS_FOR_STEP_TERM) {
                add(ScoreFactor("Not enough days tracked to judge steps", 0))
            } else {
                when {
                    steps < 4_000 -> add(ScoreFactor("Under 4,000 steps a day", -22))
                    steps < 7_000 -> add(ScoreFactor("Under 7,000 steps a day", -12))
                    steps >= 10_000 -> add(ScoreFactor("10,000 steps a day or more", 4))
                }
            }
            if (settings.smoker) add(ScoreFactor("Smoker", -18))
            if (settings.age > 45) add(ScoreFactor("Over 45", -6))
        }
        val score = factors.sumOf { it.delta }.coerceIn(5, 100)
        val rating = when {
            score >= 80 -> Rating.GOOD
            score >= 60 -> Rating.WARNING
            else -> Rating.CRITICAL
        }
        val band = when (rating) {
            Rating.GOOD -> "Low risk"
            Rating.WARNING -> "Moderate risk"
            Rating.CRITICAL -> "High risk"
        }
        return HealthScore(score, band, rating, bmi, steps, factors)
    }

    // ---- recommended goals ------------------------------------------------------------

    data class Recommended(
        val stepGoal: Int,
        val calorieGoal: Int,
        val waterGoalMl: Int,
        val heartPointGoal: Int,
        val moveMinuteGoal: Int,
    )

    /**
     * Daily goals suggested from the user's body and age, as general guidance.
     *
     * Sources, so these are arguable rather than invented:
     *
     * - Steps fall with age. Benefit accrues well below the familiar 10,000, and the
     *   evidence for older adults plateaus nearer 7,000, so the bands are 10,000 under 40,
     *   8,500 to 59, and 7,000 from 60.
     * - Heart points and move minutes both come from the WHO's 150 moderate minutes a
     *   week. One heart point is a moderate minute here, so 150 a week is about 21 a day,
     *   and the same 150 minutes is about 30 minutes a day of movement.
     * - Water is 35 ml per kg of body weight, with a floor from the EFSA adequate intake
     *   for drinking water: 2.0 L for men, 1.6 L for women.
     * - Calories are the recommended steps walked at a moderate pace, costed through the
     *   same MET model the tracker uses, so the figure agrees with what gets recorded.
     *
     * Height reaches the calorie goal only, through the stride that turns a cadence into a
     * walking speed. The step, heart point, move minute and water goals do not depend on
     * it; making them do so would give the numbers false authority.
     *
     * This is general guidance, not medical advice, and not an underwriting input.
     */
    fun recommended(settings: UserSettings): Recommended {
        val steps = when {
            settings.age < 40 -> 10_000
            settings.age < 60 -> 8_500
            else -> 7_000
        }
        val minutesAtModeratePace = steps.toDouble() / Metrics.MODERATE_CADENCE
        val calories = Metrics.kcalForMinute(
            cadence = Metrics.MODERATE_CADENCE,
            weightKg = settings.weightKg,
            heightCm = settings.heightCm,
        ) * minutesAtModeratePace
        val waterFloor = when (settings.sex) {
            Sex.MALE -> 2_000
            Sex.FEMALE -> 1_600
            Sex.UNSPECIFIED -> 1_800
        }
        val water = maxOf((settings.weightKg * 35.0).roundToInt(), waterFloor)

        return Recommended(
            stepGoal = steps,
            calorieGoal = (calories / 25).roundToInt() * 25,
            waterGoalMl = (water / 100.0).roundToInt() * 100,
            heartPointGoal = 21,
            moveMinuteGoal = 30,
        )
    }

    // ---- resting burn ---------------------------------------------------------------

    /**
     * Resting energy for a whole day, by Mifflin-St Jeor.
     *
     * This is what the body spends doing nothing, and it dwarfs the active figure: a
     * typical adult rests through roughly 1,500 kcal a day and walks off a few hundred.
     * Showing it beside active calories stops the active number being read as "all I
     * burned today".
     *
     * The sex term is +5 for men and -161 for women. When sex is unspecified the midpoint
     * is used, which is wrong by about 83 kcal either way; the screen calls it an estimate
     * for that reason.
     */
    fun restingKcalPerDay(settings: UserSettings): Double {
        val base = 10.0 * settings.weightKg + 6.25 * settings.heightCm - 5.0 * settings.age
        val sexTerm = when (settings.sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
            Sex.UNSPECIFIED -> -78.0
        }
        return (base + sexTerm).coerceAtLeast(0.0)
    }
}
