package app.bodyfit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.bodyfit.insights.Insights

/**
 * Colors for data marks: rings, bars and meters.
 *
 * These are separate from the Material roles on purpose. Material colors dress the
 * interface; these carry meaning, and they were picked against a rule: any hues that
 * appear on screen at the same time must stay apart for colorblind readers too.
 *
 * All six draw at once on the Today gauge, one arc each: [steps] yellow, [calories] red,
 * [heartPoints] green, then [moveMinutes], [distance] and [water].
 *
 * Yellow, red and green sit close together for colorblind readers, so the dark set is not
 * the same hues dimmed: the red is stepped to #CC4444 because the obvious #E66767 lands
 * only 13.0 apart from the dark yellow in normal vision, under the floor of 15.
 *
 * [distance] is violet and [moveMinutes] cyan for the same reason the other four are what
 * they are: the pink and green they replaced sat next to [calories] and [heartPoints] in the
 * figure grid and read as the same measurement twice. Violet and cyan are the two hues left
 * that are far from all four.
 *
 * Even so, six hues is past what colour alone can separate. Identity rests on the emoji and
 * figure under each arc, never on the hue, which pairs a figure to its arc and nothing more.
 *
 * Which metric wears which hue is a free choice: swapping them is a permutation of the
 * same set, so every pairwise separation is unchanged. Changing a hue value is not.
 */
@Immutable
data class VizColors(
    val steps: Color,
    val calories: Color,
    val water: Color,
    val distance: Color,
    val heartPoints: Color,
    val moveMinutes: Color,
    /**
     * The same six hues, darkened or lightened until they are safe as text.
     *
     * A colour that passes as a mark does not necessarily pass as a figure: a mark is read
     * by its shape and position, a number by the letterforms themselves. Measured against
     * the card, the mark colours give 1.90:1 for the yellow in light and 3.45:1 for the
     * green in dark, well under the 4.5:1 that body text needs. These clear 5:1 in both
     * themes, measured against surfaceContainer, which is the card the gauge sits on.
     */
    val stepsText: Color,
    val caloriesText: Color,
    val heartPointsText: Color,
    val waterText: Color,
    val distanceText: Color,
    val moveMinutesText: Color,
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
    steps = Color(0xFFEDA100),
    calories = Color(0xFFE34948),
    water = Color(0xFF2A78D6),
    distance = Color(0xFF7B3FE4),
    heartPoints = Color(0xFF008300),
    moveMinutes = Color(0xFF0E8CA8),
    stepsText = Color(0xFF8A5A00),
    caloriesText = Color(0xFFB3261E),
    heartPointsText = Color(0xFF006B00),
    waterText = Color(0xFF12558F),
    distanceText = Color(0xFF6A35C9),
    moveMinutesText = Color(0xFF0E6B80),
    track = Color(0xFFE7E5E0),
    grid = Color(0xFFEAE8E3),
    goalLine = Color(0xFF8E8C86),
    good = Color(0xFF0F7A55),
    warning = Color(0xFFB26A00),
    critical = Color(0xFFB3261E),
)

val DarkViz = VizColors(
    steps = Color(0xFFC98500),
    calories = Color(0xFFCC4444),
    water = Color(0xFF3987E5),
    distance = Color(0xFF9B78E8),
    heartPoints = Color(0xFF008300),
    moveMinutes = Color(0xFF17A8C4),
    stepsText = Color(0xFFE0A63C),
    caloriesText = Color(0xFFF08B88),
    heartPointsText = Color(0xFF4CC44C),
    waterText = Color(0xFF6FB2F5),
    distanceText = Color(0xFFB79BFA),
    moveMinutesText = Color(0xFF4CCFE3),
    track = Color(0xFF33332F),
    grid = Color(0xFF2E2E2B),
    goalLine = Color(0xFF7C7A73),
    good = Color(0xFF6FD9AE),
    warning = Color(0xFFE0A93C),
    critical = Color(0xFFF2B8B5),
)

val LocalViz = staticCompositionLocalOf { LightViz }

/**
 * The colour a rating wears, decided once.
 *
 * Three screens drew this mapping out by hand, which is three chances for one of them to
 * disagree with the others about what "warning" looks like. The word itself is always
 * printed beside it, so the colour is a second encoding and never the only one.
 */
fun Insights.Rating.color(viz: VizColors): Color = when (this) {
    Insights.Rating.GOOD -> viz.good
    Insights.Rating.WARNING -> viz.warning
    Insights.Rating.CRITICAL -> viz.critical
}
