package app.bodyfit.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Auto-pause decides whether a traffic light is billed as exercise, and the jump count
 * decides what a skipping session is worth, so both are pinned against a synthetic signal.
 */
class SessionMonitorTest {

    private val gravity = 9.81f
    private val sampleHz = 25
    private val sampleMs = 1000L / sampleHz

    /** Feeds [seconds] of periodic bouncing at [hz], or stillness when amplitude is 0. */
    private fun feed(
        monitor: SessionMonitor,
        seconds: Double,
        hz: Double,
        amplitude: Float,
        startMs: Long = 0,
    ): Long {
        var t = startMs
        repeat((seconds * sampleHz).toInt()) { i ->
            val phase = 2 * PI * hz * (i.toDouble() / sampleHz)
            monitor.onSample(t, 0f, 0f, gravity + amplitude * sin(phase).toFloat())
            t += sampleMs
        }
        return t
    }

    @Test
    fun `a still phone pauses the session`() {
        val monitor = SessionMonitor()
        feed(monitor, seconds = 10.0, hz = 0.0, amplitude = 0f)

        assertFalse(monitor.moving)
    }

    @Test
    fun `a brief stop does not pause, because a jump is briefly weightless`() {
        val monitor = SessionMonitor()
        var t = feed(monitor, seconds = 6.0, hz = 2.0, amplitude = 4f)
        // One second of quiet, well under the three the monitor waits for.
        feed(monitor, seconds = 1.0, hz = 0.0, amplitude = 0f, startMs = t)

        assertTrue(monitor.moving)
    }

    @Test
    fun `movement after a pause resumes the session`() {
        val monitor = SessionMonitor()
        var t = feed(monitor, seconds = 10.0, hz = 0.0, amplitude = 0f)
        assertFalse(monitor.moving)

        feed(monitor, seconds = 3.0, hz = 2.0, amplitude = 4f, startMs = t)
        assertTrue(monitor.moving)
    }

    @Test
    fun `walking about keeps a session running`() {
        val monitor = SessionMonitor()
        feed(monitor, seconds = 10.0, hz = 1.8, amplitude = 2.0f)

        assertTrue(monitor.moving)
    }

    @Test
    fun `jumps are counted only when asked for`() {
        val off = SessionMonitor(countJumps = false)
        feed(off, seconds = 10.0, hz = 2.0, amplitude = 5f)
        assertEquals(0, off.jumps)

        val on = SessionMonitor(countJumps = true)
        feed(on, seconds = 10.0, hz = 2.0, amplitude = 5f)
        // 10 s at 2 Hz is 20 jumps; the first cycle primes the baseline.
        assertTrue("counted ${on.jumps}", on.jumps in 17..20)
    }

    @Test
    fun `a slow walk is not mistaken for skipping`() {
        val monitor = SessionMonitor(countJumps = true)
        // Walking bounce is far smaller than a rope jump and falls under the floor.
        feed(monitor, seconds = 20.0, hz = 1.8, amplitude = 1.2f)

        assertEquals(0, monitor.jumps)
    }

    @Test
    fun `skipping MET follows the compendium's three paces`() {
        assertEquals(8.8, skippingMet(80.0), 0.01)
        assertEquals(11.8, skippingMet(110.0), 0.01)
        assertEquals(12.3, skippingMet(140.0), 0.01)
    }

    @Test
    fun `skipping MET interpolates between paces and holds at the ends`() {
        assertEquals(10.3, skippingMet(95.0), 0.01)
        // Slower than the slowest anchor is not a sustained skip, so the rate holds.
        assertEquals(8.8, skippingMet(40.0), 0.01)
        assertEquals(12.3, skippingMet(200.0), 0.01)
    }
}
