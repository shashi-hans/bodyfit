package app.bodyfit.sensor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.bodyfit.R
import app.bodyfit.data.ExerciseType
import app.bodyfit.data.TrackerStateRepository
import app.bodyfit.notification.ActivityNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What a running exercise has measured so far. */
data class LiveSession(
    val type: ExerciseType,
    val startedAt: Long,
    /** Time spent moving. Standing at a crossing is not added. */
    val activeMs: Long = 0L,
    val moving: Boolean = true,
    val jumps: Int = 0,
    val kmh: Double = 0.0,
    val metres: Double = 0.0,
    /** True when GPS is being read for this session. */
    val measuringSpeed: Boolean = false,
) {
    val seconds: Int get() = (activeMs / 1000L).toInt()

    /** Jumps a minute over the time spent moving, 0 before any time has passed. */
    val jumpRate: Double get() = if (seconds > 0) jumps / (seconds / 60.0) else 0.0

    /** True once enough ground is covered for the speed to be a measurement. */
    val hasSpeed: Boolean get() = measuringSpeed && metres >= MIN_MEASURED_METRES

    /**
     * The effort measured for this session, or null where the activity's assumed effort
     * stands: no jumps counted, no GPS, or too little ground covered to trust the speed.
     */
    val measuredMet: Double?
        get() = when {
            type == ExerciseType.SKIPPING && jumps > 0 -> skippingMet(jumpRate)
            hasSpeed && type == ExerciseType.RUNNING -> runningMet(kmh)
            hasSpeed && type == ExerciseType.CYCLING -> cyclingMet(kmh)
            else -> null
        }

    companion object {
        /** Below this the phone is still finding itself, not covering ground. */
        const val MIN_MEASURED_METRES = 50.0
    }
}

/**
 * Times one exercise in the foreground, so it keeps measuring with the screen off.
 *
 * A screen-owned timer loses while-in-use location the moment the phone locks in a pocket,
 * and the run comes out short. A foreground service of type `location` keeps that access for
 * as long as its notification is showing, and a partial wake lock keeps the clock and the
 * accelerometer running while the screen is off. Location permission is still asked only
 * when a running or cycling session starts, and the service runs only between start and stop.
 *
 * The service owns the session flag that stops the step tracker scoring the same minutes
 * twice: it sets the flag once it is running and clears it when it ends, so no failed start
 * or killed process can leave walking unscored.
 *
 * Coordinates are consumed by [SpeedMonitor] and dropped. Nothing here stores a position.
 */
