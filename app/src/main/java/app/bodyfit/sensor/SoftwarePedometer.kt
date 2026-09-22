package app.bodyfit.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Counts steps from raw accelerometer samples, for phones with no step counter chip.
 *
 * A walking phone bounces once per step whichever way it is carried, so the vertical
 * component is not used: the magnitude of the acceleration vector is, which makes the
 * count independent of the phone's orientation in a pocket or a bag.
 *
 * Gravity is removed with a slow running mean rather than a fixed 9.81, because the
 * reading drifts with temperature and calibration. What is left is the bounce, and a step
 * is one upward crossing of a threshold that tracks the recent size of that bounce. A
 * fixed threshold under-counts a phone in a bag and over-counts one in a hand.
 *
 * Deliberately a plain class over one sample at a time: no Android types, no sensor
 * registration, so the whole thing is testable against a synthetic signal.
 */
class SoftwarePedometer(
    /** Shortest gap between steps, which caps the count at 240 steps a minute. */
    private val minStepIntervalMs: Long = 250,
    /** Bounce smaller than this is noise, not walking. Metres per second squared. */
    private val minAmplitude: Float = 0.6f,
) {

    /** Running mean of the magnitude, standing in for gravity plus any bias. */
    private var gravity = Float.NaN

    /** Smoothed bounce, so one jittery sample cannot read as a step. */
    private var smoothed = 0f

    /** Recent extremes of the bounce, which set the threshold a step has to cross. */
    private var recentHigh = 0f
    private var recentLow = 0f

    private var wasAbove = false
    private var lastStepMs = 0L

    /** Steps counted since this instance was created. */
    var steps: Int = 0
        private set

    /**
     * Feeds one accelerometer sample and returns how many steps it completed, 0 or 1.
     *
     * [timestampMs] is wall-clock milliseconds; only differences are used, so any
     * monotonic source works as long as it is consistent.
     */
    fun onSample(timestampMs: Long, x: Float, y: Float, z: Float): Int {
        val magnitude = sqrt(x * x + y * y + z * z)

        if (gravity.isNaN()) {
            // First sample sets the baseline instead of reading as one enormous bounce.
            gravity = magnitude
            return 0
        }
        gravity += (magnitude - gravity) * GRAVITY_ALPHA
        val bounce = magnitude - gravity
        smoothed += (bounce - smoothed) * SMOOTH_ALPHA

        // The extremes decay toward the current value, so the threshold follows a change
        // of pace or pocket within a couple of seconds instead of staying stuck.
        recentHigh = if (smoothed > recentHigh) smoothed else recentHigh * DECAY
        recentLow = if (smoothed < recentLow) smoothed else recentLow * DECAY

        val amplitude = recentHigh - recentLow
        if (amplitude < minAmplitude) {
            wasAbove = false
            return 0
        }

        // Halfway between the recent extremes, biased up a little so the signal has to
        // rise convincingly rather than hover across the midpoint.
        val threshold = (recentHigh + recentLow) / 2f + amplitude * THRESHOLD_BIAS
        val isAbove = smoothed > threshold

        var counted = 0
        if (isAbove && !wasAbove && timestampMs - lastStepMs >= minStepIntervalMs) {
            steps++
            counted = 1
            lastStepMs = timestampMs
        }
        wasAbove = isAbove
        return counted
    }

    /** Forgets the signal history, for a gap long enough that the old state means nothing. */
    fun reset() {
        gravity = Float.NaN
        smoothed = 0f
        recentHigh = 0f
        recentLow = 0f
        wasAbove = false
    }

    private companion object {
        /** Slow, so the mean tracks gravity rather than the walking bounce itself. */
        const val GRAVITY_ALPHA = 0.05f

        /** Fast enough to keep the shape of a step, slow enough to drop single-sample noise. */
        const val SMOOTH_ALPHA = 0.4f

        /** Per-sample decay of the recent extremes, about a two-second memory at 25 Hz. */
        const val DECAY = 0.98f

        const val THRESHOLD_BIAS = 0.1f
    }
}

/** True when [value] is far enough from zero to be worth treating as movement. */
internal fun isSignificant(value: Float, floor: Float): Boolean = abs(value) >= floor
