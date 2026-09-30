package app.bodyfit

import android.app.Application
import app.bodyfit.data.AutoBackupWorker
import app.bodyfit.data.UserSettingsRepository
import app.bodyfit.notification.ActivityNotification
import app.bodyfit.notification.WaterReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BodyFitApp : Application() {

    /** Lives as long as the process; for start-up work that must not block onCreate. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Created up front so the channel exists before the first permission prompt.
        ActivityNotification.ensureChannel(this)

        // Scheduled unconditionally: the daily backup is on from install, writing to
        // Download/backup until the user picks somewhere else. Re-asserted on every launch
        // because an update or a force stop can drop a schedule, and a backup that quietly
        // stopped running is worse than one that never existed.
        AutoBackupWorker.schedule(this)

        // The drink reminder is re-asserted for the same reason, from the saved settings.
        appScope.launch {
            WaterReminder.apply(this@BodyFitApp, UserSettingsRepository(this@BodyFitApp).current())
        }
    }
}
