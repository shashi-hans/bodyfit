package app.bodyfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz

/**
 * A glass that fills as the day's water is logged.
 *
 * The percentage is written across the middle, so the reading never depends on judging
 * a fill height by eye.
 */
@Composable
fun WaterGlass(
    progress: Float,
    modifier: Modifier = Modifier,
    width: Dp = 116.dp,
    height: Dp = 168.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 700),
        label = "water-fill",
    )
    val viz = LocalViz.current
    val outline = MaterialTheme.colorScheme.outlineVariant
    val fill = viz.water

    Box(modifier = modifier.size(width = width, height = height), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val radius = 22.dp.toPx()
            val body = Rect(
                left = strokeWidth,
                top = strokeWidth,
                right = size.width - strokeWidth,
                bottom = size.height - strokeWidth,
            )
            val glass = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = body,
                        topLeft = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        topRight = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        bottomLeft = CornerRadius(radius, radius),
                        bottomRight = CornerRadius(radius, radius),
                    )
                )
            }

            clipPath(glass) {
                val fillHeight = body.height * animated
                drawRect(
                    color = fill,
                    topLeft = Offset(body.left, body.bottom - fillHeight),
                    size = Size(body.width, fillHeight),
                )
            }
            drawPath(glass, outline, style = Stroke(width = strokeWidth))
        }
        Text(
            text = "${(clamped * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            color = if (animated > 0.55f) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
