package app.bodyfit.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.bodyfit.R
import app.bodyfit.data.DrinkState
import app.bodyfit.data.HealthRepository
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.sensor.Permissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * A reminder to drink, at the interval the user picked on the Water reminders page.
 *
 * WorkManager rather than exact alarms: a reminder a few minutes late costs nothing, and
 * exact alarms need a permission Play reviews. The schedule survives a reboot on its own.
 */
object WaterReminder {

    const val NOTIFICATION_ID = 1003

    /** Every reminder channel id starts with this; the rest names the sound it plays. */
    private const val CHANNEL_PREFIX = "water_reminder"
    private const val WORK_NAME = "water-reminder"
    private const val SNOOZE_WORK_NAME = "water-reminder-snooze"
    private const val DISMISS_REQUEST = 1003
    private const val ALARM_REQUEST = 1004

    /** How long a dismissed reminder waits before ringing again, if no drink was logged. */
    const val SNOOZE_MINUTES = 15L

    /**
     * Whether [hour] falls in the user's reminder hours. An end of 24 runs to the end of the
     * day. A start later than the end runs overnight, so 22 to 6 covers the small hours;
     * equal hours mean all day.
     */
    fun inWindow(settings: UserSettings, hour: Int): Boolean {
        val start = settings.waterReminderStartHour
        val end = settings.waterReminderEndHour
        return when {
            start == end -> true
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }

    /**
     * "8:00 AM" or "08:00", in the phone's own time format. 24 is the end of the day and
     * reads as the last minute of it, 11:59 PM.
     */
    fun hourLabel(hour: Int): String {
        val time = if (hour >= 24) LocalTime.of(23, 59) else LocalTime.of(hour, 0)
        return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    /** Starts, reschedules or cancels the reminder to match [settings]. */
    fun apply(context: Context, settings: UserSettings) {
        val work = WorkManager.getInstance(context)
        if (!settings.waterReminderEnabled) {
            work.cancelUniqueWork(WORK_NAME)
            work.cancelUniqueWork(SNOOZE_WORK_NAME)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(
            settings.waterReminderMinutes.toLong(),
            TimeUnit.MINUTES,
        ).build()
        // UPDATE keeps the running schedule when nothing changed and retimes it when the
        // interval did, without a second copy of the work.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Inside the user's hours, reminders on, and the day's goal not yet met. */
    private fun eligible(settings: UserSettings, drankTodayMl: Int, time: LocalTime): Boolean =
        settings.waterReminderEnabled &&
            inWindow(settings, time.hour) &&
            drankTodayMl < settings.waterGoalMl

    /**
     * Slack on the "drank within the interval" rule. The periodic run lands a full interval
     * after the last one, so a cup tapped on a reminder a few seconds after it rang would
     * otherwise read as "just drank" when the next run arrives and cancel it.
     */
    private fun graceMs(intervalMs: Long): Long = maxOf(5 * 60_000L, intervalMs / 4)

    /**
     * Whether a reminder is worth sending now.
     *
     * Not outside the user's hours, not once the day's goal is met, and not when a drink was
     * logged well within the interval: a reminder right after a glass reads as the app not
     * listening.
     */
    fun isDue(
        settings: UserSettings,
        drankTodayMl: Int,
        lastDrinkAt: Long?,
        now: Long = System.currentTimeMillis(),
        time: LocalTime = LocalTime.now(),
    ): Boolean {
        if (!eligible(settings, drankTodayMl, time)) return false
        val intervalMs = settings.waterReminderMinutes * 60_000L
        return lastDrinkAt == null || now - lastDrinkAt >= intervalMs - graceMs(intervalMs)
    }

    /**
     * Whether a dismissed reminder should ring again: the same hours and goal rules, and no
     * drink logged since it was dismissed.
     */
    fun isSnoozeDue(
        settings: UserSettings,
        drankTodayMl: Int,
        lastDrinkAt: Long?,
        dismissedAt: Long,
        time: LocalTime = LocalTime.now(),
    ): Boolean =
        eligible(settings, drankTodayMl, time) && (lastDrinkAt == null || lastDrinkAt < dismissedAt)

    /**
     * When the next reminder will actually ring, for the Today screen.
     *
     * The scheduled checks alone would mislead: a check outside the user's hours, after the
     * goal is met, or soon after a drink rings nothing. So each upcoming check, the waiting
     * snooze and then the regular run and every interval after it for two days, is put
     * through the same rules the workers use, and the first that would ring is the answer.
     * A check on a later day starts that day's total at zero.
     */
    fun predictNext(
        settings: UserSettings,
        drankTodayMl: Int,
        lastDrinkAt: Long?,
        nextRunAt: Long?,
        snoozeAt: Long?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NextReminder {
        if (!settings.waterReminderEnabled) return NextReminder.Off
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val goalReached = drankTodayMl >= settings.waterGoalMl
        fun drankOn(at: Long) =
            if (Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == today) drankTodayMl else 0
        fun timeOf(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalTime()

        snoozeAt?.let { at ->
            val dismissedAt = at - SNOOZE_MINUTES * 60_000L
            if (isSnoozeDue(settings, drankOn(at), lastDrinkAt, dismissedAt, timeOf(at))) {
                return NextReminder.At(maxOf(at, now), goalReached)
            }
        }
        val intervalMs = settings.waterReminderMinutes * 60_000L
        var at = maxOf(nextRunAt ?: return NextReminder.Unknown, now)
        val until = now + 2 * 24 * 60 * 60_000L
        while (at <= until) {
            if (isDue(settings, drankOn(at), lastDrinkAt, now = at, time = timeOf(at))) {
                return NextReminder.At(at, goalReached)
            }
            at += intervalMs
        }
        return NextReminder.Unknown
    }

    /**
     * The next run of the regular reminder and of a waiting snooze, as WorkManager schedules
     * them; null where none is waiting.
     */
    fun observeNextRuns(context: Context): Flow<Pair<Long?, Long?>> {
        val work = WorkManager.getInstance(context)
        fun List<WorkInfo>.nextAt(): Long? = firstOrNull { it.state == WorkInfo.State.ENQUEUED }
            ?.nextScheduleTimeMillis
            ?.takeIf { it in 1 until Long.MAX_VALUE }
        return combine(
            work.getWorkInfosForUniqueWorkFlow(WORK_NAME),
            work.getWorkInfosForUniqueWorkFlow(SNOOZE_WORK_NAME),
        ) { regular, snooze -> regular.nextAt() to snooze.nextAt() }
    }

    /** Drops a waiting snooze, when a regular reminder has just rung in its place. */
    fun cancelSnooze(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SNOOZE_WORK_NAME)
    }

    /** Rings the reminder again in [SNOOZE_MINUTES], replacing any snooze already waiting. */
    fun snooze(context: Context, dismissedAt: Long) {
        val request = OneTimeWorkRequestBuilder<WaterReminderSnoozeWorker>()
            .setInitialDelay(SNOOZE_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(WaterReminderSnoozeWorker.KEY_DISMISSED_AT to dismissedAt))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SNOOZE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * The sound [settings] asks for: null for silence, the phone's default notification
     * sound when none was picked.
     */
    fun soundUri(settings: UserSettings): Uri? = when (settings.waterReminderSound) {
        "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        UserSettings.SOUND_SILENT -> null
        else -> Uri.parse(settings.waterReminderSound)
    }

    /** A name for the chosen sound, as the system picker shows it. */
    fun soundTitle(context: Context, settings: UserSettings): String = when (settings.waterReminderSound) {
        "" -> "Default"
        UserSettings.SOUND_SILENT -> "Silent"
        else -> runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(settings.waterReminderSound))?.getTitle(context)
        }.getOrNull() ?: "Custom sound"
    }

    /**
     * Makes sure the channel for the chosen sound exists, and returns its id.
     *
     * Android fixes a channel's sound and importance once it is created, so each combination
     * gets a channel of its own and the others are deleted when it is first made. Only one
     * reminder channel is ever left in the phone's notification settings. A channel that
     * already exists is used as it is, so a post does no more than one lookup.
     *
     * A reminder that rings until stopped is high importance, so it drops down over whatever
     * is on screen with its cup buttons while it rings, rather than waiting in the shade.
     */
    fun ensureChannel(context: Context, settings: UserSettings): String {
        val ring = settings.waterReminderRingUntilStopped
        val key = "${settings.waterReminderSound}|$ring"
        val id = "${CHANNEL_PREFIX}_${key.hashCode().toUInt().toString(16)}"
        val importance = if (ring) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        val manager = context.getSystemService(NotificationManager::class.java) ?: return id
        if (manager.getNotificationChannel(id) == null) {
            manager.notificationChannels
                .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
                .forEach { manager.deleteNotificationChannel(it.id) }
            val channel = NotificationChannel(id, "Water reminders", importance).apply {
                description = "A reminder to drink, at the interval you set"
                setShowBadge(false)
                setSound(
                    soundUri(settings),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    /**
     * Whether the user has turned this reminder's notifications off in the phone's settings.
     *
     * Android brings a deleted channel back with the user's old choices when the same id is
     * made again, so switching the ring or sound back can land on a channel blocked months
     * ago. The page says so rather than letting reminders stop without a word.
     */
    fun isBlocked(context: Context, settings: UserSettings): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.getNotificationChannel(ensureChannel(context, settings))?.importance ==
            NotificationManager.IMPORTANCE_NONE
    }

    // Guarded by Permissions.hasNotifications at the top; lint only recognises a check
    // written inline at the call site.
    @SuppressLint("MissingPermission")
    fun post(context: Context, settings: UserSettings, drankTodayMl: Int) {
        if (!Permissions.hasNotifications(context)) return
        val channelId = ensureChannel(context, settings)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_footsteps)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle("💧 Time for some water")
            .setContentText("${Volume.format(drankTodayMl)} of ${Volume.format(settings.waterGoalMl)} today")
            .setCategory(
                if (settings.waterReminderRingUntilStopped) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_REMINDER,
            )
            .setAutoCancel(true)
            .setContentIntent(
                if (settings.waterReminderRingUntilStopped) alarmIntent(context)
                else ActivityNotification.openApp(context),
            )
            // Fired when the user swipes the reminder away or clears all notifications, and not
            // when a cup button or the app cancels it, so only a dismissal starts the snooze.
            .setDeleteIntent(dismissIntent(context))
        settings.cupSizesMl.forEach { builder.addAction(ActivityNotification.waterAction(context, it)) }
        if (settings.waterReminderRingUntilStopped) {
            // Opens the reminder full screen over the lock screen, like an alarm. While the
            // phone is in use Android shows it as a pop-up that stays until answered instead,
            // so a ringing phone always shows what is ringing.
            builder.setFullScreenIntent(alarmIntent(context), true)
        }
        val notification = builder.build()
        if (settings.waterReminderRingUntilStopped) {
            // Android repeats the sound until the notification is answered, swiped away or
            // the shade is opened: each of those is the user having noticed it.
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /**
     * Whether Android lets this app open the reminder full screen. Android 14 and later leave
     * that to the user for apps that are not alarm clocks or phone apps.
     */
    fun canShowFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            ALARM_REQUEST,
            Intent(context, WaterReminderAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun dismissIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST,
            Intent(context, WaterReminderDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Hears the reminder being swiped away and schedules it to ring again. */
class WaterReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WaterReminder.snooze(context.applicationContext, dismissedAt = System.currentTimeMillis())
    }
}

/**
 * Loads what a reminder check needs and posts the reminder when [due] says so. Returns
 * whether it posted. Shared by the regular reminder and the snooze.
 */
private suspend fun checkAndPost(
    context: Context,
    due: (UserSettings, DrinkState) -> Boolean,
): Boolean {
    val repository = HealthRepository(context)
    val settings = repository.currentSettings()
    val state = repository.drinkState()
    if (!due(settings, state)) return false
    WaterReminder.post(context, settings, state.drankTodayMl)
    return true
}

/** Rings a dismissed reminder again unless water was logged since. */
class WaterReminderSnoozeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dismissedAt = inputData.getLong(KEY_DISMISSED_AT, 0L)
        checkAndPost(applicationContext) { settings, state ->
            WaterReminder.isSnoozeDue(settings, state.drankTodayMl, state.lastDrinkAt, dismissedAt)
        }
        return Result.success()
    }

    companion object {
        const val KEY_DISMISSED_AT = "dismissed_at"
    }
}

/** Checks whether a reminder is due and posts it. Runs at the interval the user set. */
class WaterReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val posted = checkAndPost(applicationContext) { settings, state ->
            WaterReminder.isDue(settings, state.drankTodayMl, state.lastDrinkAt)
        }
        // A snooze waiting from an earlier dismissal would ring a second time minutes later.
        if (posted) WaterReminder.cancelSnooze(applicationContext)
        return Result.success()
    }
}

/** What the Today screen says about the next water reminder. */
sealed interface NextReminder {
    /** Reminders are switched off. */
    data object Off : NextReminder

    /** On, but nothing is scheduled yet, or nothing would ring in the next two days. */
    data object Unknown : NextReminder

    /** The next reminder rings at [atMillis]; [goalReachedToday] when today needs no more. */
    data class At(val atMillis: Long, val goalReachedToday: Boolean) : NextReminder
}