class ExerciseSessionService : LifecycleService(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var locationManager: LocationManager? = null
    private var monitor: SessionMonitor? = null
    private val speed = SpeedMonitor()
    private var wakeLock: PowerManager.WakeLock? = null

    // An object rather than a lambda: before Android 11 the listener's other three methods
    // have no default body, and a provider switched off mid-run would call one of them.
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            speed.onFix(
                // The monotonic time of the fix, so a network clock change mid-run cannot
                // make two fixes look an impossible distance apart in time.
                timestampMs = location.elapsedRealtimeNanos / 1_000_000L,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMetres = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            )
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Called only before Android 10.")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = intent?.getStringExtra(EXTRA_TYPE)?.let { ExerciseType.from(it) }
        if (type == null || mutableState.value != null) return START_NOT_STICKY

        val wantsSpeed = type == ExerciseType.RUNNING || type == ExerciseType.CYCLING
        val canMeasure = wantsSpeed && Permissions.canMeasureSpeed(this)
        val measuring = goForeground(type, withLocation = canMeasure)
        if (measuring == null) {
            Toast.makeText(this, "Could not start the session. Allow physical activity access.", Toast.LENGTH_LONG).show()
            stopAfterFailedStart()
            return START_NOT_STICKY
        }
        val startedAt = System.currentTimeMillis()
        mutableState.value = LiveSession(
            type = type,
            startedAt = startedAt,
            measuringSpeed = measuring,
        )
        acquireWakeLock()
        lifecycleScope.launch { TrackerStateRepository(applicationContext).setSessionStartedAt(startedAt) }
        monitor = SessionMonitor(countJumps = type == ExerciseType.SKIPPING)
        registerAccelerometer()
        if (measuring) requestLocation()
        lifecycleScope.launch { tick() }
        // Not sticky: a session restarted by the system has no screen to stop it from.
        return START_NOT_STICKY
    }

    /** Accumulates moving time and publishes the readings until the service stops. */
    private suspend fun tick() {
        // The monotonic clock: a wall-clock change mid-session would otherwise add or remove
        // the jump in time from the session.
        var last = SystemClock.elapsedRealtime()
        var lastNotified = 0L
        while (true) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            val current = mutableState.value ?: return
            val sessionMonitor = monitor ?: return
            // Only time spent moving is added, which is what makes the pause a pause
            // rather than a label over a clock that keeps running.
            val next = current.copy(
                activeMs = current.activeMs + if (sessionMonitor.moving) now - last else 0L,
                moving = sessionMonitor.moving,
                jumps = sessionMonitor.jumps,
                kmh = speed.averageKmh,
                metres = speed.metres,
            )
            last = now
            mutableState.value = next
            if (now - lastNotified >= NOTIFY_MS) {
                lastNotified = now
                notify(next)
            }
        }
    }

    /**
     * Goes foreground for the session. Returns whether location was claimed with it, or null
     * when the service could not go foreground at all.
     */
    private fun goForeground(type: ExerciseType, withLocation: Boolean): Boolean? {
        ensureChannel()
        val placeholder = notification(LiveSession(type, System.currentTimeMillis(), measuringSpeed = withLocation))
        val types = serviceTypes(withLocation)
        // From Android 14 a service declaring types must claim at least one it holds the
        // permission for; with neither permission there is nothing it may run as.
        if (types == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, placeholder, types)
            withLocation
        } catch (e: SecurityException) {
            // Location was revoked between the permission check and here. The session still
            // runs on an assumed effort, which is what a refusal means everywhere else.
            if (!withLocation) {
                Log.w(TAG, "cannot time a session in the foreground", e)
                return null
            }
            Log.w(TAG, "location refused for the session; timing without it", e)
            if (serviceTypes(false) == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, placeholder, serviceTypes(false)) }
                .onFailure { Log.w(TAG, "cannot time a session in the foreground", it) }
                .map { false }
                .getOrNull()
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: only reachable if a start arrived
            // while the app was not in front, which the screen never does.
            Log.w(TAG, "not allowed to go foreground; not starting the session", e)
            null
        }
    }

    /**
     * Ends a start that could not go foreground, without the crash Android raises when a
     * service begun with startForegroundService stops before calling startForeground.
     *
     * From Android 14 the service first goes foreground as a short service, a type that needs
     * no permission, and then stops. The Exercise screen checks permissions before starting,
     * so this is the fallback for a permission taken away in between.
     */
    private fun stopAfterFailedStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification(LiveSession(ExerciseType.SKIPPING, System.currentTimeMillis())),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                )
            }.onFailure { Log.w(TAG, "could not settle the failed start", it) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * The foreground types this session may claim. Android 14 refuses `health` without
     * activity recognition and `location` without a location grant, so each is claimed only
     * when its permission is held.
     */
    private fun serviceTypes(withLocation: Boolean): Int {
        var types = 0
        if (withLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            Permissions.hasActivityRecognition(this)
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        return types
    }

    /**
     * Keeps the CPU awake for the session, without the screen. A foreground service alone
     * does not: the tick loop and the accelerometer both stop while the phone sleeps, and
     * jumps in a locked phone would go uncounted. Bounded, so a session that is never
     * stopped cannot hold the phone awake for good.
     */
    private fun acquireWakeLock() {
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BodyFit:exercise").apply {
            setReferenceCounted(false)
            acquire(MAX_SESSION_MS)
        }
    }

    private fun registerAccelerometer() {
        val sensors = getSystemService(SensorManager::class.java) ?: return
        sensorManager = sensors
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensors.registerListener(this, accelerometer, SAMPLE_US, BATCH_US)
    }

    // Guarded by Permissions.hasLocation and wrapped in runCatching, but lint only
    // recognises a checkSelfPermission written inline at the call site.
    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        locationManager = manager
        runCatching {
            if (Permissions.hasLocation(this)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    FIX_INTERVAL_MS,
                    FIX_DISTANCE_M,
                    locationListener,
                    mainLooper,
                )
            }
        }.onFailure { Log.w(TAG, "could not start GPS for the session", it) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return
        // The time the sample was taken, not delivered: samples arrive in batches, and one
        // arrival time for a batch squeezes its jumps below the minimum gap between them.
        monitor?.onSample(event.timestamp / 1_000_000L, event.values[0], event.values[1], event.values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        runCatching { locationManager?.removeUpdates(locationListener) }
        wakeLock?.takeIf { it.isHeld }?.release()
        // Cleared here as well as in stop(), so a service ended any other way cannot leave a
        // frozen clock on screen or turn away the next start.
        mutableState.value = null
        TrackerStateRepository.clearSessionDetached(applicationContext)
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Exercise in progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a timed exercise is running"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    // Guarded by Permissions.hasNotifications above the call; lint only recognises a check
    // written inline at the call site.
    @SuppressLint("MissingPermission")
    private fun notify(session: LiveSession) {
        if (!Permissions.hasNotifications(this)) return
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(session)) }
    }

    private fun notification(session: LiveSession): Notification {
        val detail = when {
            session.type == ExerciseType.SKIPPING -> "${session.jumps} jumps"
            session.hasSpeed -> String.format(Locale.getDefault(), "%.2f km", session.metres / 1000.0)
            session.measuringSpeed -> "Waiting for a GPS fix"
            else -> "Effort assumed"
        }
        val minutes = session.seconds / 60
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_footsteps)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle("${session.type.emoji} ${session.type.label} in progress")
            .setContentText("$minutes min moving · $detail")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(ActivityNotification.openApp(this))
            .build()
    }

    companion object {
        private const val TAG = "ExerciseSession"
        private const val EXTRA_TYPE = "type"
        private const val CHANNEL_ID = "exercise_session"
        private const val NOTIFICATION_ID = 1002

        /** 25 Hz, enough to resolve a jump without streaming needless samples. */
        private const val SAMPLE_US = 40_000

        /** A quarter second of buffering, which the clock ticks at anyway. */
        private const val BATCH_US = 250_000

        private const val TICK_MS = 250L

        /** Far past any session this app times; the wake lock lets go on its own after it. */
        private const val MAX_SESSION_MS = 6 * 60 * 60 * 1000L

        /** The notification text changes by the minute, so a redraw every few seconds is plenty. */
        private const val NOTIFY_MS = 5_000L

        /** A fix every two seconds is plenty for an average, and far cheaper than the maximum rate. */
        private const val FIX_INTERVAL_MS = 2_000L

        /** No minimum displacement: the filtering that matters is on accuracy, not distance. */
        private const val FIX_DISTANCE_M = 0f

        private val mutableState = MutableStateFlow<LiveSession?>(null)

        /** The running session, or null when none is. Survives the screen that started it. */
        val state: StateFlow<LiveSession?> = mutableState.asStateFlow()

        /**
         * Whether a session of [type] can go foreground now. Android 14 needs activity
         * recognition for the `health` type, or a location grant for running and cycling.
         */
        fun canStart(context: Context, type: ExerciseType): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
            val wantsSpeed = type == ExerciseType.RUNNING || type == ExerciseType.CYCLING
            return Permissions.hasActivityRecognition(context) ||
                (wantsSpeed && Permissions.canMeasureSpeed(context))
        }

        /** Starts timing [type]. Called from the screen, so the app is in front. */
        fun start(context: Context, type: ExerciseType) {
            val intent = Intent(context, ExerciseSessionService::class.java).putExtra(EXTRA_TYPE, type.name)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "could not start the session", it) }
        }

        /** Stops timing and returns the final readings, or null when nothing was running. */
        fun stop(context: Context): LiveSession? {
            val last = mutableState.value
            mutableState.value = null
            context.stopService(Intent(context, ExerciseSessionService::class.java))
            return last
        }
    }
}
