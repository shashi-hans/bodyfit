package app.bodyfit.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bodyfit.BuildConfig
import app.bodyfit.R
import android.net.Uri
import app.bodyfit.data.Dates
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettings
import app.bodyfit.ui.components.GoalSlider
import app.bodyfit.ui.components.InfoLine
import app.bodyfit.ui.components.KeyValueRow
import app.bodyfit.ui.components.SettingsCard

/**
 * The pages behind the menu on the Today screen.
 *
 * Each is a whole screen with its own title and a back arrow rather than a section of the
 * goals tab, so the goals tab stays about goals and nothing has two homes.
 */

/** Title row with a back arrow, drawn by every page here. */
@Composable
private fun MenuHeader(title: String, onBack: () -> Unit) {
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Shared frame: a back header, then whatever the page puts in the list. */
@Composable
private fun MenuPage(
    title: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MenuHeader(title, onBack) }
        content()
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
fun AboutYouScreen(
    settings: UserSettings,
    onHeight: (Int) -> Unit,
    onWeight: (Int) -> Unit,
    onAge: (Int) -> Unit,
    onSmoker: (Boolean) -> Unit,
    onSex: (Sex) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    MenuPage("About you", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                GoalSlider(
                    emoji = "📏",
                    label = "Height",
                    value = settings.heightCm,
                    range = UserSettings.HEIGHT_RANGE,
                    step = 1,
                    format = { "$it cm" },
                    onCommit = onHeight,
                )
                GoalSlider(
                    emoji = "⚖️",
                    label = "Weight",
                    value = settings.weightKg,
                    range = UserSettings.WEIGHT_RANGE,
                    step = 1,
                    format = { "$it kg" },
                    onCommit = onWeight,
                )
                GoalSlider(
                    emoji = "🎂",
                    label = "Age",
                    value = settings.age,
                    range = UserSettings.AGE_RANGE,
                    step = 1,
                    format = { "$it years" },
                    onCommit = onAge,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Sex.entries.forEach { option ->
                        FilterChip(
                            selected = option == settings.sex,
                            onClick = { onSex(option) },
                            label = {
                                Text(
                                    when (option) {
                                        Sex.MALE -> "Male"
                                        Sex.FEMALE -> "Female"
                                        Sex.UNSPECIFIED -> "Prefer not to say"
                                    }
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🚬  Smoker",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(checked = settings.smoker, onCheckedChange = onSmoker)
                }
                Text(
                    text = "Distance uses your height for stride length, and calories use your " +
                        "weight. Age and sex are used only for the resting-burn estimate. " +
                        "All of it stays on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CupSizeScreen(
    settings: UserSettings,
    onDefaultCup: (Int) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    MenuPage("Default cup size", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    UserSettings.CUP_SIZES_ML.forEach { size ->
                        FilterChip(
                            selected = size == settings.defaultCupMl,
                            onClick = { onDefaultCup(size) },
                            label = { Text("$size") },
                        )
                    }
                }
                Text(
                    text = "This size becomes the first water button on the lock-screen card.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LockScreenCardScreen(
    settings: UserSettings,
    onTrackerEnabled: (Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Re-read on every recomposition rather than remembering: the user leaves for system
    // settings and comes back, and a cached answer would still show the old state.
    val exempt = remember(contentPadding) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    MenuPage("Lock screen card", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Count steps and show the card",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Android needs a visible card to let an app read the step sensor in " +
                                "the background, so this switch controls both.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.trackerEnabled, onCheckedChange = onTrackerEnabled)
                }
            }
        }

        item {
            SettingsCard {
                Text(
                    text = "🔋  Battery",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                KeyValueRow(
                    "Background limits",
                    if (exempt) "Not restricted" else "Restricted",
                )
                Text(
                    text = if (exempt) {
                        "Android is letting the tracker run whenever it needs to. Nothing to do."
                    } else {
                        "Android may stop the tracker to save power. When it does, your step " +
                            "total catches up the next time you open the app, but calories, " +
                            "move minutes and heart points are lost for the time it was off, " +
                            "because those are scored as you walk."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!exempt) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) {
                        Text("Open battery settings")
                    }
                    Text(
                        text = "Find ${stringResource(R.string.app_name)} in that list and allow " +
                            "it to run unrestricted. On Xiaomi phones also set Battery saver to " +
                            "No restrictions and lock the app in Recents.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun HowNumbersWorkScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    MenuPage("How the numbers work", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                InfoLine("👣", "Steps come from the phone's own step sensor. No Google account needed.")
                InfoLine("📍", "Distance is steps times your stride, estimated as 41.5% of your height.")
                InfoLine(
                    "🫀",
                    "Heart points score effort by pace: 1 point a minute above 100 steps a minute, " +
                        "2 above 130.",
                )
                InfoLine(
                    "⏱️",
                    "A move minute is any 60 seconds at 10 or more steps a minute, timed from " +
                        "when you start walking. Pace changes what the minute is worth, not " +
                        "whether it counts.",
                )
                InfoLine(
                    "🔥",
                    "Calories are the cost of active minutes only, from your pace, weight and " +
                        "height. Resting burn is not included. Walking faster always earns " +
                        "more, but a few steps across a room earn nothing.",
                )
                InfoLine("🔐", "Everything is stored on this phone. No account, no server, no analytics.")
            }
        }
    }
}

@Composable
fun BackupScreen(
    onExport: () -> Unit,
    onRestore: () -> Unit,
    autoTarget: Uri?,
    autoLastRun: Long,
    autoError: String?,
    onChooseAutoTarget: () -> Unit,
    onDisableAuto: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    MenuPage("Backup", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                Text(
                    text = "There is no sync and no cloud backup, so a lost phone is a lost " +
                        "history. Export writes every day, hour, drink and setting to a JSON " +
                        "file you choose the location of. Restore reads one back, on this phone " +
                        "or a new one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Text("Export backup") }
                    OutlinedButton(onClick = onRestore) { Text("Restore") }
                }
                Text(
                    text = "Restoring overwrites only the days the file carries. A day tracked " +
                        "since the backup is left alone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard {
                Text(
                    text = "🔁  Weekly backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                KeyValueRow("Status", if (autoTarget == null) "Off" else "On")
                if (autoTarget != null) {
                    KeyValueRow(
                        "Last written",
                        if (autoLastRun > 0) Dates.dayLabel(Dates.of(autoLastRun)) else "Not yet",
                    )
                }
                Text(
                    text = if (autoTarget == null) {
                        "Choose a file once and the app rewrites it every week, so a phone lost " +
                            "between manual exports does not cost you a year. The same file is " +
                            "overwritten each time rather than a new one added."
                    } else {
                        "The chosen file is rewritten every week. Nothing is sent anywhere: it " +
                            "is written straight to the location you picked."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (autoError != null && autoError.isNotBlank()) {
                    Text(
                        text = "Last attempt failed: $autoError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onChooseAutoTarget) {
                        Text(if (autoTarget == null) "Choose a file" else "Change file")
                    }
                    if (autoTarget != null) {
                        OutlinedButton(onClick = onDisableAuto) { Text("Turn off") }
                    }
                }
                Text(
                    text = "The weekly write waits for the battery not to be low, so it can " +
                        "land a few hours late.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    MenuPage("About", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "An activity and hydration tracker that runs entirely on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValueRow("Build", BuildConfig.BUILD_TYPE)
                KeyValueRow("Package", BuildConfig.APPLICATION_ID)
                KeyValueRow("Data", "On this phone only")
            }
        }
    }
}
