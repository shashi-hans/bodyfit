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

    /**
     * Steps a minute past which the clock is wrong rather than the legs.
     *
     * Sustained running sits near 180 and a sprinter's peak near 250, so nothing above
     * this was produced by a person in the time the window thinks it ran. It is produced
     * two ways, both of them the phone's doing: a process frozen by the system leaves a
     * window open far longer than a minute, and a service restarted after a kill reads
     * the whole gap out of the cumulative counter and banks it in one go.
     */
    const val MAX_HUMAN_CADENCE = 220

    /**
     * Minutes past which a window's own clock stops describing what happened inside it.
     *
     * The tick runs every five seconds, so a window is normally closed within moments of
     * its minute. Anything much longer means the process was not running for most of it,
     * and the steps sit somewhere unknown inside the gap rather than spread evenly across
     * it.
     */
    const val LATE_TICK_MINUTES = 2.0

    /**
     * The pace assumed for steps whose timing was lost.
     *
     * A moderate walk. It cannot be measured after the fact, so it is stated rather than
     * implied, and it is used only to work out how long the steps must have taken.
     */
    const val RECOVERY_CADENCE = 100

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
     * Every anchor comes from the CADENCE-adults work, so the whole curve traces to one
     * source rather than mixing cadence research with compendium walking speeds.
     *
     * 100 and 130 steps a minute are the published moderate and vigorous thresholds,
     * carrying the 3.0 and 6.0 MET they were defined against; between them the cost rises
     * about 1 MET per 10 steps a minute, which the interpolation reproduces exactly.
     *
     * Below a breakpoint at 97.2 steps a minute the same work fits a much flatter line,
     * `METs = 1.2606 + 0.0141 x cadence`. The two lower anchors are that line evaluated at
     * this app's floor and at [ACTIVE_CADENCE]: 1.40 and 2.11. Walking slowly costs far
     * less than walking is usually credited with, and the gap matters here because the
     * resting 1.0 is subtracted afterwards.
     *
     * Interpolating rather than banding means walking faster always earns more, and a
     * single step never moves the rate by more than a fraction.
     */
    private val MET_ANCHORS = listOf(
        MIN_MOVE_STEPS to 1.40,
        ACTIVE_CADENCE to 2.11,
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

    /** What one closed window earned. */
    data class WindowScore(
        val moveMinutes: Int,
        val heartPoints: Int,
        val kcal: Double,
        /** The pace the score was worked out at, for logging and tests. */
        val cadence: Int,
        /** True when the duration was inferred from the steps rather than timed. */
        val inferred: Boolean,
    )

    /**
     * Scores a closed window of [steps] that stayed open for [elapsedMs].
     *
     * Every figure scales with the minutes the window actually represents. Awarding one
     * move minute and at most two heart points per window, whatever its length, is what
     * made a frozen phone report a fraction of the walking it had counted: the steps came
     * back from the cumulative counter but the minutes they were worth did not.
     *
     * A window below [MIN_MOVE_STEPS] a minute earns nothing: not a minute, not a point,
     * not a calorie. Standing up to reach for something opens a window like any other step
     * does, and billing it is how a day of sitting still accumulates calories.
     *
     * Past [MAX_HUMAN_CADENCE] the elapsed time is not believable, so the duration is
     * inferred from the steps at [RECOVERY_CADENCE] instead. Those minutes and their
     * energy cost stand, but they earn no heart points: a heart point is a claim about
     * intensity, and intensity is exactly what was not observed.
     */
    fun scoreWindow(
        steps: Int,
        elapsedMs: Long,
        weightKg: Int,
        heightCm: Int = REFERENCE_HEIGHT_CM,
    ): WindowScore {
        if (steps <= 0 || elapsedMs <= 0) return WindowScore(0, 0, 0.0, 0, inferred = false)

        val elapsedMinutes = elapsedMs / 60_000.0
        val walkedMinutes = steps / RECOVERY_CADENCE.toDouble()

        val minutes = when {
            // Banked in one go by a service that had just restarted. The steps were taken
            // before this window opened, so its clock says nothing at all about them and
            // the walk they represent can be longer than the window was.
            steps / elapsedMinutes > MAX_HUMAN_CADENCE -> walkedMinutes

            // A window the tick could not close on time. These steps did happen inside it,
            // so the walking cannot have outlasted the window, but a short walk inside a
            // long freeze is exactly what this looks like: crediting the whole gap would
            // turn an hour of sitting still into an hour of movement.
            elapsedMinutes > LATE_TICK_MINUTES -> minOf(elapsedMinutes, walkedMinutes)

            else -> elapsedMinutes
        }
        val inferred = minutes != elapsedMinutes
        val cadence = (steps / minutes).toInt()

        // One floor for the whole window, not just for its minute. The MET curve bottoms
        // out at its slowest anchor and never falls below it, so a window holding a single
        // step used to be billed a full minute at that floor rate, the same as one holding
        // nine. Sixty such trips across a room added about 31 kcal to a day for walking
        // nowhere. Movement too slight to earn a move minute now earns nothing at all.
        val counts = isMoveMinute(cadence)

        return WindowScore(
            moveMinutes = if (counts) Math.round(minutes).toInt() else 0,
            heartPoints = if (inferred || !counts) {
                0
            } else {
                Math.round(heartPointsForMinute(cadence) * minutes).toInt()
            },
            kcal = if (counts) kcalForMinute(cadence, weightKg, heightCm) * minutes else 0.0,
            cadence = cadence,
            inferred = inferred,
        )
    }
}
