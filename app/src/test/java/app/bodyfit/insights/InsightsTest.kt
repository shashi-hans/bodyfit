package app.bodyfit.insights

import app.bodyfit.data.DailyRecord
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {

    private val today = LocalDate.of(2026, 9, 8)
    private val settings = UserSettings(stepGoal = 10_000, waterGoalMl = 2_500, heightCm = 170, weightKg = 70)

    /** Days counting back from [today]; index 0 is today. */
    private fun days(vararg steps: Int, water: Int = 0, move: Int = 0): List<DailyRecord> =
        steps.mapIndexed { back, value ->
            DailyRecord(
                date = today.minusDays(back.toLong()).toString(),
                steps = value,
                moveMinutes = move,
                waterMl = water,
            )
        }

    // ---- body and health -----------------------------------------------------------

    @Test
    fun `bmi and its band`() {
        assertEquals(24.22, Insights.bmi(settings), 0.01)
        assertEquals("Healthy", Insights.bmiBand(Insights.bmi(settings)))
        assertEquals("Obese", Insights.bmiBand(31.0))
        assertEquals("Underweight", Insights.bmiBand(17.0))
    }

    @Test
    fun `average steps divides by the window, not by the days present`() {
        // Seven recorded days averaged over fourteen: half the window is genuinely empty,
        // and treating it as absent would flatter the number.
        val avg = Insights.averageSteps(days(14_000, 14_000, 14_000, 14_000, 14_000, 14_000, 14_000), 14, today)
        assertEquals(7_000, avg)
    }

    @Test
    fun `an active non-smoker scores in the low risk band`() {
        val active = days(*IntArray(14) { 11_000 })
        val score = Insights.healthScore(active, settings, today)
        assertEquals("Low risk", score.band)
        assertTrue(score.score > 80)
    }

    @Test
    fun `smoking and inactivity pull the score down`() {
        val sedentary = days(*IntArray(14) { 2_000 })
        val heavier = settings.copy(weightKg = 95, smoker = true, age = 50)
        val score = Insights.healthScore(sedentary, heavier, today)
        // 100 - 20 obese - 22 inactive - 18 smoker - 6 age
        assertEquals(34, score.score)
        assertEquals("High risk", score.band)
    }

    // ---- recommended goals ----------------------------------------------------------

    @Test
    fun `step recommendation falls with age`() {
        assertEquals(10_000, Insights.recommended(settings.copy(age = 30)).stepGoal)
        assertEquals(8_500, Insights.recommended(settings.copy(age = 45)).stepGoal)
        assertEquals(7_000, Insights.recommended(settings.copy(age = 65)).stepGoal)
    }

    @Test
    fun `water is 35 ml per kg with a sex floor`() {
        // 90 kg clears both floors, so weight decides.
        assertEquals(3_200, Insights.recommended(settings.copy(weightKg = 90, sex = Sex.MALE)).waterGoalMl)
        // 45 kg would give 1,575 ml, below the male floor but above the female one.
        assertEquals(2_000, Insights.recommended(settings.copy(weightKg = 45, sex = Sex.MALE)).waterGoalMl)
        assertEquals(1_600, Insights.recommended(settings.copy(weightKg = 45, sex = Sex.FEMALE)).waterGoalMl)
    }

    @Test
    fun `calories follow the recommended steps and the user's weight`() {
        val light = Insights.recommended(settings.copy(weightKg = 55, age = 30)).calorieGoal
        val heavy = Insights.recommended(settings.copy(weightKg = 95, age = 30)).calorieGoal
        assertTrue(heavy > light)
        // Rounded to the slider's step so applying it lands exactly on a notch.
        assertEquals(0, heavy % 25)
    }

    @Test
    fun `activity targets come from the WHO week and do not vary by body`() {
        val small = Insights.recommended(settings.copy(weightKg = 50, age = 25, sex = Sex.FEMALE))
        val large = Insights.recommended(settings.copy(weightKg = 110, age = 70, sex = Sex.MALE))
        assertEquals(21, small.heartPointGoal)
        assertEquals(21, large.heartPointGoal)
        assertEquals(30, small.moveMinuteGoal)
        assertEquals(30, large.moveMinuteGoal)
    }

    @Test
    fun `height moves the calorie goal and nothing else`() {
        val short = Insights.recommended(settings.copy(heightCm = 150))
        val tall = Insights.recommended(settings.copy(heightCm = 195))

        // A longer stride covers more ground at the same cadence, so the same steps cost more.
        assertEquals(true, tall.calorieGoal > short.calorieGoal)
        assertEquals(short.stepGoal, tall.stepGoal)
        assertEquals(short.waterGoalMl, tall.waterGoalMl)
        assertEquals(short.heartPointGoal, tall.heartPointGoal)
        assertEquals(short.moveMinuteGoal, tall.moveMinuteGoal)
    }

    // ---- resting burn ---------------------------------------------------------------

    @Test
    fun `resting burn follows Mifflin-St Jeor`() {
        // 10x70 + 6.25x170 - 5x30 = 1612.5, then the sex term.
        val male = settings.copy(age = 30, sex = Sex.MALE)
        assertEquals(1617.5, Insights.restingKcalPerDay(male), 0.01)
        assertEquals(1451.5, Insights.restingKcalPerDay(settings.copy(age = 30, sex = Sex.FEMALE)), 0.01)
    }

    @Test
    fun `unspecified sex sits between the two`() {
        val male = Insights.restingKcalPerDay(settings.copy(sex = Sex.MALE))
        val female = Insights.restingKcalPerDay(settings.copy(sex = Sex.FEMALE))
        val unspecified = Insights.restingKcalPerDay(settings.copy(sex = Sex.UNSPECIFIED))
        assertTrue(unspecified in female..male)
        assertEquals((male + female) / 2, unspecified, 0.01)
    }

    @Test
    fun `resting burn dwarfs a day of walking, which is the point of showing it`() {
        assertTrue(Insights.restingKcalPerDay(settings) > 1_000)
    }

    @Test
    fun `the factors add up to the score and explain it`() {
        val sedentary = days(*IntArray(14) { 2_000 })
        val score = Insights.healthScore(sedentary, settings.copy(smoker = true, age = 50), today)
        assertEquals(score.score, score.factors.sumOf { it.delta })
        assertEquals(100, score.factors.first().delta)
        assertTrue(score.factors.any { it.label == "Smoker" && it.delta == -18 })
        assertTrue(score.factors.any { it.label == "Over 45" && it.delta == -6 })
    }

    @Test
    fun `the rating matches the band it is shown beside`() {
        val active = days(*IntArray(14) { 11_000 })
        assertEquals(Insights.Rating.GOOD, Insights.healthScore(active, settings, today).rating)
        val sedentary = days(*IntArray(14) { 2_000 })
        val poor = Insights.healthScore(sedentary, settings.copy(smoker = true, age = 50), today)
        assertEquals(Insights.Rating.CRITICAL, poor.rating)
    }

    @Test
    fun `bmi rating brackets match the bands`() {
        assertEquals(Insights.Rating.GOOD, Insights.bmiRating(22.0))
        assertEquals(Insights.Rating.WARNING, Insights.bmiRating(27.0))
        assertEquals(Insights.Rating.WARNING, Insights.bmiRating(17.0))
        assertEquals(Insights.Rating.CRITICAL, Insights.bmiRating(31.0))
    }

    @Test
    fun `the score stays inside its bounds`() {
        val worst = Insights.healthScore(
            emptyList(),
            settings.copy(weightKg = 140, smoker = true, age = 70),
            today,
        )
        assertTrue(worst.score >= 5)
    }
}
