package app.bodyfit.insights

import app.bodyfit.data.DailyRecord
import app.bodyfit.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The score is shown beside a risk band, so reading "no data" as "no activity" tells a
 * healthy new user they are at elevated risk on their first morning.
 */
class NewUserScoreTest {

    private val today = LocalDate.of(2026, 9, 18)
    private val settings = UserSettings(heightCm = 175, weightKg = 70, age = 30)

    private fun day(daysAgo: Int, steps: Int) =
        DailyRecord(today.minusDays(daysAgo.toLong()).toString(), steps = steps)

    @Test
    fun `a fresh install is not called sedentary`() {
        val score = Insights.healthScore(emptyList(), settings, today)

        assertEquals(100, score.score)
        assertEquals("Low risk", score.band)
        assertTrue(score.factors.any { it.label.contains("Not enough days") })
        assertTrue(score.factors.none { it.delta < 0 })
    }

    @Test
    fun `today alone is not enough to judge, however little was walked`() {
        val score = Insights.healthScore(listOf(day(0, steps = 12)), settings, today)

        assertEquals(100, score.score)
    }

    @Test
    fun `the step term starts counting once three full days are tracked`() {
        val sparse = listOf(day(1, 500), day(2, 500))
        val enough = sparse + day(3, 500)

        assertEquals(100, Insights.healthScore(sparse, settings, today).score)
        // Three tracked days at 500 steps averages under 4,000, which is the -22 band.
        assertEquals(78, Insights.healthScore(enough, settings, today).score)
    }

    @Test
    fun `a day with no steps does not count as tracked`() {
        val days = listOf(day(1, 0), day(2, 0), day(3, 0), day(4, 900))

        assertEquals(1, Insights.trackedDays(days, 14, today))
        assertEquals(100, Insights.healthScore(days, settings, today).score)
    }

    @Test
    fun `an established walker is still scored normally`() {
        val days = (1..14).map { day(it, steps = 11_000) }
        val score = Insights.healthScore(days, settings, today)

        assertTrue(score.factors.any { it.label.contains("10,000 steps") })
        assertEquals(100, score.score)
    }
}
