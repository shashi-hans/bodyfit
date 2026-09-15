package app.bodyfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz

/** A slim progress bar: a recessive track with the achieved share drawn over it. */
@Composable
fun ProgressMeter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 8.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "meter-progress",
    )
    val trackColor = LocalViz.current.track

    Canvas(modifier = modifier.height(thickness)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius),
        )
        if (animated > 0f) {
            drawRoundRect(
                color = color,
                size = Size(width = size.width * animated, height = size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}
