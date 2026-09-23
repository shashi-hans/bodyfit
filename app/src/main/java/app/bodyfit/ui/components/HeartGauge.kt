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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz

/** One heart of the gauge: how full it is, and what to call it underneath. */
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
 * One heart per metric, each filling from the bottom as its goal is approached.
 *
 * A fill level is read at a glance; a position along a curve is not. Three nested outlines
 * shared one path, so 70% and 90% ended up looking alike and all three crowded together
 * where the shape narrows. One shape per metric removes both problems and lets each carry
 * its colour outright rather than as a thin line.
 *
 * The hues are the only set that stays distinguishable for colorblind readers in both
 * themes, and every heart is named underneath, so identity never rests on colour alone.
 */
@Composable
fun HeartGauge(arcs: List<GaugeArc>, modifier: Modifier = Modifier) {
    val trackColor = LocalViz.current.track
    val animated = arcs.map { arc ->
        animateFloatAsState(
            targetValue = arc.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700),
            label = "heart-${arc.label}",
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
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    val path = heartPath(size)
                    drawPath(path, trackColor)
                    // Clipping to the outline and filling a rectangle upward from the
                    // bottom is what makes the level follow the shape: the water line
                    // stays flat while the vessel around it narrows.
                    clipPath(path) {
                        val filled = size.height * animated[index].value
                        drawRect(
                            color = arc.color,
                            topLeft = Offset(0f, size.height - filled),
                            size = Size(size.width, filled),
                        )
                    }
                }
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
            }
        }
    }
}

/**
 * A heart with a rounded base rather than a point, filling [box].
 *
 * Built from cubics rather than the usual `x = 16 sin^3 t` parametric, which meets itself
 * at the bottom in a corner and cannot be rounded without cutting the shape about. Here
 * the two flanks stop short of the centre and a separate curve sweeps between them, so the
 * base is a smooth arc.
 *
 * Coordinates are a unit box, x and y in -1 to 1 with y upward, mapped onto [box] at the
 * end. That keeps the control points readable as proportions of the shape.
 */
private fun heartPath(box: Size): Path {
    fun px(x: Float) = (x + 1f) / 2f * box.width
    fun py(y: Float) = (1f - y) / 2f * box.height

    return Path().apply {
        // Bottom right, where the rounded base begins.
        moveTo(px(0.30f), py(-0.70f))
        // The base itself, sweeping left. Both control points sit below the ends, which is
        // what turns the meeting of the two flanks into an arc instead of a V.
        cubicTo(px(0.14f), py(-0.94f), px(-0.14f), py(-0.94f), px(-0.30f), py(-0.70f))
        // Up the left flank.
        cubicTo(px(-0.78f), py(-0.26f), px(-1.00f), py(0.08f), px(-1.00f), py(0.44f))
        // Over the left lobe and down into the dip between them.
        cubicTo(px(-1.00f), py(0.86f), px(-0.62f), py(1.00f), px(-0.32f), py(1.00f))
        cubicTo(px(-0.10f), py(1.00f), px(0.00f), py(0.82f), px(0.00f), py(0.50f))
        // Out of the dip and over the right lobe.
        cubicTo(px(0.00f), py(0.82f), px(0.10f), py(1.00f), px(0.32f), py(1.00f))
        cubicTo(px(0.62f), py(1.00f), px(1.00f), py(0.86f), px(1.00f), py(0.44f))
        // Down the right flank to where the base began.
        cubicTo(px(1.00f), py(0.08f), px(0.78f), py(-0.26f), px(0.30f), py(-0.70f))
        close()
    }
}
