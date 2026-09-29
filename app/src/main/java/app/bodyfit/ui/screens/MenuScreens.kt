package app.bodyfit.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.bodyfit.BuildConfig
import app.bodyfit.R
import app.bodyfit.data.Dates
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.ui.components.GoalSlider
import app.bodyfit.ui.components.InfoLine
import app.bodyfit.ui.components.KeyValueRow
import app.bodyfit.ui.components.SettingsCard
import app.bodyfit.ui.components.Wellness
import app.bodyfit.ui.components.WellnessNote

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
    onName: (String) -> Unit,
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
                NameField(value = settings.name, onValue = onName)
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
                        "Your name is only used to greet you and is never part of a figure. " +
                        "All of it stays on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What to call the user, written as they type.
 *
 * No Save button: every other row on this page commits as it is changed, and a lone field
 * that needed confirming would be the one setting a user could leave half entered. The
 * repository trims and caps what arrives, so the field itself stays a plain box.
 */
@Composable
private fun NameField(value: String, onValue: (String) -> Unit) {
    // The field draws its own text once typing starts, rather than the stored value read
    // back. A write to DataStore is a suspend that completes after the next keystroke has
    // already arrived, so a field fed by the stored value receives characters out of the
    // order they were typed: "Shashi" lands as "ahS". Null means untouched, which is what
    // lets the saved name appear when the page opens.
    var draft by rememberSaveable { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = draft ?: value,
        onValueChange = {
            draft = it
            onValue(it)
        },
        label = { Text("🙂  Your name") },
        placeholder = { Text("Optional") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CupSizeScreen(
    settings: UserSettings,
    onCupSizes: (List<Int>) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Held locally so the user can clear one size before picking its replacement. Saved
    // only when exactly three are picked, so the stored set is never short.
    var picked by remember(settings.cupSizesMl) { mutableStateOf(settings.cupSizesMl.toSet()) }
    MenuPage("Cup sizes", onBack, contentPadding, modifier) {
        item {
            SettingsCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    UserSettings.CUP_SIZES_ML.forEach { size ->
                        val selected = size in picked
                        FilterChip(
                            selected = selected,
                            enabled = selected || picked.size < UserSettings.CUP_COUNT,
                            onClick = {
                                picked = if (selected) picked - size else picked + size
                                if (picked.size == UserSettings.CUP_COUNT) onCupSizes(picked.toList())
                            },
                            label = { Text(Volume.format(size)) },
                        )
                    }
                }
                Text(
                    text = if (picked.size == UserSettings.CUP_COUNT) {
                        "These three sizes are the water buttons on the Today screen and the lock-screen card."
                    } else {
                        "Pick ${UserSettings.CUP_COUNT - picked.size} more to save."
                    },
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

        // The page that explains how each figure is produced is the right place for the full
        // statement of what those figures are worth, rather than a line the user meets first
        // on a card and has no working to read it against.
        item { WellnessNote() }
    }
}

@Composable
fun BackupScreen(
    onExport: () -> Unit,
    onRestore: () -> Unit,
    autoTarget: Uri?,
    autoDefaultLabel: String,
    autoLastRun: Long,
    autoError: String?,
    onChooseAutoTarget: () -> Unit,
    onUseDefaultLocation: () -> Unit,
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
                    text = "🔁  Daily backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                KeyValueRow("Status", "On")
                KeyValueRow("Writes to", if (autoTarget == null) autoDefaultLabel else "A file you chose")
                KeyValueRow(
                    "Last written",
                    if (autoLastRun > 0) Dates.dayLabel(Dates.of(autoLastRun)) else "Not yet",
                )
                Text(
                    text = if (autoTarget == null) {
                        "On from the moment the app is installed, rewriting one file every " +
                            "day rather than adding a new one. It sits outside the app, so " +
                            "uninstalling does not take it with you and a file manager can " +
                            "copy it off the phone. It holds your whole history, so any app " +
                            "you give storage access to can read it. Pick another file to " +
                            "keep it somewhere only you reach."
                    } else {
                        "The file you chose is rewritten every day. Nothing is sent anywhere: " +
                            "it is written straight to that location."
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
                        OutlinedButton(onClick = onUseDefaultLocation) { Text("Use default folder") }
                    }
                }
                Text(
                    text = "The daily write waits for the battery not to be low, so it can " +
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

        item { WellnessNote() }
    }
}
