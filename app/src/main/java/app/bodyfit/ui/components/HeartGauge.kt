package app.bodyfit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** One heart of the gauge: how full it is, and what to call it underneath. */
data class GaugeArc(
    val emoji: String,
    @DrawableRes val iconRes: Int? = null,
    /** Drawn in [color] where present, which an emoji cannot be. */
    val vector: ImageVector? = null,
    /**
     * The emblem that fills to show progress. Null falls back to the heart, which is drawn
     * from a path rather than an asset so it can carry an outline.
     */
    val shape: ImageVector? = null,
    val label: String,
    val value: String,
    /** Printed under the value, in place of a percentage. */
    val goalLabel: String,
    val progress: Float,
    val color: Color,
)

/**
 * One heart per metric, each filling from the bottom as its goal is approached.
 *
 * A fill level is read at a glance; a position along a curve is not. Three nested outlines
 * shared one path, so 70% and 90% ended up looking alike and all three crowded together
 * where the shape narrows to its point. One shape per metric removes both problems and
 * lets each carry its colour outright rather than as a thin line.
 *
 * The hues are the only set that stays distinguishable for colorblind readers in both
 * themes, and every heart is named underneath with its goal, so identity never rests on
 * colour alone and the fill level always has a scale to be read against.
 */
@Composable
fun HeartGauge(
    arcs: List<GaugeArc>,
    modifier: Modifier = Modifier,
    outlineWidth: Dp = 3.dp,
) {
    val trackColor = LocalViz.current.track
    val animated = arcs.map { arc ->
        animateFloatAsState(
            targetValue = arc.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "shape-${arc.label}",
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        arcs.forEachIndexed { index, arc ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FillingShape(
                    arc = arc,
                    progress = animated[index].value,
                    trackColor = trackColor,
                    outlineWidth = outlineWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = arc.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = arc.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "of ${arc.goalLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One metric's emblem, filling from the bottom.
 *
 * The heart is drawn from its own path so it can be stroked as an outline. The others are
 * vector assets, which are filled by painting the same icon twice, once dim and once in
 * the metric's colour clipped to the level. That way any icon can become a gauge without
 * first being rewritten as a path.
 */
@Composable
private fun FillingShape(
    arc: GaugeArc,
    progress: Float,
    trackColor: Color,
    outlineWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val painter = arc.shape?.let { rememberVectorPainter(it) }

    Canvas(
        modifier = modifier.aspectRatio(if (painter == null) HEART_ASPECT else 1f),
    ) {
        if (painter == null) {
            val outline = outlineWidth.toPx()
            // The stroke straddles the path, so the shape is inset by half of it.
            val scale = minOf(
                (size.width - outline) / HEART_WIDTH,
                (size.height - outline) / HEART_HEIGHT,
            )
            val path = heartPath(Offset(size.width / 2f, size.height / 2f), scale)
            drawPath(path, trackColor)
            clipPath(path) {
                val filled = size.height * progress
                drawRect(
                    color = arc.color,
                    topLeft = Offset(0f, size.height - filled),
                    size = Size(size.width, filled),
                )
            }
            drawPath(
                path = path,
                color = arc.color,
                style = Stroke(width = outline, join = StrokeJoin.Round),
            )
            return@Canvas
        }

        with(painter) {
            draw(size, colorFilter = ColorFilter.tint(trackColor))
        }
        // Clipping the canvas rather than the icon is what makes the level cut straight
        // across whatever the shape is, the way a liquid would sit in it.
        clipRect(top = size.height * (1f - progress)) {
            with(painter) {
                draw(size, colorFilter = ColorFilter.tint(arc.color))
            }
        }
    }
}

/** Samples enough to hide the straight segments at this size. */
private const val HEART_SAMPLES = 240

/**
 * The outline sampled once in its own units, starting at the bottom point and running up
 * the right side.
 *
 * Sampled rather than described by constants because the curve's extent is not obvious:
 * the lobes peak near y = 11.9, not at the y = 5 the formula reaches at t = 0, and a
 * height guessed from that endpoint clips them.
 */
private val HEART_POINTS: List<Offset> = List(HEART_SAMPLES + 1) { i ->
    val t = PI - (i.toDouble() / HEART_SAMPLES) * 2 * PI
    Offset(
        (16.0 * sin(t).pow(3)).toFloat(),
        (13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)).toFloat(),
    )
}

private val HEART_WIDTH = HEART_POINTS.maxOf { it.x } - HEART_POINTS.minOf { it.x }
private val HEART_HEIGHT = HEART_POINTS.maxOf { it.y } - HEART_POINTS.minOf { it.y }
private val HEART_MID_X = (HEART_POINTS.maxOf { it.x } + HEART_POINTS.minOf { it.x }) / 2f
private val HEART_MID_Y = (HEART_POINTS.maxOf { it.y } + HEART_POINTS.minOf { it.y }) / 2f

/** About 1.11: a shade wider than tall. */
private val HEART_ASPECT = HEART_WIDTH / HEART_HEIGHT

/** The outline centred on [middle] at [scale] pixels per unit. */
private fun heartPath(middle: Offset, scale: Float): Path {
    val path = Path()
    HEART_POINTS.forEachIndexed { i, point ->
        val px = middle.x + (point.x - HEART_MID_X) * scale
        // Screen y grows downward, so the shape is flipped as it is placed.
        val py = middle.y - (point.y - HEART_MID_Y) * scale
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}
