package app.bodyfit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors for data marks: rings, bars and meters.
 *
 * These are separate from the Material roles on purpose. Material colors dress the
 * interface; these carry meaning, and they were picked against a rule: any hues that
 * appear on screen at the same time must stay apart for colorblind readers too.
 *
 * Three metrics get a band on the Today heart: [steps] green, [calories] yellow and
 * [heartPoints] red, which puts the red on the heart's own metric. The remaining metrics, water included, are shown as neutral cards
 * there, and take their own hue only where they are the single colored thing on screen:
 * the water glass on its own tab, and the weekly chart, which draws one metric at a time.
 *
 * Yellow, red and green sit close together for colorblind readers, so the dark set is not
 * the same hues dimmed: the red is stepped to #CC4444 because the obvious #E66767 lands
 * only 13.0 apart from the dark yellow in normal vision, under the floor of 15. The set
 * that ships clears every gate in both modes, with colorblind separation in the 6-8 band
 * that is allowed only because the list beside the heart labels each band by name and
 * value.
 *
 * Which metric wears which of the three is a free choice: swapping them is a permutation
 * of the same set, so every pairwise separation is unchanged. Changing a hue value is not,
 * and needs scripts/validate_palette.js re-run first.
 */
@Immutable
data class VizColors(
    val steps: Color,
    val calories: Color,
    val water: Color,
    val distance: Color,
    val heartPoints: Color,
    val moveMinutes: Color,
    /** Unfilled part of a ring or meter. Recessive by design. */
    val track: Color,
    /** Chart baseline and gridlines. Recessive by design. */
    val grid: Color,
    /** Dashed goal reference line on the weekly chart. */
    val goalLine: Color,
    /**
     * Status colors, kept apart from the metric hues above and never reused as a fourth
     * series. They only ever appear next to the word they qualify, so the rating is never
     * carried by color alone.
     */
    val good: Color,
    val warning: Color,
    val critical: Color,
)

val LightViz = VizColors(
    steps = Color(0xFF008300),
    calories = Color(0xFFEDA100),
    water = Color(0xFF2A78D6),
    distance = Color(0xFFE87BA4),
    heartPoints = Color(0xFFE34948),
    moveMinutes = Color(0xFF1BAF7A),
    track = Color(0xFFE7E5E0),
    grid = Color(0xFFEAE8E3),
    goalLine = Color(0xFF8E8C86),
    good = Color(0xFF0F7A55),
    warning = Color(0xFFB26A00),
    critical = Color(0xFFB3261E),
)

val DarkViz = VizColors(
    steps = Color(0xFF008300),
    calories = Color(0xFFC98500),
    water = Color(0xFF3987E5),
    distance = Color(0xFFD55181),
    heartPoints = Color(0xFFCC4444),
    moveMinutes = Color(0xFF199E70),
    track = Color(0xFF33332F),
    grid = Color(0xFF2E2E2B),
    goalLine = Color(0xFF7C7A73),
    good = Color(0xFF6FD9AE),
    warning = Color(0xFFE0A93C),
    critical = Color(0xFFF2B8B5),
)

val LocalViz = staticCompositionLocalOf { LightViz }
