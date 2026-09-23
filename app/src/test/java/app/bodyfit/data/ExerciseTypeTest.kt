package app.bodyfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A logged session writes straight into the day's calories and heart points, so its
 * arithmetic has to agree with what a walked minute earns or the totals stop meaning
 * one thing.
 */
class ExerciseTypeTest {

    @Test
    fun `calories use the same net MET basis as the step tracker`() {
        // Cycling at 7.0 MET for 30 minutes at 75 kg: (7.0 - 1) x 3.5 x 75 / 200 x 30.
        assertEquals(236.25, ExerciseType.CYCLING.kcal(minutes = 30.0, weightKg = 75), 0.01)
        // Running at 8.0 for the same half hour costs more.
        assertEquals(275.625, ExerciseType.RUNNING.kcal(minutes = 30.0, weightKg = 75), 0.01)
    }

    @Test
    fun `a heavier person burns proportionally more for the same session`() {
        val light = ExerciseType.SKIPPING.kcal(minutes = 10.0, weightKg = 60)
        val heavy = ExerciseType.SKIPPING.kcal(minutes = 10.0, weightKg = 120)

        assertEquals(light * 2, heavy, 0.01)
    }

    @Test
    fun `every activity here is vigorous, so each minute earns two heart points`() {
        ExerciseType.entries.forEach { type ->
            assertTrue("${type.label} is not vigorous", type.met >= 6.0)
            assertEquals(type.label, 40, type.heartPoints(minutes = 20.0))
        }
    }

    @Test
    fun `an unknown stored type is skipped rather than fatal`() {
        assertEquals(ExerciseType.RUNNING, ExerciseType.from("RUNNING"))
        assertEquals(null, ExerciseType.from("SWIMMING"))
        assertEquals(null, ExerciseType.from(""))
    }

    @Test
    fun `only running produces steps the tracker is already counting`() {
        assertTrue(ExerciseType.RUNNING.producesSteps)
        assertTrue(!ExerciseType.CYCLING.producesSteps)
        assertTrue(!ExerciseType.SKIPPING.producesSteps)
    }
}
