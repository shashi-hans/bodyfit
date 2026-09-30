package app.bodyfit.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A window is scored on the minutes it represents, not on the one minute it was meant to be.
 *
 * These pin the two ways a phone breaks that. A process the system freezes leaves a window
 * open far past its minute, and a service restarted after a kill reads the whole gap out of
 * the cumulative step counter and banks it in one go. Both used to score a single move
 * minute and at most two heart points, which is how a phone that counted 12,000 steps
 * reported 45 minutes of walking.
 */
class WindowScoreTest {

    private val weightKg = 70
    private val heightCm = 170

    @Test
    fun `an ordinary minute scores one minute`() {
        val score = Metrics.scoreWindow(110, 60_000, weightKg, heightCm)

        assertEquals(1, score.moveMinutes)
        assertEquals(1, score.heartPoints)
        assertEquals(110, score.cadence)
        assertFalse(score.inferred)
    }

    @Test
    fun `a window held open by a frozen process scores every minute it ran`() {
        // Twenty minutes of walking at 110 steps a minute, noticed all at once.
        val score = Metrics.scoreWindow(2_200, 20 * 60_000, weightKg, heightCm)

        assertEquals(20, score.moveMinutes)
        assertEquals(20, score.heartPoints)
        assertEquals(110, score.cadence)
        assertFalse(score.inferred)
    }

    @Test
    fun `calories scale with the window rather than with one minute`() {
        val minute = Metrics.scoreWindow(110, 60_000, weightKg, heightCm)
        val twenty = Metrics.scoreWindow(2_200, 20 * 60_000, weightKg, heightCm)

        assertEquals(minute.kcal * 20, twenty.kcal, 0.001)
    }

    @Test
    fun `a restart's worth of recovered steps is not one minute of sprinting`() {
        // 3,000 steps banked the instant the service came back, then a 60-second window.
        val score = Metrics.scoreWindow(3_000, 60_000, weightKg, heightCm)

        assertTrue(score.inferred)
        assertEquals(Metrics.RECOVERY_CADENCE, score.cadence)
        assertEquals(30, score.moveMinutes)
        // The duration can be inferred; the intensity cannot, so no heart points are given.
        assertEquals(0, score.heartPoints)
        assertTrue("earned ${score.kcal} kcal", score.kcal > 20.0)
    }

    @Test
    fun `a real sprint is still timed rather than inferred`() {
        // 200 steps a minute is fast but human, so the clock is believed.
        val score = Metrics.scoreWindow(200, 60_000, weightKg, heightCm)

        assertFalse(score.inferred)
        assertEquals(200, score.cadence)
        assertEquals(2, score.heartPoints)
    }

    @Test
    fun `a window too slow to be movement earns no minutes`() {
        // Five steps in a minute is standing up, not walking.
        val score = Metrics.scoreWindow(5, 60_000, weightKg, heightCm)

        assertEquals(0, score.moveMinutes)
        assertEquals(0, score.heartPoints)
    }

    @Test
    fun `an empty or untimed window scores nothing rather than dividing by zero`() {
        assertEquals(0, Metrics.scoreWindow(0, 60_000, weightKg, heightCm).moveMinutes)
        assertEquals(0, Metrics.scoreWindow(100, 0, weightKg, heightCm).moveMinutes)
    }

    @Test
    fun `the fix restores what a frozen phone was losing`() {
        // What a phone frozen by its OEM did with twenty minutes of walking, before and after.
        val steps = 2_200
        val elapsed = 20 * 60_000L
        val fixed = Metrics.scoreWindow(steps, elapsed, weightKg, heightCm)

        // The old rule: one minute, whatever the window held.
        val oldMoveMinutes = 1
        val oldHeartPoints = Metrics.heartPointsForMinute(110)

        assertTrue(fixed.moveMinutes > oldMoveMinutes * 10)
        assertTrue(fixed.heartPoints > oldHeartPoints * 10)
    }

    @Test
    fun `a long freeze with few steps is not an hour of walking`() {
        // A thousand steps somewhere inside a ninety-minute freeze. Crediting the whole
        // gap would turn sitting still into movement; the steps bound the walk instead.
        val score = Metrics.scoreWindow(1_000, 90 * 60_000, weightKg, heightCm)

        assertTrue(score.inferred)
        assertEquals(10, score.moveMinutes)
        assertEquals(Metrics.RECOVERY_CADENCE, score.cadence)
        assertEquals(0, score.heartPoints)
    }

    @Test
    fun `an inferred walk never outlasts the window it sat in`() {
        // Ten minutes open, and only enough steps for four of them.
        val score = Metrics.scoreWindow(400, 10 * 60_000, weightKg, heightCm)

        assertTrue(score.moveMinutes <= 10)
        assertEquals(4, score.moveMinutes)
    }

    @Test
    fun `movement too slight for a move minute earns no calories either`() {
        // One step and nine steps used to cost the same as a full slow minute, because the
        // MET curve floors at its slowest anchor and the calorie was never gated.
        for (steps in listOf(1, 2, 5, 9)) {
            val score = Metrics.scoreWindow(steps, 60_000, weightKg, heightCm)
            assertEquals("$steps steps", 0, score.moveMinutes)
            assertEquals("$steps steps", 0.0, score.kcal, 0.0001)
            assertEquals("$steps steps", 0, score.heartPoints)
        }
    }

    @Test
    fun `a slow walk at the floor still earns its calories`() {
        val score = Metrics.scoreWindow(Metrics.MIN_MOVE_STEPS, 60_000, weightKg, heightCm)

        assertEquals(1, score.moveMinutes)
        assertTrue("earned ${score.kcal} kcal", score.kcal > 0.0)
    }
}
