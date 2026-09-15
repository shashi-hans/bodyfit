package app.bodyfit.sensor

/**
 * Turns raw step counts into the numbers the app shows.
 *
 * Everything here is a pure function of steps, cadence and the user's body
 * measurements, so the same rules apply on the tracker service and in the UI.
 */
object Metrics {

    /** Stride length as a fraction of standing height, the common walking estimate. */
    private const val STRIDE_FACTOR = 0.415

    /**
     * Steps in a window below which it does not count as movement at all.
     *
     * A window opens on the first step, so without a floor a single step to the kitchen
     * would earn a whole move minute. Ten steps is roughly a room's width: low enough
     * that real slow walking still counts, high enough that standing up does not.
     */
    const val MIN_MOVE_STEPS = 10

    /** Steps per minute from which walking is brisk enough to carry a metabolic cost. */
    const val ACTIVE_CADENCE = 60

    /** Steps per minute that scores one heart point (moderate effort). */
    const val MODERATE_CADENCE = 100

    /** Steps per minute that scores two heart points (vigorous effort). */
    const val VIGOROUS_CADENCE = 130

    fun strideMeters(heightCm: Int): Double = heightCm * STRIDE_FACTOR / 100.0

    fun distanceKm(steps: Int, heightCm: Int): Double = steps * strideMeters(heightCm) / 1000.0

    /**
     * Whether a measured window counts as a move minute.
     *
     * Any walking counts once it clears [MIN_MOVE_STEPS], however slow. Pace decides how
     * many heart points and calories it earns, not whether the minute happened at all.
     */
    fun isMoveMinute(cadence: Int): Boolean = cadence >= MIN_MOVE_STEPS

    /**
     * Heart points for one minute at [cadence] steps per minute. Phones without a
     * heart-rate sensor score intensity from cadence: moderate effort earns one
     * point a minute, vigorous effort earns two.
     */
    fun heartPointsForMinute(cadence: Int): Int = when {
        cadence >= VIGOROUS_CADENCE -> 2
        cadence >= MODERATE_CADENCE -> 1
        else -> 0
    }

    /**
     * Height the cadence anchors below describe.
     *
     * The published thresholds are population figures, measured on people of ordinary
     * build. Treating them as if they held at any height would charge a short person
     * and a tall person the same for covering different ground.
     */
    const val REFERENCE_HEIGHT_CM = 170

    /**
     * Metabolic cost by cadence, as `steps per minute to MET`, ascending.
     *
     * 100 and 130 steps a minute are the published moderate and vigorous thresholds, and
     * they carry the 3.0 and 6.0 MET those thresholds were defined against. The two lower
     * anchors are the compendium's slow and very slow walking. Anything in between is
     * interpolated rather than rounded to a band, so walking faster always earns more and
     * a single step never moves the rate by more than a fraction.
     */
    private val MET_ANCHORS = listOf(
        MIN_MOVE_STEPS to 2.0,
        ACTIVE_CADENCE to 2.8,
        MODERATE_CADENCE to 3.0,
        VIGOROUS_CADENCE to 6.0,
    )

    /**
     * [cadence] restated as the cadence a person of [REFERENCE_HEIGHT_CM] would need to
     * cover the same ground in the same time.
     *
     * Energy cost tracks speed, and speed is cadence times stride. At 110 steps a minute
     * someone 150 cm walks 4.1 km/h while someone 190 cm walks 5.2 km/h, which is real
     * work the raw count cannot see.
     */
    fun effectiveCadence(cadence: Int, heightCm: Int): Double =
        cadence * strideMeters(heightCm) / strideMeters(REFERENCE_HEIGHT_CM)

    /**
     * Metabolic equivalent of one minute at [cadence] steps per minute for a person of
     * [heightCm], interpolated between [MET_ANCHORS].
     *
     * A window that does not clear [MIN_MOVE_STEPS] is not movement, so it scores the 1.0
     * of sitting still. Past the top anchor the rate holds: beyond about 130 steps a
     * minute a person is running, and the walking curve stops describing them.
     */
    fun metForCadence(cadence: Int, heightCm: Int = REFERENCE_HEIGHT_CM): Double {
        if (cadence < MIN_MOVE_STEPS) return 1.0
        val effective = effectiveCadence(cadence, heightCm)
        val first = MET_ANCHORS.first()
        if (effective <= first.first) return first.second
        MET_ANCHORS.zipWithNext { (lowCadence, lowMet), (highCadence, highMet) ->
            if (effective <= highCadence) {
                val share = (effective - lowCadence) / (highCadence - lowCadence)
                return lowMet + share * (highMet - lowMet)
            }
        }
        return MET_ANCHORS.last().second
    }

    /**
     * Energy cost of one active minute, `(MET - 1) x 3.5 x kg / 200` kcal.
     *
     * The MET formula gives gross expenditure, which includes the 1.0 MET the body
     * spends at rest. Subtracting that leaves activity burn alone, so the daily figure
     * answers "what did moving cost me", not "what did I burn today".
     */
    fun kcalForMinute(cadence: Int, weightKg: Int, heightCm: Int = REFERENCE_HEIGHT_CM): Double =
        (metForCadence(cadence, heightCm) - 1.0).coerceAtLeast(0.0) * 3.5 * weightKg / 200.0
}
