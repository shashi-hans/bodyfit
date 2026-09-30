package app.bodyfit.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.bodyfit.MainActivity
import app.bodyfit.R
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.sensor.Metrics
import java.util.Locale

/**
 * The always-on card that carries steps, calories and water to the lock screen.
 *
 * Android does not let an app draw on the keyguard, so this is a foreground-service
 * notification at [NotificationCompat.VISIBILITY_PUBLIC]: it shows its content on the
 * lock screen on every supported version, and its water buttons work from there too.
 */
object ActivityNotification {

    const val CHANNEL_ID = "activity_visible"
    const val ID = 1001

    /** The first channel, at IMPORTANCE_LOW. Removed on start so users see one entry. */
    private const val LEGACY_CHANNEL_ID = "activity"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Xiaomi HyperOS, and Android's own "hide silent notifications" setting, keep
        // IMPORTANCE_LOW cards off the lock screen. The channel therefore sits at
        // IMPORTANCE_DEFAULT with its sound and vibration removed: visible on the
        // keyguard, but it never makes a noise. Importance cannot be raised on a channel
        // that already exists, so this is a new id and the old channel is removed.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily activity",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Steps, calories and water on the lock screen"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** A minimal card, used for the first `startForeground` call before any data is read. */
    fun placeholder(context: Context): Notification = base(context)
        .setContentTitle("👣 Counting your steps")
        .setContentText("Body Fit is watching the step sensor")
        .build()

    /**
     * Everything [build] prints, so a caller can skip a redraw that would change nothing.
     * Kept beside [build] so a figure added to the card is added here in the same edit.
     */
    fun contentKey(record: DailyRecord, settings: UserSettings): List<Any> = listOf(
        record.steps,
        record.activeKcal.toInt(),
        record.heartPoints,
        record.moveMinutes,
        record.waterMl,
        settings.heightCm,
        settings.stepGoal,
        settings.waterGoalMl,
        settings.cupSizesMl,
    )

    fun build(context: Context, record: DailyRecord, settings: UserSettings): Notification {
        val distanceKm = Metrics.distanceKm(record.steps, settings.heightCm)
        val kcal = record.activeKcal.toInt()
        val percent = if (settings.stepGoal > 0) record.steps * 100 / settings.stepGoal else 0
        // No progress bar: a collapsed card gives the bar the same row as the text, and on
        // the lock screen the calorie and water numbers matter more than the bar. The
        // percentage rides along in the title instead.
        val builder = base(context)
            .setContentTitle("👣 ${format(record.steps)} steps · $percent%")
            // Heart points earn their place on the collapsed line: they are the one figure
            // here that says how hard the walking was rather than how much of it there was.
            // The water goal moves to the expanded view to pay for the room.
            .setContentText(
                "🔥 $kcal kcal   🫀 ${record.heartPoints} pts   💧 ${Volume.format(record.waterMl)}"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append("🔥 $kcal kcal   💧 ${Volume.format(record.waterMl)} / ${Volume.format(settings.waterGoalMl)}\n")
                        append("📍 ${String.format(Locale.getDefault(), "%.2f", distanceKm)} km   ")
                        append("⏱️ ${record.moveMinutes} min   ")
                        append("🫀 ${record.heartPoints} pts\n")
                        append("🎯 Goal ${format(settings.stepGoal)} steps")
                    }
                )
            )

        settings.cupSizesMl.forEach { builder.addAction(waterAction(context, it)) }
        return builder.build()
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_footsteps)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setColorized(false)
            .setOngoing(true)
            // Not setSilent: that groups the card under "silent", which some skins hide
            // from the lock screen. The channel has no sound, so it stays quiet anyway,
            // and onlyAlertOnce stops every step update from re-alerting.
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))

    /** Opens the app from a notification. Shared with the exercise session card. */
    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** A button that logs [amountMl] of water. Shared with the water reminder. */
    fun waterAction(context: Context, amountMl: Int): NotificationCompat.Action {
        val intent = Intent(context, WaterActionReceiver::class.java).apply {
            action = WaterActionReceiver.ACTION_ADD_WATER
            putExtra(WaterActionReceiver.EXTRA_AMOUNT_ML, amountMl)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            amountMl,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, "+$amountMl ml", pending).build()
    }

    private fun format(value: Int): String = String.format(Locale.getDefault(), "%,d", value)
}
