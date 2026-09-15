package app.bodyfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz
import kotlin.math.max

/**
 * One metric across a row of equal slots: days of a week, or hours of a day.
 *
 * One metric is drawn at a time, so the chart is single-series: no legend, the heading
 * above it names the measure. Values are not printed on every bar; only the slots worth
 * pointing at get a label, and tapping a bar labels that one instead. An empty slot keeps
 * a short track-colored stub so the range reads as seven days, not five.
 *
 * Pass [todayIndex] as -1 where no slot is the current one, and [goal] as 0 to leave out
 * the goal line.
 */
@Composable
fun WeeklyBarChart(
    values: List<Double>,
    labels: List<String>,
    todayIndex: Int,
    goal: Double,
    barColor: Color,
    formatValue: (Double) -> String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 196.dp,
    /** Draw every nth axis label. Thirty labels under thirty bars is unreadable. */
    labelEvery: Int = 1,
) {
    val viz = LocalViz.current
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val todayAxisStyle = axisStyle.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
    )
    val valueStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val gridColor = viz.grid
    val trackColor = viz.track
    val goalLineColor = viz.goalLine

    // Labelled days: the tapped bar wins; otherwise today plus the best day of the week.
    val bestIndex = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0.0 }
    val todayHasValue = (values.getOrNull(todayIndex) ?: 0.0) > 0.0
    val labelled: Set<Int> = if (selectedIndex != null) {
        setOf(selectedIndex)
    } else {
        setOfNotNull(todayIndex.takeIf { todayHasValue }, bestIndex)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values.size, selectedIndex) {
                detectTapGestures { offset ->
                    if (values.isEmpty()) return@detectTapGestures
                    val slot = size.width.toFloat() / values.size
                    val index = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                    onSelect(if (index == selectedIndex) null else index)
                }
            }
    ) {
        if (values.isEmpty()) return@Canvas

        val topPadding = 22.dp.toPx()
        val axisHeight = 20.dp.toPx()
        val plotTop = topPadding
        val plotBottom = size.height - axisHeight
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        // Headroom above the tallest mark so a labelled bar never collides with the top.
        val ceiling = max(values.maxOrNull() ?: 0.0, goal).coerceAtLeast(1.0) * 1.12
        fun yFor(value: Double): Float = plotBottom - (value / ceiling * plotHeight).toFloat()

        // Baseline, kept recessive.
        drawLine(
            color = gridColor,
            start = Offset(0f, plotBottom),
            end = Offset(size.width, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )

        if (goal > 0) {
            val goalY = yFor(goal)
            if (goalY > plotTop) {
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
        }

        val slot = size.width / values.size
        // The gap has to shrink with the slot. A fixed 8dp is most of the width once the
        // window reaches thirty days, which leaves hairlines instead of bars.
        val gap = minOf(8.dp.toPx(), slot * 0.3f)
        val barWidth = (slot - gap).coerceAtLeast(2.dp.toPx())
        val corner = 4.dp.toPx()
        val stub = 3.dp.toPx()

        values.forEachIndexed { index, value ->
            val left = index * slot + (slot - barWidth) / 2f
            val top = if (value > 0) yFor(value).coerceAtMost(plotBottom - stub) else plotBottom - stub
            drawTopRoundedBar(
                left = left,
                top = top,
                right = left + barWidth,
                bottom = plotBottom,
                corner = corner,
                color = if (value > 0) barColor else trackColor,
            )

            if (index in labelled && value > 0) {
                val layout = measurer.measure(formatValue(value), valueStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (left + barWidth / 2f - layout.size.width / 2f)
                            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                        y = (top - layout.size.height - 4.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }

            // Always label today and the first bar, then every nth in between.
            val showLabel = labelEvery <= 1 ||
                index == todayIndex || index == 0 || (values.lastIndex - index) % labelEvery == 0
            if (!showLabel) return@forEachIndexed

            val dayLayout = measurer.measure(
                labels.getOrElse(index) { "" },
                if (index == todayIndex) todayAxisStyle else axisStyle,
            )
            drawText(
                textLayoutResult = dayLayout,
                topLeft = Offset(
                    x = left + barWidth / 2f - dayLayout.size.width / 2f,
                    y = plotBottom + 4.dp.toPx(),
                ),
            )
        }
    }
}

private fun DrawScope.drawTopRoundedBar(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    corner: Float,
    color: Color,
) {
    val radius = corner.coerceAtMost((bottom - top).coerceAtLeast(0f))
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, right, bottom),
                topLeft = CornerRadius(radius, radius),
                topRight = CornerRadius(radius, radius),
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, color)
}
