package app.bodyfit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** One band of the gauge: how far round it has gone, and what to call it beside it. */
data class GaugeArc(
    val emoji: String,
    @DrawableRes val iconRes: Int? = null,
    /** Drawn in [color] where present, which an emoji cannot be. */
    val vector: ImageVector? = null,
    val label: String,
    val value: String,
    /** Printed under the value, in place of a percentage. */
    val goalLabel: String,
    val progress: Float,
    val color: Color,
)

/**
 * The day's figures on the left, a heart of three thin nested bands on the right.
 *
 * The heart is the classic parametric outline, `x = 16 sin^3 t` and
 * `y = 13 cos t - 5 cos 2t - 2 cos 3t - cos 4t`, sampled into a path and scaled to its
 * half of the row. Each band is that outline shrunk by one stroke plus a gap, so the three
 * stay evenly spaced whatever the size.
 *
 * Progress runs from the bottom point and up the right side, which is where the eye starts
 * on a heart. A band stops at a full lap when its goal is beaten; the surplus is reported
 * by the figures rather than by a second lap, so a band always means "how much of the
 * goal" and never an ambiguous overlap.
 *
 * Three bands is the cap. The hues are the only set that stays distinguishable for
 * colorblind readers in both themes, and every band is named in the list beside it, so
 * identity never rests on color alone.
 */
@Composable
fun HeartGauge(
    arcs: List<GaugeArc>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dp,
    arcGap: Dp = 6.dp,
) {
    val trackColor = LocalViz.current.track
    val animated = arcs.map { arc ->
        animateFloatAsState(
            targetValue = arc.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "band-${arc.label}",
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GaugeLegend(arcs = arcs, modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(HEART_ASPECT),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val gap = arcGap.toPx()
                // Fit whichever axis is tighter, so the shape keeps its proportions rather
                // than being stretched to the box.
                val outerScale = minOf(
                    (size.width - stroke) / HEART_WIDTH,
                    (size.height - stroke) / HEART_HEIGHT,
                )
                val middle = Offset(size.width / 2f, size.height / 2f)
                val measure = PathMeasure()

                arcs.forEachIndexed { index, arc ->
                    // Shrinking the scale by (stroke + gap) / half-width moves the outline
                    // in by exactly that much at its widest point.
                    val scale = outerScale - index * (stroke + gap) / (HEART_WIDTH / 2f)
                    if (scale <= 0f) return@forEachIndexed

                    val path = heartPath(middle, scale)
                    drawPath(
                        path = path,
                        color = trackColor,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )

                    val progress = animated[index].value
                    if (progress <= 0f) return@forEachIndexed
                    measure.setPath(path, false)
                    val drawn = Path()
                    measure.getSegment(0f, measure.length * progress, drawn, true)
                    drawPath(
                        path = drawn,
                        color = arc.color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

/**
 * One row per band: its icon in the band's own colour, then the value over its goal.
 *
 * The icon carries the hue, so the separate colour dot that used to sit beside it is gone:
 * two marks for one identity is one too many. Stacked rather than spread across the width,
 * because the list has half the row to live in and "Heart points" does not fit beside two
 * siblings in that space.
 */
@Composable
private fun GaugeLegend(arcs: List<GaugeArc>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        arcs.forEach { arc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(
                    emoji = arc.emoji,
                    iconRes = arc.iconRes,
                    vector = arc.vector,
                    size = 22.dp,
                    tint = arc.color,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = arc.value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "of ${arc.goalLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
