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
import android.location.LocationListener
import android.location.LocationManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.bodyfit.sensor.Permissions
import app.bodyfit.sensor.SessionMonitor
import app.bodyfit.sensor.SpeedMonitor
import app.bodyfit.sensor.cyclingMet
import app.bodyfit.sensor.runningMet
import app.bodyfit.sensor.skippingMet
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
    onStop: (ExerciseType, Long, Int, Double?) -> Unit,
    onDelete: (ExerciseSession) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var breathing by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<ExerciseType?>(null) }
    var pendingLocation by remember { mutableStateOf<ExerciseType?>(null) }

    // Asked at the moment it is used rather than at launch, so the reason is on screen when
    // the prompt appears. A refusal starts the session anyway, on an assumed effort: the
    // session is the point, and the measurement is the improvement.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingLocation?.let { type ->
            running = type
            onStart(type)
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
                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                running = type
                                onStart(type)
                            }
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
            onDone = { startedAt, seconds, measuredMet ->
                onStop(type, startedAt, seconds, measuredMet)
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
 * The running clock for one exercise, paused whenever the phone stops moving.
 *
 * Elapsed time is accumulated from wall-clock deltas while movement is happening, rather
 * than counted from the start, so a session is billed for the time spent exercising and a
 * dropped tick loses nothing. Standing at a crossing does not earn calories.
 *
 * For skipping the same signal counts jumps, and the rate replaces the assumed effort with
 * a measured one.
 */
@Composable
private fun TimerDialog(type: ExerciseType, onDone: (Long, Int, Double?) -> Unit) {
    val context = LocalContext.current
    val startedAt = remember { System.currentTimeMillis() }
    val monitor = remember(type) { SessionMonitor(countJumps = type == ExerciseType.SKIPPING) }

    var activeMs by remember { mutableLongStateOf(0L) }
    var moving by remember { mutableStateOf(true) }
    var jumps by remember { mutableIntStateOf(0) }
    var kmh by remember { mutableDoubleStateOf(0.0) }
    var metres by remember { mutableDoubleStateOf(0.0) }

    // Speed is only worth measuring where distance is the effort. Skipping goes nowhere,
    // and asking for location during it would be a permission with no purpose.
    val wantsSpeed = type == ExerciseType.RUNNING || type == ExerciseType.CYCLING
    val canMeasure = remember(type) { wantsSpeed && Permissions.canMeasureSpeed(context) }
    val speed = remember(type) { SpeedMonitor() }

    // Registered for the life of the dialog only. A session is bounded, so streaming the
    // accelerometer for it is a fair trade in a way an always-on listener would not be.
    DisposableEffect(type) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return
                monitor.onSample(
                    System.currentTimeMillis(),
                    event.values[0],
                    event.values[1],
                    event.values[2],
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) {
            sensors.registerListener(listener, accelerometer, SAMPLE_US, BATCH_US)
        }
        onDispose { sensors?.unregisterListener(listener) }
    }

    // Registered for the life of the dialog only, and the fixes are consumed for distance
    // and dropped. No coordinate reaches the database or leaves this composable.
    DisposableEffect(canMeasure) {
        if (!canMeasure) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location ->
            speed.onFix(
                timestampMs = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMetres = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            )
        }
        runCatching {
            manager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                FIX_INTERVAL_MS,
                FIX_DISTANCE_M,
                listener,
            )
        }
        onDispose { runCatching { manager?.removeUpdates(listener) } }
    }

    LaunchedEffect(startedAt) {
        var last = System.currentTimeMillis()
        while (true) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            // Only time spent moving is added, which is what makes the pause a pause
            // rather than a label over a clock that keeps running.
            if (monitor.moving) activeMs += now - last
            last = now
            moving = monitor.moving
            jumps = monitor.jumps
            kmh = speed.averageKmh
            metres = speed.metres
        }
    }

    val seconds = (activeMs / 1000L).toInt()
    val minutes = seconds / 60.0
    val rate = if (minutes > 0) jumps / minutes else 0.0
    val measuredMet = when {
        type == ExerciseType.SKIPPING && jumps > 0 -> skippingMet(rate)
        // A handful of metres is a phone settling on a fix, not a session worth costing.
        canMeasure && metres >= MIN_MEASURED_METRES && type == ExerciseType.RUNNING -> runningMet(kmh)
        canMeasure && metres >= MIN_MEASURED_METRES && type == ExerciseType.CYCLING -> cyclingMet(kmh)
        else -> null
    }

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("${type.emoji}  ${type.label}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clock(seconds),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (moving) "Counting" else "Paused, no movement",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (wantsSpeed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            !canMeasure -> "Effort assumed"
                            metres < MIN_MEASURED_METRES -> "Waiting for a GPS fix"
                            else -> String.format(
                                Locale.getDefault(),
                                "%.2f km at %.1f km/h",
                                metres / 1000.0,
                                kmh,
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (canMeasure) {
                            "Speed measured by GPS. No location is stored."
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
                        text = "$jumps jumps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (jumps > 0) {
                            "${rate.toInt()} a minute, measured"
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
                if (seconds < HealthRepository.MIN_SESSION_SECONDS) {
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
        confirmButton = {
            Button(onClick = { onDone(startedAt, seconds, measuredMet) }) { Text("Stop and save") }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDone(startedAt, 0, null) }) { Text("Discard") }
        },
    )
}

/** 25 Hz, enough to resolve a jump without streaming needless samples. */
private const val SAMPLE_US = 40_000

/** A quarter second of buffering, which the clock ticks at anyway. */
private const val BATCH_US = 250_000

private const val TICK_MS = 250L

/** A fix every two seconds is plenty for an average, and far cheaper than the maximum rate. */
private const val FIX_INTERVAL_MS = 2_000L

/** No minimum displacement: the filtering that matters is on accuracy, not distance. */
private const val FIX_DISTANCE_M = 0f

/** Below this the phone is still finding itself, not covering ground. */
private const val MIN_MEASURED_METRES = 50.0

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
