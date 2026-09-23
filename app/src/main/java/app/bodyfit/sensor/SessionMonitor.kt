package app.bodyfit.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Watches the accelerometer during a timed exercise: whether the person is still moving,
 * and for rope skipping how many jumps they have made.
 *
 * Two questions, one signal. Stillness is the size of the bounce, and a jump is a peak in
 * it, so both come from the same running mean of the acceleration magnitude. Gravity is
 * removed with that mean rather than a fixed 9.81, because the reading drifts.
 *
 * Time is not accounted for here. The caller owns the clock and asks this only whether
 * movement is happening, which keeps the awkward part, what counts as elapsed, in one
 * place instead of two.
 *
 * Deliberately a plain class over one sample at a time: no Android types, so the whole
 * thing is testable against a synthetic signal.
 */
class SessionMonitor(
    /** Counting jumps costs nothing when off, so it is only on where it means something. */
    private val countJumps: Boolean = false,
    /** Bounce below this reads as a phone being carried, not exercise. */
    private val movementFloor: Float = 0.8f,
    /** How long the signal must stay quiet before the session is treated as paused. */
    private val stillnessMs: Long = 3_000,
) {

    /**
     * Jumps are counted by the same peak detector as steps, on different bounds: a rope
     * jump is a much larger bounce than a footfall and cannot exceed about 210 a minute.
     */
    private val jumpCounter = SoftwarePedometer(minStepIntervalMs = 280, minAmplitude = 2.5f)

    private var gravity = Float.NaN
    private var smoothed = 0f
    private var quietSinceMs = 0L

    /** False once the signal has been quiet for [stillnessMs]. Starts true, before any sample. */
    var moving: Boolean = true
        private set

    /** Jumps counted so far, always 0 when [countJumps] is false. */
    val jumps: Int get() = if (countJumps) jumpCounter.steps else 0

    fun onSample(timestampMs: Long, x: Float, y: Float, z: Float) {
        if (countJumps) jumpCounter.onSample(timestampMs, x, y, z)

        val magnitude = sqrt(x * x + y * y + z * z)
        if (gravity.isNaN()) {
            // The first sample sets the baseline instead of reading as one huge bounce.
            gravity = magnitude
            quietSinceMs = timestampMs
            return
        }
        gravity += (magnitude - gravity) * GRAVITY_ALPHA
        // The absolute bounce, smoothed: a jump swings both ways and the sign is noise.
        smoothed += (abs(magnitude - gravity) - smoothed) * SMOOTH_ALPHA

        if (smoothed >= movementFloor) {
            quietSinceMs = timestampMs
            moving = true
        } else if (timestampMs - quietSinceMs >= stillnessMs) {
            // Only after a continuous quiet stretch. A single quiet sample happens at the
            // top of every jump, where the body is briefly weightless.
            moving = false
        }
    }

    private companion object {
        /** Slow, so the mean tracks gravity rather than the exercise itself. */
        const val GRAVITY_ALPHA = 0.05f

        /** Fast enough to follow a jump, slow enough to ignore single-sample noise. */
        const val SMOOTH_ALPHA = 0.3f
    }
}

/**
 * Metabolic cost of rope skipping at [jumpsPerMinute], from the compendium's three paces.
 *
 * Anchored at 80, 110 and 140 jumps a minute for its slow, moderate and fast entries, and
 * interpolated between them so a measured rate is used rather than a band. Below the first
 * anchor the rate holds: slower than that is not a sustained skip.
 */
fun skippingMet(jumpsPerMinute: Double): Double {
    val anchors = listOf(80.0 to 8.8, 110.0 to 11.8, 140.0 to 12.3)
    if (jumpsPerMinute <= anchors.first().first) return anchors.first().second
    anchors.zipWithNext { (lowRate, lowMet), (highRate, highMet) ->
        if (jumpsPerMinute <= highRate) {
            val share = (jumpsPerMinute - lowRate) / (highRate - lowRate)
            return lowMet + share * (highMet - lowMet)
        }
    }
    return anchors.last().second
}
