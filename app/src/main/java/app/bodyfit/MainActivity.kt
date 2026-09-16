package app.bodyfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bodyfit.data.UserSettingsRepository
import app.bodyfit.sensor.Permissions
import app.bodyfit.sensor.StepTrackerService
import app.bodyfit.ui.BodyFitAppScreen
import app.bodyfit.ui.screens.NoSensorScreen
import app.bodyfit.ui.theme.BodyFitTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private var activityPermissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        activityPermissionGranted = Permissions.hasActivityRecognition(this)
        startTrackerIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activityPermissionGranted = Permissions.hasActivityRecognition(this)

        // Every number the app reports comes from the step counter, so a phone without one
        // is shown the warning instead of the app rather than a working-looking screen of
        // zeros. Checked before anything else, including the permission prompt: asking for
        // access to a sensor that is not fitted would only confuse.
        val hasStepCounter = Permissions.hasStepCounter(this)

        setContent {
            BodyFitTheme {
                if (hasStepCounter) {
                    BodyFitAppScreen(
                        activityPermissionGranted = activityPermissionGranted,
                        onRequestPermissions = ::requestPermissions,
                    )
                } else {
                    NoSensorScreen(onClose = { finish() })
                }
            }
        }

        if (!hasStepCounter) return
        if (!activityPermissionGranted) requestPermissions() else startTrackerIfAllowed()
    }

    override fun onResume() {
        super.onResume()
        if (!Permissions.hasStepCounter(this)) return
        // The day-rollover check lives with the view model, driven by a lifecycle observer
        // in the composable. Reaching for the view model from here would depend on the
        // activity and the composable resolving the same instance, which is true today but
        // is not something the activity should rely on.
        activityPermissionGranted = Permissions.hasActivityRecognition(this)
        startTrackerIfAllowed()
    }

    private fun requestPermissions() {
        val wanted = Permissions.missing(this)
        if (wanted.isEmpty()) {
            activityPermissionGranted = Permissions.hasActivityRecognition(this)
            startTrackerIfAllowed()
            return
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    /** Starts the tracker only when the user has both allowed it and left it switched on. */
    private fun startTrackerIfAllowed() {
        if (!activityPermissionGranted) return
        lifecycleScope.launch {
            if (UserSettingsRepository(applicationContext).current().trackerEnabled) {
                StepTrackerService.start(this@MainActivity)
            }
        }
    }

}
