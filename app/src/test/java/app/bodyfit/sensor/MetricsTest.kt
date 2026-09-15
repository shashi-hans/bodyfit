package app.bodyfit.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsTest {

    @Test
    fun `stride is 41 percent of height`() {
        assertEquals(0.7055, Metrics.strideMeters(170), 0.0001)
    }

    @Test
    fun `ten thousand steps at 170 cm is about seven kilometres`() {
        assertEquals(7.055, Metrics.distanceKm(10_000, 170), 0.001)
    }

    @Test
    fun `heart points follow the cadence bands`() {
        assertEquals(0, Metrics.heartPointsForMinute(99))
        assertEquals(1, Metrics.heartPointsForMinute(100))
        assertEquals(1, Metrics.heartPointsForMinute(129))
        assertEquals(2, Metrics.heartPointsForMinute(130))
    }

    @Test
    fun `any walking is a move minute once it clears the floor, however slow`() {
        assertEquals(false, Metrics.isMoveMinute(0))
        assertEquals(false, Metrics.isMoveMinute(9))
        assertEquals(true, Metrics.isMoveMinute(10))
        assertEquals(true, Metrics.isMoveMinute(59))
        assertEquals(true, Metrics.isMoveMinute(60))
    }

    @Test
    fun `a stroll earns calories, a few steps across a room does not`() {
        // 10 steps a minute is the lowest anchor, 2.0 MET: (2.0 - 1.0) x 3.5 x 70 / 200
        assertEquals(1.225, Metrics.kcalForMinute(10, 70), 0.0001)
        assertEquals(0.0, Metrics.kcalForMinute(9, 70), 0.0001)
        assertEquals(0.0, Metrics.kcalForMinute(0, 70), 0.0001)
    }

    @Test
    fun `the published thresholds carry the MET they were defined against`() {
        assertEquals(3.0, Metrics.metForCadence(100), 0.0001)
        assertEquals(6.0, Metrics.metForCadence(130), 0.0001)
        assertEquals(2.8, Metrics.metForCadence(60), 0.0001)
        assertEquals(2.0, Metrics.metForCadence(10), 0.0001)
    }

    @Test
    fun `cost rises smoothly between anchors instead of jumping at a band edge`() {
        // Halfway from 100 to 130 is halfway from 3.0 to 6.0 MET.
        assertEquals(4.5, Metrics.metForCadence(115), 0.0001)
        // One step either side of the old 130 band edge now differs by a fraction.
        val below = Metrics.kcalForMinute(129, 70)
        val above = Metrics.kcalForMinute(130, 70)
        assertEquals(true, above - below < 0.15)
        assertEquals(true, above > below)
    }

    @Test
    fun `every extra step a minute costs more, with no flat stretch`() {
        var previous = 0.0
        (Metrics.MIN_MOVE_STEPS..Metrics.VIGOROUS_CADENCE).forEach { cadence ->
            val cost = Metrics.kcalForMinute(cadence, 70)
            assertEquals("cadence $cadence", true, cost > previous)
            previous = cost
        }
    }

    @Test
    fun `past the top anchor the walking curve stops rising`() {
        val vigorous = Metrics.metForCadence(Metrics.VIGOROUS_CADENCE)
        assertEquals(vigorous, Metrics.metForCadence(180), 0.0001)
        assertEquals(vigorous, Metrics.metForCadence(240), 0.0001)
    }

    @Test
    fun `the same cadence costs a tall person more than a short one`() {
        val short = Metrics.kcalForMinute(110, weightKg = 70, heightCm = 150)
        val reference = Metrics.kcalForMinute(110, weightKg = 70, heightCm = 170)
        val tall = Metrics.kcalForMinute(110, weightKg = 70, heightCm = 190)

        assertEquals(true, short < reference)
        assertEquals(true, reference < tall)
        // A longer stride covers more ground in the same minute, which is real work.
        assertEquals(110.0, Metrics.effectiveCadence(110, 170), 0.0001)
        assertEquals(97.0, Metrics.effectiveCadence(110, 150), 0.5)
        assertEquals(123.0, Metrics.effectiveCadence(110, 190), 0.5)
    }

    @Test
    fun `calories exclude the resting burn of the same minute`() {
        // 100 steps a minute at the reference height is 3.0 MET, so 2.0 MET of activity.
        assertEquals(2.45, Metrics.kcalForMinute(100, 70), 0.0001)
        // Doubling weight doubles the cost of the same minute.
        assertEquals(4.90, Metrics.kcalForMinute(100, 140), 0.0001)
    }

    @Test
    fun `a fast minute costs more than a slow minute at the same weight`() {
        val slow = Metrics.kcalForMinute(80, 70)
        val fast = Metrics.kcalForMinute(140, 70)
        assertEquals(true, fast > slow)
    }
}
