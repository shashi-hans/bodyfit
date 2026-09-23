package app.bodyfit.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GPS decides what a ride is worth, and a single bad fix can invent a sprint, so what is
 * accepted and what is thrown away is pinned here.
 */
class SpeedMonitorTest {

    /** A degree of latitude is about 111 km, which makes a known-distance track easy. */
    private val DEG_PER_100M = 100.0 / 111_320.0

    @Test
    fun `a straight track measures its own length`() {
        val monitor = SpeedMonitor()
        var lat = 12.9716
        var t = 0L
        // Ten hops of 100 m, one every 20 s: a kilometre in 200 s, which is 18 km/h.
        repeat(11) {
            monitor.onFix(t, lat, 77.5946, accuracyMetres = 5f)
            lat += DEG_PER_100M
            t += 20_000
        }

        assertEquals(1000.0, monitor.metres, 15.0)
        assertEquals(18.0, monitor.averageKmh, 0.5)
    }

    @Test
    fun `a vague fix is ignored rather than measured`() {
        val monitor = SpeedMonitor()
        monitor.onFix(0, 12.9716, 77.5946, accuracyMetres = 5f)
        // 100 m away but reported to within 80 m, which cannot tell movement from noise.
        monitor.onFix(20_000, 12.9716 + DEG_PER_100M, 77.5946, accuracyMetres = 80f)

        assertEquals(0.0, monitor.metres, 0.01)
    }

    @Test
    fun `a jump no cyclist could make is discarded`() {
        val monitor = SpeedMonitor()
        monitor.onFix(0, 12.9716, 77.5946, accuracyMetres = 5f)
        // 5 km in 10 seconds is 1,800 km/h: two bad fixes, not a sprint.
        monitor.onFix(10_000, 12.9716 + DEG_PER_100M * 50, 77.5946, accuracyMetres = 5f)

        assertEquals(0.0, monitor.metres, 0.01)
    }

    @Test
    fun `a standing start reports nothing rather than dividing by zero`() {
        val monitor = SpeedMonitor()
        assertEquals(0.0, monitor.averageKmh, 0.001)

        monitor.onFix(0, 12.9716, 77.5946, accuracyMetres = 5f)
        assertEquals(0.0, monitor.averageKmh, 0.001)
    }

    @Test
    fun `running MET rises with pace and holds past the ends`() {
        assertEquals(8.3, runningMet(8.0), 0.01)
        assertEquals(9.8, runningMet(10.0), 0.01)
        assertEquals(14.5, runningMet(14.0), 0.01)
        // Slower than the first anchor is a jog the walking curve describes better.
        assertEquals(8.3, runningMet(5.0), 0.01)
        assertEquals(14.5, runningMet(25.0), 0.01)
    }

    @Test
    fun `cycling MET rises with speed and holds past the ends`() {
        assertEquals(5.8, cyclingMet(16.0), 0.01)
        assertEquals(8.0, cyclingMet(20.0), 0.01)
        assertEquals(12.0, cyclingMet(30.0), 0.01)
        assertEquals(5.8, cyclingMet(8.0), 0.01)
        assertEquals(12.0, cyclingMet(50.0), 0.01)
    }

    @Test
    fun `a measured ride can cost more or less than the assumed figure`() {
        // The assumed cycling MET is 7.0, which a gentle ride beats and a fast one exceeds.
        assertTrue("a 16 km/h ride should cost less than assumed", cyclingMet(16.0) < 7.0)
        assertTrue("a 25 km/h ride should cost more than assumed", cyclingMet(25.0) > 7.0)
    }

    @Test
    fun `haversine matches a known separation`() {
        // One degree of latitude at the equator is about 110.6 km.
        val metres = haversineMetres(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, metres, 500.0)
    }
}
