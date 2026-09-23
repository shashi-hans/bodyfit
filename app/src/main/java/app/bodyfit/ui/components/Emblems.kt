package app.bodyfit.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The shapes the Today screen fills to show progress.
 *
 * Each is a path rather than a rendered vector, because the screen strokes them as
 * outlines as well as filling them, and a stroke needs geometry. The two icon-derived ones
 * carry the same path data as their drawables, so the emblem on the card and the icon
 * everywhere else are the same shape.
 */
enum class Emblem {
    STEPS,
    CALORIES,
    HEART_POINTS,
}

/**
 * Path data lifted from the drawables of the same name, in their 24x24 viewport.
 *
 * Duplicated from the XML rather than parsed out of it at runtime: an `AnimatedVectorDrawable`
 * cannot be read back as geometry, and a parser for the resource format would be far more
 * code than the two strings it would save. The comment in each drawable says the same.
 */
private const val VIEWPORT = 24f

private val FOOTSTEPS_DATA = listOf(
    "M9.24951 11.5487c0.432735 2.87934 -0.36004 4.45359 -2.59887 4.78465c-2.48786 0.367899 " +
        "-3.51297 -1.2987 -3.9457 -4.17803c-0.582056 -3.87448 0.785899 -6.28424 2.7364 -6.43946" +
        "C7.21649 5.57491 8.81629 8.66988 9.24951 11.5487",
    "M10.4112 19.5398c0.153741 1.63467 -0.729903 3.1603 -2.06298 3.42161c-1.34585 0.263276 " +
        "-2.88376 -0.804073 -3.19713 -2.41615s0.871856 -1.69754 2.3243 -1.97506s2.75015 " +
        "-1.00202 2.93582 0.969603",
    "M14.7508 6.83284c-0.432735 2.87934 0.36004 4.45359 2.59838 4.78465c2.48835 0.367899 " +
        "3.51297 -1.2987 3.9457 -4.17803c0.582547 -3.87497 -0.785899 -6.298 -2.73591 -6.43946" +
        "c-1.78792 -0.129673 -3.37494 2.95351 -3.80817 5.83284",
    "M13.5891 14.8244c-0.153741 1.63418 0.729903 3.1603 2.06298 3.42161c1.34585 0.263276 " +
        "2.88376 -0.804073 3.19713 -2.41615s-0.871856 -1.69754 -2.3243 -1.97555s-2.75015 " +
        "-1.00202 -2.93582 0.970094",
)

private val FLAME_DATA = listOf(
    "M12 2.2C14.2 5.6 16.8 8.6 16.8 13C16.8 17.6 14.6 21.3 12 21.3C9.4 21.3 7.2 17.6 7.2 13" +
        "C7.2 10.4 8.4 8.4 9.8 6.6C9.8 8.8 10.4 10.4 11.4 11.4C11.9 9 11.6 5.4 12 2.2Z",
)

/** Parsed once: the parser is not cheap and the shapes never change. */
private val PARSED: Map<Emblem, Path> = mapOf(
    Emblem.STEPS to combine(FOOTSTEPS_DATA),
    Emblem.CALORIES to combine(FLAME_DATA),
)

private fun combine(data: List<String>): Path {
    val path = Path()
    data.forEach { path.addPath(PathParser().parsePathString(it).toPath()) }
    return path
}

/**
 * [emblem] scaled to fit [box], inset by [inset] so a stroke centred on the path is not
 * clipped by the canvas edge.
 *
 * Returns null for the heart, which is generated rather than parsed and has its own
 * builder.
 */
fun emblemPath(emblem: Emblem, box: Size, inset: Float): Path? {
    val source = PARSED[emblem] ?: return null
    val scale = minOf((box.width - inset) / VIEWPORT, (box.height - inset) / VIEWPORT)
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            (box.width - VIEWPORT * scale) / 2f,
            (box.height - VIEWPORT * scale) / 2f,
        )
    }
    // Copied before transforming: the parsed path is shared and must stay in its own units.
    val out = Path().apply { addPath(source) }
    out.asAndroidPath().transform(matrix)
    return out
}
