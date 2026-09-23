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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz

/** One band of the gauge: how far round it has gone, and what to call it beside it. */
data class GaugeArc(
    val emoji: String,
    @DrawableRes val iconRes: Int? = null,
    /** Drawn in [color] where present, which an emoji cannot be. */
    val vector: ImageVector? = null,
    val label: String,
    val value: String,
    /**
     * Printed under the value, in place of a percentage.
     *
     * It carries the unit, which is why [value] does not: at this size "3,768 steps" costs
     * the width three rings need, and printing the unit twice buys nothing.
     */
    val goalLabel: String,
    val progress: Float,
    /** The ring's hue. Validated as a mark, not as text. */
    val color: Color,
    /** The same hue made safe to print a figure in. */
    val textColor: Color = color,
)

/**
 * The day's figures on the left, three nested rings on the right.
 *
 * A circle's perimeter is uniform, so a given share of the goal is always the same length
 * of arc and the three rings are honestly comparable by eye. The shapes tried before this,
 * a heart traced by a band and then emblems filling from the bottom, could not manage that:
 * a heart's perimeter is not uniform and its area does not grow evenly with height, so the
 * same percentage looked different on each metric. The emblem stays, beside the figure it
 * belongs to, where it carries identity without also being asked to carry measurement.
 *
 * Three rings is the cap. The hues are the only set that stays distinguishable for
 * colorblind readers in both themes, and every ring is named beside it, so identity never
 * rests on colour alone.
 */
@Composable
fun TodayGauge(
    arcs: List<GaugeArc>,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 8.dp,
    ringGap: Dp = 7.dp,
) {
    val trackColor = LocalViz.current.track
    val animated = arcs.map { arc ->
        animateFloatAsState(
            targetValue = arc.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "ring-${arc.label}",
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            // The figures are the point and they set the type size, so they take the
            // larger share. The rings stay readable well below half the row.
            modifier = Modifier.weight(1.5f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            arcs.forEach { arc ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(
                        emoji = arc.emoji,
                        iconRes = arc.iconRes,
                        vector = arc.vector,
                        size = 26.dp,
                        tint = arc.color,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = arc.value,
                            style = MaterialTheme.typography.headlineMedium,
                            // The figure wears its ring's colour, so the eye can pair the
                            // two without counting inwards from the outside. A text-safe
                            // shade of it: the mark colours are too faint to read as a
                            // number, worst at 1.90:1 for calories on a light card.
                            color = arc.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "of ${arc.goalLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
        ) {
            val stroke = ringWidth.toPx()
            val gap = ringGap.toPx()
            arcs.forEachIndexed { index, arc ->
                // Half a stroke keeps the outermost ring inside the canvas; the rest is
                // one ring plus a gap per step inwards.
                val inset = stroke / 2f + index * (stroke + gap)
                val diameter = minOf(size.width, size.height) - inset * 2
                // Not merely positive: a ring narrower than its own stroke draws as a blob
                // at the centre, which reads as a mark rather than as a third metric.
                if (diameter <= stroke * 2) return@forEachIndexed

                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val ring = Size(diameter, diameter)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ring,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )

                val progress = animated[index].value
                // Below about half a degree a round cap draws as a lone dot on the track,
                // which reads as a marker rather than as "almost nothing".
                if (progress * 360f < 0.5f) return@forEachIndexed
                drawArc(
                    color = arc.color,
                    // From twelve o'clock, which is where a ring is read from.
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ring,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
