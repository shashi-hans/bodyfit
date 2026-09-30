package app.bodyfit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.bodyfit.data.Backup
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettings
import app.bodyfit.ui.components.AppLogo
import app.bodyfit.ui.components.GoalSlider
import app.bodyfit.ui.components.SettingsCard
import app.bodyfit.ui.components.SexChips
import app.bodyfit.ui.components.WellnessNote
import kotlinx.coroutines.launch

/**
 * The one screen a fresh install opens on, asked once and never again.
 *
 * Every figure the app reports is scaled by height or weight: distance is steps times a
 * stride derived from height, calories are linear in weight, BMI and resting burn need
 * both. Opening straight into the app would therefore show a full screen of numbers
 * computed from untouched defaults, which look like measurements of the user while being
 * measurements of nobody. Asking first is the only way the first day's figures mean
 * anything.
 *
 * Each measurement has to be moved before the button enables. A slider already sitting on
 * a plausible default cannot tell "this is my height" apart from "I did not read this
 * screen", and the second is what the defaults would quietly become. Sex has no default
 * for the same reason: "Prefer not to say" is a real answer the user picks, not a value
 * they fail to change. The name is the exception and stays optional, because nothing is
 * calculated from it.
 */
@Composable
fun SetupScreen(
    onDone: (name: String, heightCm: Int, weightKg: Int, age: Int, sex: Sex) -> Unit,
    /** Reads a backup file and returns how many days it restored, or fails with a reason. */
    onRestore: suspend (Uri) -> Result<Int>,
    modifier: Modifier = Modifier,
) {
    val defaults = UserSettings()
    var name by rememberSaveable { mutableStateOf("") }
    var heightCm by rememberSaveable { mutableIntStateOf(defaults.heightCm) }
    var weightKg by rememberSaveable { mutableIntStateOf(defaults.weightKg) }
    var age by rememberSaveable { mutableIntStateOf(defaults.age) }
    var sex by rememberSaveable { mutableStateOf<Sex?>(null) }

    var heightSet by rememberSaveable { mutableStateOf(false) }
    var weightSet by rememberSaveable { mutableStateOf(false) }
    var ageSet by rememberSaveable { mutableStateOf(false) }

    val ready = heightSet && weightSet && ageSet && sex != null

    val scope = rememberCoroutineScope()
    var restoring by rememberSaveable { mutableStateOf(false) }
    var restoreError by rememberSaveable { mutableStateOf<String?>(null) }

    // The picker rather than a path: no storage permission, and the app never sees a file
    // it was not handed. The same route the backup page uses, so a file written by either
    // is read by either.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoring = true
        restoreError = null
        scope.launch {
            onRestore(uri)
                .onFailure { restoreError = it.message ?: "That file could not be read." }
            restoring = false
        }
    }

    Scaffold(modifier = modifier) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                AppLogo()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Before we start",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your height and weight are what turn steps into distance and " +
                        "calories, and age and sex set your resting burn. Without them the " +
                        "app can count steps but every other figure would be guesswork. " +
                        "It is asked once, stays on this phone, and can be changed later " +
                        "under About you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("🙂  Your name") },
                    placeholder = { Text("Optional") },
                    supportingText = { Text("Left blank, the app will call you Guest.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                GoalSlider(
                    emoji = "📏",
                    label = "Height",
                    value = heightCm,
                    range = UserSettings.HEIGHT_RANGE,
                    step = 1,
                    format = { "$it cm" },
                    onCommit = {
                        heightCm = it
                        heightSet = true
                    },
                )
                GoalSlider(
                    emoji = "⚖️",
                    label = "Weight",
                    value = weightKg,
                    range = UserSettings.WEIGHT_RANGE,
                    step = 1,
                    format = { "$it kg" },
                    onCommit = {
                        weightKg = it
                        weightSet = true
                    },
                )
                GoalSlider(
                    emoji = "🎂",
                    label = "Age",
                    value = age,
                    range = UserSettings.AGE_RANGE,
                    step = 1,
                    format = { "$it years" },
                    onCommit = {
                        age = it
                        ageSet = true
                    },
                )
                SexChips(selected = sex, onSelect = { sex = it })
            }
        }

        item {
            Button(
                onClick = { onDone(name, heightCm, weightKg, age, sex ?: Sex.UNSPECIFIED) },
                enabled = ready && !restoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start tracking")
            }
        }

        // The second way in, for a phone replacing one that already had the app. A backup
        // carries the same measurements this screen asks for, so restoring answers the
        // questions and opens the app in one step rather than asking twice.
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "or",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { picker.launch(arrayOf(Backup.MIME_TYPE, "*/*")) },
                    enabled = !restoring,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (restoring) "Restoring..." else "Restore from a backup file")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Moving from another phone? Your file carries your height, weight, " +
                        "age and sex, so restoring fills this in and starts the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                restoreError?.let { message ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            Text(
                // Named rather than left to be discovered from a greyed-out button, which
                // says something is missing without saying what.
                text = if (ready) {
                    "Counting starts as soon as you tap."
                } else {
                    "Set your height, weight and age, and choose one of the three above."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { WellnessNote() }

        item { Spacer(Modifier.height(4.dp)) }
    }
    }
}
