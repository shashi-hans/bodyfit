package app.bodyfit.ui.screens

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.bodyfit.data.UserSettings
import app.bodyfit.notification.WaterReminder
import app.bodyfit.sensor.Permissions
import app.bodyfit.ui.components.GoalSlider
import app.bodyfit.ui.components.SettingsCard

/**
 * The drink reminder: on or off, how often, between which hours, and how it sounds.
 *
 * Its own file rather than one more page in MenuScreens, which it would have pushed towards
 * the size where a file stops being readable in one sitting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterReminderScreen(
    settings: UserSettings,
    onEnabled: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
    onHours: (Int, Int) -> Unit,
    onSound: (String) -> Unit,
    onRing: (Boolean) -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Re-read on every return to the app: the user may grant notifications, or turn this
    // app's notifications or the reminder channel off, in the phone's settings meanwhile.
    var notificationsAllowed by remember { mutableStateOf(Permissions.hasNotifications(context)) }
    var channelBlocked by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, settings.waterReminderSound, settings.waterReminderRingUntilStopped) {
        fun refresh() {
            notificationsAllowed = Permissions.hasNotifications(context)
            channelBlocked = WaterReminder.isBlocked(context, settings)
        }
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun openNotificationSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    // Asked when the reminder is switched on, the moment its purpose is on screen. Android
    // 12 and older grant notifications without a prompt. A refusal Android no longer asks
    // about, or notifications turned off for the app, can only be undone in its settings.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = Permissions.hasNotifications(context)
        if (notificationsAllowed) onEnabled(true) else if (!granted) openNotificationSettings()
    }
    val active = settings.waterReminderEnabled && notificationsAllowed

    // The system sound picker: it previews each sound as it is tapped, lists notification
    // sounds, ringtones and alarms, and offers Default and Silent.
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        onSound(
            when (picked) {
                null -> UserSettings.SOUND_SILENT
                Settings.System.DEFAULT_NOTIFICATION_URI -> ""
                else -> picked.toString()
            }
        )
    }

    MenuPage("Water reminders", onBack, contentPadding, modifier) {
        if (active && channelBlocked) {
            item {
                SettingsCard {
                    Text(
                        text = "⚠️  Water reminders are turned off for Body Fit in the phone's " +
                            "notification settings, so none will show.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = ::openNotificationSettings) { Text("Open notification settings") }
                }
            }
        }
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Remind me to drink",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = active,
                        onCheckedChange = { on ->
                            when {
                                !on || notificationsAllowed -> onEnabled(on)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !Permissions.hasNotificationPermission(context) ->
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> openNotificationSettings()
                            }
                        },
                    )
                }
                Text(
                    text = "Every",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    UserSettings.WATER_REMINDER_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = minutes == settings.waterReminderMinutes,
                            enabled = active,
                            onClick = { onMinutes(minutes) },
                            label = { Text(reminderLabel(minutes)) },
                        )
                    }
                }
                GoalSlider(
                    emoji = "🌅",
                    label = "From",
                    value = settings.waterReminderStartHour,
                    range = UserSettings.START_HOUR_RANGE,
                    step = 1,
                    format = WaterReminder::hourLabel,
                    onCommit = { onHours(it, settings.waterReminderEndHour) },
                )
                GoalSlider(
                    emoji = "🌙",
                    label = "Until",
                    value = settings.waterReminderEndHour,
                    range = UserSettings.END_HOUR_RANGE,
                    step = 1,
                    format = WaterReminder::hourLabel,
                    onCommit = { onHours(settings.waterReminderStartHour, it) },
                )
                Text(
                    text = if (!notificationsAllowed && settings.waterReminderEnabled) {
                        "Notifications are off for Body Fit, so no reminder can be shown. Turn " +
                            "them on in the phone's settings."
                    } else {
                        val hours = if (settings.waterReminderStartHour == settings.waterReminderEndHour) {
                            "At any hour"
                        } else {
                            "Only from ${WaterReminder.hourLabel(settings.waterReminderStartHour)} " +
                                "until ${WaterReminder.hourLabel(settings.waterReminderEndHour)}"
                        }
                        "$hours. Skipped once today's goal is met, or when you logged a drink " +
                            "within the interval. Swipe a reminder away and it rings again in " +
                            "${WaterReminder.SNOOZE_MINUTES} minutes unless you log a drink; tap a " +
                            "cup on it to log one."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔔  Sound",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = WaterReminder.soundTitle(context, settings),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val current = when (settings.waterReminderSound) {
                                "" -> Settings.System.DEFAULT_NOTIFICATION_URI
                                UserSettings.SOUND_SILENT -> null
                                else -> Uri.parse(settings.waterReminderSound)
                            }
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Reminder sound")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                    Settings.System.DEFAULT_NOTIFICATION_URI,
                                )
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                            runCatching { soundPicker.launch(intent) }
                        },
                    ) { Text("Change") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Keep ringing until I respond",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = settings.waterReminderRingUntilStopped, onCheckedChange = onRing)
                }
                OutlinedButton(onClick = onTest, enabled = notificationsAllowed) {
                    Text("Send a test reminder")
                }
                Text(
                    text = if (settings.waterReminderRingUntilStopped) {
                        "Rings over and over at the notification volume, and drops down over the " +
                            "screen with your cup buttons, until you tap a cup, swipe it away or " +
                            "pull down the notification shade. Do Not Disturb still silences it. "
                    } else {
                        "Plays once when a reminder arrives, at the notification volume. "
                    } + "The test ignores your hours and today's total, so you can hear it now.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "30 min", "1 hour", "1½ hours". */
private fun reminderLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "${minutes / 60}½ hours"
}
