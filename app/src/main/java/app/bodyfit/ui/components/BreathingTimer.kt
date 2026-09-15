package app.bodyfit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.theme.LocalViz
import kotlinx.coroutines.delay

/** The four equal phases of box breathing, in order. */
private enum class Phase(val label: String) {
    INHALE("Breathe in"),
    HOLD_IN("Hold"),
    EXHALE("Breathe out"),
    HOLD_OUT("Hold"),
}

/**
 * A box-breathing timer: four seconds in, hold, out, hold, repeating.
 *
 * Nothing is recorded. The app does not store mindful minutes, and inventing a counter that
 * nothing reads would be worse than leaving the session unlogged. It is a tool, not a
 * metric.
 */
@Composable
fun BreathingDialog(onDismiss: () -> Unit, secondsPerPhase: Int = 4) {
    var phaseIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(secondsPerPhase) }
    var cycles by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val phase = Phase.entries[phaseIndex]

    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            if (secondsLeft > 1) {
                secondsLeft--
            } else {
                secondsLeft = secondsPerPhase
                phaseIndex = (phaseIndex + 1) % Phase.entries.size
                if (phaseIndex == 0) cycles++
            }
        }
    }

    // The circle grows on the in-breath, holds, shrinks on the out-breath, holds. The
    // animation runs for the whole phase so the eye can pace the breath by it.
    val target = when (phase) {
        Phase.INHALE -> 1f
        Phase.HOLD_IN -> 1f
        Phase.EXHALE -> 0.45f
        Phase.HOLD_OUT -> 0.45f
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (phase == Phase.INHALE || phase == Phase.EXHALE) secondsPerPhase * 1000 else 200),
        label = "breath",
    )
    val accent = LocalViz.current.water

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { running = !running }) { Text(if (running) "Pause" else "Resume") }
        },
        title = { Text("🧘 Box breathing") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val radius = size.minDimension / 2f * scale
                        drawCircle(
                            color = accent.copy(alpha = 0.18f),
                            radius = radius,
                        )
                        drawCircle(
                            color = accent,
                            radius = radius,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = phase.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$secondsLeft",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (cycles == 0) {
                        "$secondsPerPhase seconds each: in, hold, out, hold"
                    } else {
                        "$cycles round${if (cycles == 1) "" else "s"} done"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

/** The entry point on the Today screen. */
@Composable
fun BreathingCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🧘 Take a minute",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Box breathing: four seconds in, hold, out, hold. Nothing is recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStart) { Text("Start") }
        }
    }
}
