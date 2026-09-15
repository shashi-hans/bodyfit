package app.bodyfit.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz

/** One ring of the gauge: how far round it has gone, and what to call it underneath. */
data class GaugeArc(
    val emoji: String,
    @DrawableRes val iconRes: Int? = null,
    val label: String,
    val value: String,
    /** Printed under the value, in place of a percentage. */
    val goalLabel: String,
    val progress: Float,
    val color: Color,
)

/**
 * A half-circle gauge with one arc per metric, nested outermost first.
 *
 * The sweep is a flat 180 degrees, so the ends sit level and the numbers get a straight
 * base to sit on. Each arc stops at a full half circle when its goal is beaten; the
 * surplus is reported by the percentage in the legend rather than by a second lap, so an
 * arc always means "how much of the goal" and never an ambiguous overlap.
 *
 * Three arcs is the cap. The hues are the only set that stays distinguishable for
 * colorblind readers in both themes, and the legend under the gauge names each one, so
 * identity never rests on color alone.
 */
@Composable
fun HalfCircleGauge(
    arcs: List<GaugeArc>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 15.dp,
    arcGap: Dp = 7.dp,
    center: @Composable ColumnScope.() -> Unit,
) {
    val trackColor = LocalViz.current.track
    val animated = arcs.map { arc ->
        animateFloatAsState(
            targetValue = arc.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "arc-${arc.label}",
        )
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .aspectRatio(2f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val gap = arcGap.toPx()
                // The flat base sits on the bottom edge, half a stroke up so the round
                // caps are not clipped.
                val baseY = size.height - stroke / 2f
                val outerRadius = (size.width - stroke) / 2f

                arcs.forEachIndexed { index, arc ->
                    val radius = outerRadius - index * (stroke + gap)
                    if (radius <= stroke) return@forEachIndexed
                    val topLeft = Offset(size.width / 2f - radius, baseY - radius)
                    val arcSize = Size(radius * 2f, radius * 2f)

                    drawArc(
                        color = trackColor,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    val progress = animated[index].value
                    // Below about half a degree a round cap draws as a lone dot on the
                    // track, which reads as a marker rather than as "almost nothing".
                    if (progress * 180f >= 0.5f) {
                        drawArc(
                            color = arc.color,
                            startAngle = 180f,
                            sweepAngle = 180f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = center,
            )
        }

        Spacer(Modifier.height(16.dp))
        GaugeLegend(arcs = arcs)
    }
}

/**
 * Names every arc with its own color dot, emoji, value and goal.
 *
 * Values wear text ink; only the dot carries the hue, so the numbers stay readable
 * against either surface.
 */
@Composable
private fun GaugeLegend(arcs: List<GaugeArc>, modifier: Modifier = Modifier) {
    // Equal-width columns, so a long label like "Heart points" cannot squeeze its
    // neighbours or run into the card edge.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        arcs.forEach { arc ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(arc.color, CircleShape)
                    )
                    Spacer(Modifier.width(3.dp))
                    Glyph(emoji = arc.emoji, iconRes = arc.iconRes, size = 13.dp)
                }
                Text(
                    text = arc.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = arc.value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = "Goal: ${arc.goalLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
