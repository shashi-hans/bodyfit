package app.bodyfit.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bodyfit.ui.components.AppLogo
import app.bodyfit.ui.components.InfoLine
import app.bodyfit.ui.components.SettingsCard

/**
 * The last thing first-run asks, and the one that decides whether any of it works.
 *
 * Android lets a phone freeze an app it thinks is idle. A frozen tracker keeps its step
 * total, because the hardware counter is cumulative, but loses the pace those steps were
 * taken at, and pace is what calories and heart points are made of. The result is a phone
 * that looks like it is counting while quietly reporting a fraction of the day.
 *
 * Asked after the permission prompts rather than before, because two system dialogs in a
 * row is already as much as a first run should spend, and because this one is not a
 * permission: it opens a settings page and the user does the rest.
 *
 * Skippable on purpose. The app does work without it, just less reliably, and a first run
 * that cannot be finished without visiting Settings is a first run people abandon.
 */
@Composable
fun BackgroundAccessScreen(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        text = "One last thing",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Android can put apps to sleep to save battery. If it does " +
                            "that to Body Fit, your steps still arrive but the pace they " +
                            "were walked at is lost, and pace is what calories and heart " +
                            "points are worked out from. The day then reads far lower than " +
                            "you actually walked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard {
                    InfoLine("🔋", "Find Body Fit in the list and set it to not optimised.")
                    vendorHint()?.let { InfoLine("⚠️", it) }
                    InfoLine(
                        "🔒",
                        "This lets the app keep counting. It grants no access to anything " +
                            "else, and nothing leaves the phone either way.",
                    )
                }
            }

            item {
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open battery settings")
                }
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onSkip) { Text("Skip for now") }
                    Text(
                        text = "You can come back to this from the menu, under Lock screen card.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The extra switch some manufacturers keep outside Android's own battery settings.
 *
 * Android's exemption does not bind these: Oppo, Realme and OnePlus freeze through their
 * own framework, and Xiaomi and Vivo do the same, so a user who grants the standard
 * exemption on those phones is still not done. No app can open those pages, so the only
 * honest thing to do is name them.
 */
private fun vendorHint(): String? {
    val make = Build.MANUFACTURER.lowercase()
    return when {
        listOf("oppo", "realme", "oneplus").any { it in make } ->
            "On this phone also open Settings, Battery, App battery management, Body Fit, " +
                "then allow background activity and turn off Sleep standby optimisation."
        listOf("xiaomi", "redmi", "poco").any { it in make } ->
            "On this phone also open Settings, Apps, Body Fit, Battery saver, and choose " +
                "No restrictions, then turn on Autostart."
        "vivo" in make || "iqoo" in make ->
            "On this phone also open Settings, Battery, Background power consumption " +
                "management, and allow Body Fit to run in the background."
        else -> null
    }
}
