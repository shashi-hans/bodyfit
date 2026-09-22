package app.bodyfit.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The fallback counter is the only source of steps on a phone with no pedometer chip, so
 * these pin what it does with walking, stillness, noise and a shaken phone.
 *
 * Signals are synthetic: a walking phone bounces roughly sinusoidally about gravity, one
 * cycle per step. Real gait is messier, so the tolerances are loose on purpose; what is
 * pinned is that the count tracks the cycles, not that it is exact.
 */
class SoftwarePedometerTest {

    private val gravity = 9.81f
    private val sampleHz = 25
    private val sampleMs = 1000L / sampleHz

    /** Feeds [seconds] of walking at [stepsPerSecond], with the bounce on one axis. */
    private fun walk(
        pedometer: SoftwarePedometer,
        seconds: Double,
        stepsPerSecond: Double,
        amplitude: Float = 2.0f,
        startMs: Long = 0,
        noise: Float = 0f,
    ): Long {
        val samples = (seconds * sampleHz).toInt()
        val random = Random(7)
        var t = startMs
        repeat(samples) { i ->
            val phase = 2 * PI * stepsPerSecond * (i.toDouble() / sampleHz)
            val jitter = if (noise > 0f) (random.nextFloat() - 0.5f) * 2 * noise else 0f
            pedometer.onSample(t, 0f, 0f, gravity + amplitude * sin(phase).toFloat() + jitter)
            t += sampleMs
        }
        return t
    }

    @Test
    fun `steady walking is counted within a step or two of the truth`() {
        val pedometer = SoftwarePedometer()
        walk(pedometer, seconds = 20.0, stepsPerSecond = 2.0)

        // 20 s at 2 steps a second is 40 steps; the first cycle primes the baseline.
        assertTrue("counted ${pedometer.steps}", pedometer.steps in 37..40)
    }

    @Test
    fun `a slower walk is counted too`() {
        val pedometer = SoftwarePedometer()
        walk(pedometer, seconds = 20.0, stepsPerSecond = 1.2)

        assertTrue("counted ${pedometer.steps}", pedometer.steps in 21..24)
    }

    @Test
    fun `a still phone counts nothing`() {
        val pedometer = SoftwarePedometer()
        var t = 0L
        repeat(25 * 30) {
            pedometer.onSample(t, 0f, 0f, gravity)
            t += sampleMs
        }

        assertEquals(0, pedometer.steps)
    }

    @Test
    fun `sensor noise on a still phone is not walking`() {
        val pedometer = SoftwarePedometer()
        val random = Random(3)
        var t = 0L
        repeat(25 * 30) {
            val jitter = (random.nextFloat() - 0.5f) * 0.3f
            pedometer.onSample(t, jitter, jitter, gravity + jitter)
            t += sampleMs
        }

        assertEquals(0, pedometer.steps)
    }

    @Test
    fun `a phone in a bag still counts, on a much smaller bounce`() {
        val pedometer = SoftwarePedometer()
        walk(pedometer, seconds = 20.0, stepsPerSecond = 2.0, amplitude = 0.9f)

        assertTrue("counted ${pedometer.steps}", pedometer.steps in 34..40)
    }

    @Test
    fun `a noisy walk is still counted`() {
        val pedometer = SoftwarePedometer()
        walk(pedometer, seconds = 20.0, stepsPerSecond = 2.0, noise = 0.4f)

        assertTrue("counted ${pedometer.steps}", pedometer.steps in 35..42)
    }

    @Test
    fun `the refractory period caps an implausible cadence`() {
        val pedometer = SoftwarePedometer()
        // 8 Hz is a shaken phone, not a run. The 250 ms floor caps it at 4 a second.
        walk(pedometer, seconds = 10.0, stepsPerSecond = 8.0, amplitude = 6f)

        assertTrue("counted ${pedometer.steps}", pedometer.steps <= 41)
    }

    @Test
    fun `walking after a rest resumes counting`() {
        val pedometer = SoftwarePedometer()
        var t = walk(pedometer, seconds = 10.0, stepsPerSecond = 2.0)
        val afterFirst = pedometer.steps

        repeat(25 * 60) {
            pedometer.onSample(t, 0f, 0f, gravity)
            t += sampleMs
        }
        assertEquals("rest added steps", afterFirst, pedometer.steps)

        walk(pedometer, seconds = 10.0, stepsPerSecond = 2.0, startMs = t)
        assertTrue("second walk added too little", pedometer.steps - afterFirst >= 17)
    }

    @Test
    fun `orientation does not change the count`() {
        val upright = SoftwarePedometer()
        walk(upright, seconds = 15.0, stepsPerSecond = 2.0)

        // Same bounce, phone lying on its side: the magnitude is what is measured.
        val sideways = SoftwarePedometer()
        val samples = (15.0 * sampleHz).toInt()
        var t = 0L
        repeat(samples) { i ->
            val phase = 2 * PI * 2.0 * (i.toDouble() / sampleHz)
            sideways.onSample(t, gravity + 2.0f * sin(phase).toFloat(), 0f, 0f)
            t += sampleMs
        }

        assertEquals(upright.steps, sideways.steps)
    }
}
