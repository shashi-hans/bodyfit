package app.bodyfit.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bodyfit.data.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the tracker after a reboot or an app update.
 *
 * The hardware counter restarts at zero on boot, and [StepTrackerService] detects
 * that from the first reading, so no steps taken before the service starts are lost.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (UserSettingsRepository(appContext).current().trackerEnabled) {
                    StepTrackerService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
