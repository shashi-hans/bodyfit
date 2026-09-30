package app.bodyfit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
     * The whole line printed under the value, in place of a percentage.
     *
     * It carries the unit, which is why [value] does not: at this size "3,768 steps" costs
     * the width a second figure needs, and printing the unit twice buys nothing. Written
     * out rather than assembled here, because not every figure is measured against a goal.
     */
    val caption: String,
    val progress: Float,
    /**
     * Whether this metric gets a band of its own.
     *
     * A figure can be worth printing without being worth an arc. Distance is steps counted
     * a second way and move minutes track the same walking, so an arc for either would draw
     * a curve the steps arc has already drawn.
     */
    val hasArc: Boolean = true,
    /** The arc's hue. Validated as a mark, not as text. */
    val color: Color,
    /** The same hue made safe to print a figure in. */
    val textColor: Color = color,
    /**
     * Opens an explanation of this figure, from an icon beside it.
     *
     * For a number whose name does not say what it counts. Most do, and carry none.
     */
    val onInfo: (() -> Unit)? = null,
    /** Opens this metric's detail, from a tap anywhere on its figure. */
    val onOpen: (() -> Unit)? = null,
)

/**
 * The reading that sits in the well the arcs enclose.
 *
 * That space is the largest clear area on the card, so it holds the one number worth
 * reading before any other. It is deliberately not one of the figures below: a number
 * printed twice on one card reads as two different measurements.
 */
data class GaugeCenter(
    val value: String,
    val caption: String,
    val color: Color,
    /** Opens an explanation of this reading, from a round button under it. */
    val onInfo: (() -> Unit)? = null,
    /** Opens this reading's detail, from a tap on the number or its caption. */
    val onOpen: (() -> Unit)? = null,
)

/**
 * Nested half-circle arcs across the top, the day's figures underneath.
 *
 * An arc's length is uniform along its sweep, so a given share of a goal is always the same
 * run of ink and six metrics are honestly comparable by eye. The shapes tried before this,
 * a heart traced by a band and then emblems filling from the bottom, could not manage that:
 * a heart's perimeter is not uniform and its area does not grow evenly with height, so the
 * same percentage looked different on each metric.
 *
 * Half a circle rather than a whole one so the arcs occupy a band the width of the card
 * instead of a disc beside the figures, which leaves the figures the full width and room
 * to stay large at six of them.
 *
 * Every metric gets a figure; only those marked [GaugeArc.hasArc] get a band. More figures
 * than arcs on purpose: a number can be worth reading without being worth a second curve
 * that tracks one already drawn.
 *
 * Several hues at once is past what colour alone can separate for a colorblind reader.
 * Identity therefore rests on the emoji and the figure beneath each one; the hue pairs a
 * figure to its arc and is never the only thing saying which metric this is.
 */
@Composable
fun TodayGauge(
    arcs: List<GaugeArc>,
    /** The one reading printed in the well the arcs enclose. Omitted leaves it empty. */
    center: GaugeCenter? = null,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 8.dp,
    ringGap: Dp = 6.dp,
    /** Figures per row underneath. Three keeps the block about as tall as the arcs. */
    columns: Int = 3,
) {
    val trackColor = LocalViz.current.track
    val animated = arcs.map { arc ->
        animateFloatAsState(
            targetValue = arc.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "arc-${arc.label}",
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Two to one: a half circle is exactly half as tall as it is wide.
                .aspectRatio(2f),
        ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = ringWidth.toPx()
            val gap = ringGap.toPx()
            // The flat side sits on the bottom edge. Half a stroke up from it leaves the
            // round caps at each end room to draw instead of being clipped away.
            val center = Offset(size.width / 2f, size.height - stroke / 2f)
            val outerRadius = size.width / 2f - stroke / 2f

            arcs.withIndex().filter { it.value.hasArc }.forEachIndexed { ring, (index, arc) ->
                val radius = outerRadius - ring * (stroke + gap)
                // Not merely positive: an arc narrower than its own stroke draws as a smear
                // at the centre, which reads as a mark rather than as another metric.
                if (radius <= stroke) return@forEachIndexed

                val topLeft = Offset(center.x - radius, center.y - radius)
                val box = Size(radius * 2, radius * 2)
                drawArc(
                    color = trackColor,
                    // Nine o'clock, sweeping clockwise over the top to three o'clock.
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )

                val progress = animated[index].value
                // Below about half a degree a round cap draws as a lone dot on the track,
                // which reads as a marker rather than as "almost nothing".
                if (progress * 180f < 0.5f) return@forEachIndexed
                drawArc(
                    color = arc.color,
                    startAngle = 180f,
                    sweepAngle = 180f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

            if (center != null) {
                Column(
                    // The well is the half disc under the arcs, so its floor is the bottom
                    // edge of the canvas and the reading sits on that rather than centred
                    // in a box whose top half is arc.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .then(center.onOpen?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Above the reading, not under it: the well is read top down, and a
                    // button under the caption sat closer to the figures below than to the
                    // number it belongs to.
                    if (center.onInfo != null) {
                        InfoButton(
                            onClick = center.onInfo,
                            description = "Where today's ${center.caption} came from",
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = center.value,
                        style = MaterialTheme.typography.displaySmall,
                        color = center.color,
                        maxLines = 1,
                    )
                    Text(
                        text = center.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // A grid rather than one column: six figures stacked would run past the fold, and
        // the arcs they belong to are already read left to right.
        arcs.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { column, arc ->
                    // The outer columns sit under the ends of the arc above rather than
                    // floating inside their own third of the row, so the block reads as
                    // one shape with the arcs instead of two stacked grids.
                    Figure(
                        arc = arc,
                        alignment = when (column) {
                            0 -> Alignment.Start
                            columns - 1 -> Alignment.End
                            else -> Alignment.CenterHorizontally
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short last row aligned with the one above rather than spread.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** One metric's mark, figure and goal, in its own colour. */
@Composable
private fun Figure(
    arc: GaugeArc,
    alignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.then(arc.onOpen?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        horizontalAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(
                emoji = arc.emoji,
                iconRes = arc.iconRes,
                vector = arc.vector,
                size = 18.dp,
                tint = arc.color,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = arc.value,
                style = MaterialTheme.typography.titleLarge,
                // The figure wears its arc's colour, so the eye can pair the two without
                // counting inwards from the outside. A text-safe shade of it: the mark
                // colours are too faint to read as a number, worst at 1.90:1 for a yellow
                // on a light card.
                color = arc.textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The icon rides with the caption, not the figure. A third of a phone's width has
        // no room for both a five-digit number and an icon, and the figure is what the row
        // is for.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = arc.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (arc.onInfo != null) {
                InfoButton(onClick = arc.onInfo, description = "What ${arc.label} counts")
            }
        }
    }
}

/**
 * A round button that opens what a figure is made of.
 *
 * Filled and circular rather than a bare glyph: a plain icon beside a number reads as part
 * of the label, and a figure that can be opened has to look different from one that cannot.
 * The ring of padding outside the circle is the touch target, which is why the circle itself
 * can stay small enough to sit on one line with the caption.
 */
@Composable
private fun InfoButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // Vertical only. Side padding is what the touch target usually buys, but here
            // it reads as a gap: on the leading side between the label and its own button,
            // and on the trailing side as the circle failing to reach the edge the figure
            // above it sits on. The row's own height carries the target instead.
            .padding(vertical = 10.dp)
            .size(18.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
    }
}
