package app.bodyfit

import android.app.Application
import app.bodyfit.data.AutoBackupSettings
import app.bodyfit.data.AutoBackupWorker
import app.bodyfit.notification.ActivityNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BodyFitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Created up front so the channel exists before the first permission prompt.
        ActivityNotification.ensureChannel(this)

        // Re-asserted on every launch. WorkManager keeps its own schedule across reboots,
        // but an app update or a force stop can drop it, and a weekly backup that quietly
        // stopped running is worse than one that never existed.
        CoroutineScope(Dispatchers.IO).launch {
            if (AutoBackupSettings(this@BodyFitApp).targetOnce() != null) {
                AutoBackupWorker.schedule(this@BodyFitApp)
            }
        }
    }
}
