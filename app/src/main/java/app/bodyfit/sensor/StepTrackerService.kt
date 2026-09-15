package app.bodyfit.sensor

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.bodyfit.data.Dates
import app.bodyfit.data.HealthRepository
import app.bodyfit.data.TrackerStateRepository
import app.bodyfit.data.UserSettings
import app.bodyfit.notification.ActivityNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Counts steps in the background and keeps the lock-screen card current.
 *
 * The hardware counter reports a running total since boot, so each reading is
 * turned into a delta and banked against today's row. Steps are also bucketed
 * into 60-second windows that start on the first step after a rest, not on the
 * clock. A window at 10 or more steps a minute counts as a move minute; its
 * cadence decides how many heart points it scores and what it cost in calories.
 * Everything is written through [HealthRepository] to the local database;
 * nothing is sent anywhere.
 */
class StepTrackerService : LifecycleService(), SensorEventListener {

    private lateinit var repository: HealthRepository
    private lateinit var trackerState: TrackerStateRepository
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            lifecycleScope.launch {
                // A write can fail: the disk is full, the row is locked, the process is
                // shutting down. An uncaught throw here reaches the default handler and
                // takes the whole service down, losing the counter with it, so the tick
                // swallows it and the next one retries with the steps still banked.
                runCatching { flush() }
                    .onFailure { Log.w(TAG, "tick failed, retrying on the next one", it) }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Last raw reading from the counter, or -1 before the first event. */
    private var lastRawCount = -1L

    /** Last raw reading written to disk, so a tick with no new steps writes nothing. */
    private var persistedRawCount = -1L

    /** Steps seen since the last write to the database. */
    private var pendingSteps = 0

    /** Steps seen inside the window currently being measured. */
    private var stepsInWindow = 0

    /** When the open window started, or -1 when no window is open. */
    private var windowStartMs = -1L

    /** Date the open window started on, so a window that ends after midnight lands correctly. */
    private var windowDate = Dates.today()

    /** Hour the open window started in. A window spanning two hours scores in the first. */
    private var windowHour = Dates.currentHour()

    /** Date the lock-screen card is following, so it is repointed once at midnight. */
    private var notifiedDate = Dates.today()

    private var settings = UserSettings()
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = HealthRepository(applicationContext)
        trackerState = TrackerStateRepository(applicationContext)
        ActivityNotification.ensureChannel(this)
        startInForeground()

        sensorManager = getSystemService(SensorManager::class.java)
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        lifecycleScope.launch {
            lastRawCount = trackerState.lastRawCount()
            persistedRawCount = lastRawCount
            registerSensor()
        }
        lifecycleScope.launch {
            repository.settings.collect { settings = it }
        }
        restartNotificationUpdates()
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values.firstOrNull()?.toLong() ?: return
        val delta = when {
            // First reading of all time: adopt it as the baseline, bank nothing.
            lastRawCount < 0 -> 0L
            // A lower reading means the device rebooted and the counter restarted at zero.
            raw < lastRawCount -> raw
            else -> raw - lastRawCount
        }
        lastRawCount = raw
        if (delta > 0) {
            val steps = delta.toInt()
            pendingSteps += steps
            // The first step after a rest opens the window, so the 60 seconds are measured
            // from when walking started rather than from the next tick of the clock.
            if (windowStartMs < 0) {
                windowStartMs = System.currentTimeMillis()
                windowDate = Dates.today()
                windowHour = Dates.currentHour()
            }
            stepsInWindow += steps
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSensor() {
        val sensor = stepSensor
        if (sensor == null) {
            Log.w(TAG, "no step counter on this device; the tracker will report zero")
            return
        }
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
    }

    /**
     * Writes what has been counted since the last tick and scores the open window once
     * it has run its 60 seconds. Called every [TICK_MS] milliseconds.
     *
     * A window is opened by the first step after a rest, not by the clock, so a walk
     * that starts at 10:00:40 is measured to 10:01:40. When it closes, no new window
     * opens until the next step, so standing still adds nothing.
     *
     * A window is scored on the time it actually ran, not on an assumed 60 seconds. The
     * tick can only notice it has expired up to [TICK_MS] late, and treating 65 seconds
     * of steps as a minute would read as a pace nobody walked.
     */
    private suspend fun flush() {
        val now = System.currentTimeMillis()
        val steps = pendingSteps
        // Captured together with the steps. Persisting a raw count that runs ahead of what
        // was recorded would silently drop the difference on the next restart, because the
        // baseline would already account for steps no row ever received.
        val rawAtCapture = lastRawCount
        pendingSteps = 0

        var moveMinutes = 0
        var heartPoints = 0
        var kcal = 0.0
        val today = Dates.today()
        val hour = Dates.currentHour()
        var scoreDate = today
        var scoreHour = hour
        val windowClosed = windowStartMs >= 0 && now - windowStartMs >= WINDOW_MS

        if (windowClosed) {
            val ranForMinutes = (now - windowStartMs) / WINDOW_MS.toDouble()
            val cadence = (stepsInWindow / ranForMinutes).toInt()
            if (Metrics.isMoveMinute(cadence)) moveMinutes = 1
            heartPoints = Metrics.heartPointsForMinute(cadence)
            kcal = Metrics.kcalForMinute(cadence, settings.weightKg, settings.heightCm) * ranForMinutes
            scoreDate = windowDate
            scoreHour = windowHour
            stepsInWindow = 0
            windowStartMs = -1L
        }

        try {
            if (scoreDate == today && scoreHour == hour) {
                repository.recordActivity(today, hour, steps, moveMinutes, heartPoints, kcal)
            } else {
                // The window opened in an earlier hour, or before midnight, so its minute
                // belongs there while the steps counted since the last tick belong to now.
                repository.recordActivity(scoreDate, scoreHour, 0, moveMinutes, heartPoints, kcal)
                repository.recordActivity(today, hour, steps, 0, 0, 0.0)
            }
        } catch (e: Exception) {
            // Put the steps back so the next tick tries again rather than losing them.
            pendingSteps += steps
            throw e
        }
        if (rawAtCapture >= 0 && rawAtCapture != persistedRawCount) {
            trackerState.setLastRawCount(rawAtCapture)
            persistedRawCount = rawAtCapture
        }

        if (today != notifiedDate) {
            notifiedDate = today
            restartNotificationUpdates()
            // A new day is the one moment the oldest hourly rows can fall out of range.
            runCatching { repository.pruneHourly() }
                .onFailure { Log.w(TAG, "could not prune old hourly rows", it) }
        }
    }

    /** Redraws the card whenever today's row changes, including water logged elsewhere. */
    private fun restartNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = lifecycleScope.launch {
            val date = Dates.today()
            repository.observeDay(date).collectLatest { record ->
                if (!Permissions.hasNotifications(this@StepTrackerService)) return@collectLatest
                val current = repository.currentSettings()
                val card = ActivityNotification.build(this@StepTrackerService, record, current)
                try {
                    NotificationManagerCompat.from(this@StepTrackerService).notify(ActivityNotification.ID, card)
                } catch (e: SecurityException) {
                    // Notification access was revoked between the check above and this call.
                    Log.w(TAG, "cannot post the lock-screen card", e)
                }
            }
        }
    }

    private fun startInForeground() {
        // The "health" service type exists from API 34; older releases accept 0.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, ActivityNotification.ID, ActivityNotification.placeholder(this), type)
        } catch (e: SecurityException) {
            // Reached when activity recognition was revoked while the service was starting.
            Log.w(TAG, "cannot run in the foreground without activity recognition", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "StepTracker"
        private const val TICK_MS = 5_000L

        /** Length of a measured window. Cadence is steps per window, so this is one minute. */
        private const val WINDOW_MS = 60_000L

        /** Starts the tracker. Safe to call repeatedly; a running service just keeps running. */
        fun start(context: Context) {
            if (!Permissions.hasActivityRecognition(context)) return
            val intent = Intent(context, StepTrackerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepTrackerService::class.java))
        }
    }
}
