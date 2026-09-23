package app.bodyfit.sensor

/**
 * Turns a stream of GPS fixes into a distance and an average speed, and that speed into an
 * effort, for the activities whose intensity the accelerometer cannot see.
 *
 * Coordinates are consumed and dropped. Nothing here keeps a position, and nothing it
 * returns could be used to reconstruct where the session happened: only how far and how
 * fast. That is deliberate, and it is what keeps a route trace out of the database.
 *
 * Deliberately a plain class over one fix at a time: no Android types, so the whole thing
 * is testable against a synthetic track.
 */
class SpeedMonitor {

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastAt = 0L

    /** Metres covered, summed over the fixes seen. */
    var metres: Double = 0.0
        private set

    /** Milliseconds between the first and last fix, which is what the average divides by. */
    var elapsedMs: Long = 0
        private set

    /** Average speed in km/h over the whole session, or 0 before two fixes have arrived. */
    val averageKmh: Double
        get() = if (elapsedMs <= 0) 0.0 else metres / 1000.0 / (elapsedMs / 3_600_000.0)

    /**
     * Adds a fix. [accuracyMetres] is the horizontal accuracy the platform reports.
     *
     * A fix worse than [WORST_ACCURACY_M] is dropped: an urban canyon reading can be out by
     * a hundred metres, and two of them in a row invent a sprint that never happened.
     */
    fun onFix(timestampMs: Long, latitude: Double, longitude: Double, accuracyMetres: Float) {
        if (accuracyMetres > WORST_ACCURACY_M) return

        if (lastLat.isNaN()) {
            lastLat = latitude
            lastLon = longitude
            lastAt = timestampMs
            return
        }

        val step = haversineMetres(lastLat, lastLon, latitude, longitude)
        val gapMs = timestampMs - lastAt
        lastLat = latitude
        lastLon = longitude
        lastAt = timestampMs
        if (gapMs <= 0) return

        // A jump implying a speed no runner or cyclist reaches is a bad fix, not movement.
        val impliedKmh = step / 1000.0 / (gapMs / 3_600_000.0)
        if (impliedKmh > IMPLAUSIBLE_KMH) return

        metres += step
        elapsedMs += gapMs
    }
}

/** Beyond this a fix is too vague to measure with. */
private const val WORST_ACCURACY_M = 35f

/** Faster than any running or cycling session, so it is a jump between bad fixes. */
private const val IMPLAUSIBLE_KMH = 80.0

/** Mean Earth radius, which is the usual approximation for distances of this size. */
private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle distance between two points, accurate enough well below a kilometre. */
internal fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

/**
 * Metabolic cost of running at [kmh], from the compendium's treadmill entries.
 *
 * Anchored at 8, 10, 12 and 14 km/h, interpolated between. Below the first anchor the rate
 * holds: slower than 8 km/h is a jog the walking curve already describes better.
 */
fun runningMet(kmh: Double): Double = interpolate(
    kmh,
    listOf(8.0 to 8.3, 10.0 to 9.8, 12.0 to 11.8, 14.0 to 14.5),
)

/**
 * Metabolic cost of cycling at [kmh], from the compendium's speed bands.
 *
 * Anchored at 16, 20, 25 and 30 km/h. This is the figure the accelerometer could never
 * supply: a phone on a bike sees vibration, and speed cannot be recovered from
 * acceleration because the integration drifts within seconds.
 */
fun cyclingMet(kmh: Double): Double = interpolate(
    kmh,
    listOf(16.0 to 5.8, 20.0 to 8.0, 25.0 to 10.0, 30.0 to 12.0),
)

/** Reads [value] off a curve through [anchors], holding flat past either end. */
private fun interpolate(value: Double, anchors: List<Pair<Double, Double>>): Double {
    if (value <= anchors.first().first) return anchors.first().second
    anchors.zipWithNext { (lowX, lowY), (highX, highY) ->
        if (value <= highX) {
            val share = (value - lowX) / (highX - lowX)
            return lowY + share * (highY - lowY)
        }
    }
    return anchors.last().second
}
