package app.bodyfit.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The two runtime permissions the tracker needs, and the versions that actually ask for them.
 *
 * Activity recognition became a runtime permission in Android 10; before that the step
 * counter is readable without asking. Notification permission arrived in Android 13.
 * The permission name constants are compile-time strings, so referring to them on older
 * releases is safe.
 *
 * Also answers whether the step sensor exists at all, which no permission can grant.
 */
@SuppressLint("InlinedApi")
object Permissions {

    /**
     * Whether this phone has the hardware step counter the whole app is built on.
     *
     * The sensor is optional in Android: budget phones, many tablets and most emulators
     * ship without it. Asked of [SensorManager] rather than the `PackageManager` feature
     * flag, because a device can declare the feature and still hand back no sensor.
     */
    fun hasStepCounter(context: Context): Boolean = sensor(context, Sensor.TYPE_STEP_COUNTER)

    /**
     * Whether steps can be counted at all, by either route.
     *
     * The step counter is preferred and the accelerometer is the fallback. Only a phone
     * with neither can do nothing, which in practice means almost none: an accelerometer
     * is fitted to essentially every Android phone.
     */
    fun canCountSteps(context: Context): Boolean =
        hasStepCounter(context) || sensor(context, Sensor.TYPE_ACCELEROMETER)

    private fun sensor(context: Context, type: Int): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) != null

    /**
     * Whether this phone has a GNSS receiver at all.
     *
     * Asked before the permission, because prompting for access to hardware that is not
     * fitted would be a question with no useful answer.
     */
    fun hasGps(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    fun hasLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** True when a session can actually measure its speed rather than assume its effort. */
    fun canMeasureSpeed(context: Context): Boolean = hasGps(context) && hasLocation(context)

    fun hasActivityRecognition(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(context, Manifest.permission.POST_NOTIFICATIONS)

    /** The permissions still worth asking for on this device, empty when nothing is missing. */
    fun missing(context: Context): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !granted(context, Manifest.permission.ACTIVITY_RECOGNITION)
        ) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
