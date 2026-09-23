package app.bodyfit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bodyfit.data.ExerciseSession
import app.bodyfit.data.ExerciseType
import app.bodyfit.data.HealthRepository
import app.bodyfit.ui.components.BreathingDialog
import app.bodyfit.ui.components.SectionHeader
import app.bodyfit.ui.components.SettingsCard
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Everything the app can time, and what has been timed today.
 *
 * Box breathing is here with the rest but behaves differently: it is a guided minute that
 * records nothing, where the other three log a session against the day. The list says so
 * rather than leaving the difference to be discovered.
 */
@Composable
fun ExerciseScreen(
    sessions: List<ExerciseSession>,
    onStart: (ExerciseType) -> Unit,
    onStop: (ExerciseType, Long, Int) -> Unit,
    onDelete: (ExerciseSession) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var breathing by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<ExerciseType?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Exercise",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item {
            SettingsCard {
                ExerciseRow(
                    emoji = "🧘",
                    name = "Box breathing",
                    description = "Four seconds in, hold, out, hold. Nothing is recorded.",
                    onClick = { breathing = true },
                )
                ExerciseType.entries.forEach { type ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ExerciseRow(
                        emoji = type.emoji,
                        name = type.label,
                        description = type.description,
                        onClick = {
                            running = type
                            onStart(type)
                        },
                    )
                }
            }
        }

        item {
            Text(
                text = "A timed session adds its minutes, calories and heart points to the day. " +
                    "Effort is assumed, not measured, so the figures are estimates. While a " +
                    "session runs your steps are still counted but they stop earning " +
                    "separately, which is what keeps a run from being scored twice.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader(emoji = "📋", title = "Today's sessions") }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "Nothing logged today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        sessions.forEach { session ->
            item(key = session.id) {
                SessionRow(session = session, onDelete = { onDelete(session) })
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (breathing) {
        BreathingDialog(onDismiss = { breathing = false })
    }

    running?.let { type ->
        TimerDialog(
            type = type,
            onDone = { startedAt, seconds ->
                onStop(type, startedAt, seconds)
                running = null
            },
        )
    }
}

@Composable
private fun ExerciseRow(
    emoji: String,
    name: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One logged session, with the means to undo it. */
@Composable
private fun SessionRow(session: ExerciseSession, onDelete: () -> Unit) {
    val type = ExerciseType.from(session.type)
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = type?.emoji ?: "🏃", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(
                    text = "${type?.label ?: session.type} · ${clock(session.seconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${session.kcal.toInt()} kcal · ${session.heartPoints} heart points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove this session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The running clock for one exercise.
 *
 * Elapsed time is taken from the wall clock rather than counted up, so a tick the system
 * delays or drops does not lose time from the session.
 */
@Composable
private fun TimerDialog(type: ExerciseType, onDone: (Long, Int) -> Unit) {
    val startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(startedAt) {
        while (true) {
            delay(250)
            seconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("${type.emoji}  ${type.label}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clock(seconds),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (seconds < HealthRepository.MIN_SESSION_SECONDS) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Under ${HealthRepository.MIN_SESSION_SECONDS} seconds is " +
                            "discarded rather than logged.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDone(startedAt, seconds) }) { Text("Stop and save") }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDone(startedAt, 0) }) { Text("Discard") }
        },
    )
}

/** Seconds as m:ss, or h:mm:ss once an exercise runs past the hour. */
private fun clock(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }
}
