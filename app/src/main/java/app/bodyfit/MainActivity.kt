package app.bodyfit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.bodyfit.data.Backup
import app.bodyfit.data.HealthRepository
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettingsRepository
import app.bodyfit.sensor.Permissions
import app.bodyfit.sensor.StepTrackerService
import app.bodyfit.ui.BodyFitAppScreen
import app.bodyfit.ui.screens.BackgroundAccessScreen
import app.bodyfit.ui.screens.NoSensorScreen
import app.bodyfit.ui.screens.SetupScreen
import app.bodyfit.ui.theme.BodyFitTheme
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private var activityPermissionGranted by mutableStateOf(false)

    /**
     * Whether the first-run questions have been answered, or null while that is being read.
     *
     * Null draws nothing at all. A frame of the app before the flag arrives would be a
     * flash of somebody else's numbers, and a frame of the setup screen would be an
     * insult to a user who answered it months ago.
     */
    private var setupComplete by mutableStateOf<Boolean?>(null)

    /**
     * Whether the background-access step has been shown, or null while that is being read.
     *
     * Re-read on every resume, because the user leaves for the system battery page and
     * comes back: granting the exemption there should carry them into the app rather than
     * leaving them looking at the question they have just answered.
     */
    private var backgroundPromptSeen by mutableStateOf<Boolean?>(null)

    /**
     * Whether the system prompt has been through once and been answered.
     *
     * The warning dialog waits for this. Without it the dialog opens in the same frame as
     * the system prompt, so the user reads "nothing is being counted" over the top of the
     * very dialog that would fix it, and `shouldShowRequestPermissionRationale` is false
     * before the first ask, so a fresh install is told Android has stopped asking.
     */
    private var permissionsAnswered by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsAnswered = true
        activityPermissionGranted = Permissions.hasActivityRecognition(this)
        startTrackerIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activityPermissionGranted = Permissions.hasActivityRecognition(this)

        // Every number the app reports is counted from motion, by the step counter where
        // there is one and from the accelerometer where there is not. A phone with neither
        // is shown the warning instead of the app rather than a working-looking screen of
        // zeros. Checked before anything else, including the permission prompt: asking for
        // access to a sensor that is not fitted would only confuse.
        val hasStepCounter = Permissions.canCountSteps(this)

        val settings = UserSettingsRepository(applicationContext)
        // The permission prompt waits for the answers. Asking to read the step counter over
        // a screen that has not yet said why the app wants it is how a refusal is earned.
        lifecycleScope.launch {
            val current = settings.current()
            setupComplete = current.setupComplete
            backgroundPromptSeen = current.backgroundPromptSeen
            if (hasStepCounter && setupComplete == true) {
                if (!activityPermissionGranted) requestPermissions() else startTrackerIfAllowed()
            }
        }

        setContent {
            BodyFitTheme {
                when {
                    !hasStepCounter -> NoSensorScreen(onClose = { finish() })
                    setupComplete == false -> SetupScreen(
                        onDone = { name, height, weight, age, sex ->
                            lifecycleScope.launch {
                                settings.completeSetup(name, height, weight, age, sex)
                                setupComplete = true
                                // Counting begins the moment the answers land, so the first
                                // figures the user sees are already their own.
                                if (!activityPermissionGranted) requestPermissions()
                                else startTrackerIfAllowed()
                            }
                        },
                        onRestore = { uri -> restoreAndOpen(uri) },
                    )
                    // Asked after the permission prompts, and only while the phone is
                    // still free to freeze the app. Granting it elsewhere skips the step.
                    setupComplete == true && backgroundPromptSeen == false && !isExemptFromBatteryOptimisation() ->
                        BackgroundAccessScreen(
                            onOpenSettings = {
                                markBackgroundPromptSeen()
                                runCatching {
                                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure {
                                    Log.w(TAG, "no battery optimisation settings on this phone", it)
                                }
                            },
                            onSkip = ::markBackgroundPromptSeen,
                        )

                    setupComplete == true -> BodyFitAppScreen(
                        activityPermissionGranted = activityPermissionGranted,
                        permissionsAnswered = permissionsAnswered,
                        onRequestPermissions = ::requestPermissions,
                    )
                }
            }
        }

    }

    /** Whether the phone has already been told to leave this app running. */
    private fun isExemptFromBatteryOptimisation(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: false

    private fun markBackgroundPromptSeen() {
        backgroundPromptSeen = true
        lifecycleScope.launch {
            UserSettingsRepository(applicationContext).markBackgroundPromptSeen()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Permissions.canCountSteps(this)) return
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
            permissionsAnswered = true
            activityPermissionGranted = Permissions.hasActivityRecognition(this)
            startTrackerIfAllowed()
            return
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    /**
     * Restores a backup file and treats it as the answers to the setup questions.
     *
     * A backup carries the same height, weight, age and sex the screen asks for, so a user
     * moving from another phone answers them by restoring rather than by typing them a
     * second time and hoping they match what the file will overwrite.
     *
     * Only a restore that actually parsed opens the app. A file that is not a backup leaves
     * the setup screen where it was, with the reason under the button.
     */
    private suspend fun restoreAndOpen(uri: Uri): Result<Int> = runCatching {
        val repository = HealthRepository(applicationContext)
        val days = repository.restoreJson(Backup.read(applicationContext, uri))
        UserSettingsRepository(applicationContext).markSetupComplete()
        setupComplete = true
        if (!activityPermissionGranted) requestPermissions() else startTrackerIfAllowed()
        days
    }

    /**
     * Starts the tracker only when the user has allowed it, left it switched on, and
     * answered the first-run questions.
     *
     * The setup check is here rather than only at the call sites because `onResume` runs
     * on every return to the app, including a return from the permission dialog that the
     * setup screen itself is still behind.
     */
    private fun startTrackerIfAllowed() {
        if (!activityPermissionGranted) return
        lifecycleScope.launch {
            val current = UserSettingsRepository(applicationContext).current()
            if (current.setupComplete && current.trackerEnabled) {
                StepTrackerService.start(this@MainActivity)
            }
        }
    }

}
