package app.bodyfit

import android.app.Application
import app.bodyfit.data.AutoBackupWorker
import app.bodyfit.notification.ActivityNotification

class BodyFitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Created up front so the channel exists before the first permission prompt.
        ActivityNotification.ensureChannel(this)

        // Re-asserted on every launch. WorkManager keeps its own schedule across reboots,
        // but an app update or a force stop can drop it, and a weekly backup that quietly
        // stopped running is worse than one that never existed.
        // Scheduled unconditionally: the weekly backup is on from install, writing to the
        // app's own folder until the user picks somewhere else. Re-asserted on every launch
        // because an update or a force stop can drop the schedule, and a weekly backup that
        // quietly stopped running is worse than one that never existed.
        AutoBackupWorker.schedule(this)
    }
}
