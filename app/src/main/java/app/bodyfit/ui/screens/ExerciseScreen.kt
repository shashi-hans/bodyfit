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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import android.Manifest
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import app.bodyfit.data.ExerciseSession
import app.bodyfit.data.ExerciseType
import app.bodyfit.data.HealthRepository
import app.bodyfit.sensor.ExerciseSessionService
import app.bodyfit.sensor.LiveSession
import app.bodyfit.sensor.Permissions
import app.bodyfit.ui.components.BreathingDialog
import app.bodyfit.ui.components.SectionHeader
import app.bodyfit.ui.components.SettingsCard
import app.bodyfit.ui.components.Wellness
import app.bodyfit.ui.components.WellnessNote
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
    onStop: (ExerciseType, Long, Int, Double?, Double) -> Unit,
    onDelete: (ExerciseSession) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var breathing by remember { mutableStateOf(false) }
    // The service owns the running session, so it outlives a rotation, a theme change and
    // the screen going off; this only reflects it.
    val live by ExerciseSessionService.state.collectAsState()
    var pendingLocation by rememberSaveable { mutableStateOf<ExerciseType?>(null) }

    // Asked here when missing: from Android 14 a session cannot go foreground without it
    // (or, for running and cycling, without location).
    var pendingActivity by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingActivity?.let { type ->
            if (ExerciseSessionService.canStart(context, type)) {
                ExerciseSessionService.start(context, type)
            } else {
                Toast.makeText(
                    context,
                    "Body Fit needs physical activity access to time a session.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            pendingActivity = null
        }
    }

    fun begin(type: ExerciseType) {
        if (ExerciseSessionService.canStart(context, type)) {
            ExerciseSessionService.start(context, type)
        } else {
            pendingActivity = type
            activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // Asked at the moment it is used rather than at launch, so the reason is on screen when
    // the prompt appears. A refusal starts the session anyway, on an assumed effort: the
    // session is the point, and the measurement is the improvement.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        pendingLocation?.let { type ->
            begin(type)
            pendingLocation = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(emoji = "🏋️", title = "Exercise") }

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
                            val wantsSpeed =
                                type == ExerciseType.RUNNING || type == ExerciseType.CYCLING
                            if (wantsSpeed &&
                                Permissions.hasGps(context) &&
                                !Permissions.hasLocation(context)
                            ) {
                                pendingLocation = type
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            } else {
                                begin(type)
                            }
                        },
                    )
                }
            }
        }

        item {
            WellnessNote(
                text = "A timed session adds its minutes, calories and heart points to the " +
                    "day. Effort is taken from published figures for the activity, or from " +
                    "your measured pace where GPS can supply one, so every total is an " +
                    "estimate. While a session runs your steps are still counted but they " +
                    "stop earning separately, which is what keeps a run from being scored " +
                    "twice. ${Wellness.SHORT}",
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

    live?.let { session ->
        TimerDialog(
            session = session,
            onStop = {
                val final = ExerciseSessionService.stop(context) ?: session
                onStop(final.type, final.startedAt, final.seconds, final.measuredMet, final.metres)
            },
            // Zero seconds is under the minimum, so the stop clears the session flag and
            // logs nothing.
            onDiscard = {
                ExerciseSessionService.stop(context)
                onStop(session.type, session.startedAt, 0, null, 0.0)
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
                // Absent rather than zero when nothing measured it: "0.00 km" beside a
                // skipping session would read as a failed measurement, not as no attempt.
                if (session.metres > 0.0) {
                    Text(
                        text = distanceLine(session.metres, session.seconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
 * How far the session went, and the pace that implies.
 *
 * Pace comes from the stored distance and moving time rather than being stored itself, so
 * the two can never disagree. Below a kilometre the figure is metres: "0.26 km" is harder
 * to read than "260 m" and pretends to a precision GPS does not have at that range.
 */
private fun distanceLine(metres: Double, seconds: Int): String {
    val distance = if (metres >= 1000.0) {
        String.format(Locale.getDefault(), "%.2f km", metres / 1000.0)
    } else {
        String.format(Locale.getDefault(), "%.0f m", metres)
    }
    if (seconds <= 0) return distance
    val kmh = metres / seconds * 3.6
    return "$distance · ${String.format(Locale.getDefault(), "%.1f", kmh)} km/h"
}

/**
 * The running clock for one exercise, drawn from [ExerciseSessionService].
 *
 * The service does the measuring, so the session keeps its clock, jump count and GPS
 * distance with the screen off or the activity recreated. This dialog only shows the
 * readings and ends the session.
 */
@Composable
private fun TimerDialog(session: LiveSession, onStop: () -> Unit, onDiscard: () -> Unit) {
    val type = session.type
    val wantsSpeed = type == ExerciseType.RUNNING || type == ExerciseType.CYCLING

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("${type.emoji}  ${type.label}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clock(session.seconds),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (session.moving) "Counting" else "Paused, no movement",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (wantsSpeed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            !session.measuringSpeed -> "Effort assumed"
                            !session.hasSpeed -> "Waiting for a GPS fix"
                            else -> String.format(
                                Locale.getDefault(),
                                "%.2f km at %.1f km/h",
                                session.metres / 1000.0,
                                session.kmh,
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (session.measuringSpeed) {
                            "Speed measured by GPS, also with the screen off. No location is stored."
                        } else {
                            "No GPS access, so the effort in the description is used."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (type == ExerciseType.SKIPPING) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${session.jumps} jumps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (session.jumps > 0) {
                            "${session.jumpRate.toInt()} a minute, measured"
                        } else {
                            "Counting jumps from the accelerometer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (session.seconds < HealthRepository.MIN_SESSION_SECONDS) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Under ${HealthRepository.MIN_SESSION_SECONDS} seconds of " +
                            "movement is discarded rather than logged.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onStop) { Text("Stop and save") } },
        dismissButton = { OutlinedButton(onClick = onDiscard) { Text("Discard") } },
    )
}

/** Seconds as m:ss, or h:mm:ss once an exercise runs past the hour. Shared with Today. */
internal fun clock(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }
}
