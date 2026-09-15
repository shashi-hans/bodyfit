package app.bodyfit

import android.app.Application
import app.bodyfit.notification.ActivityNotification

class BodyFitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Created up front so the channel exists before the first permission prompt.
        ActivityNotification.ensureChannel(this)
    }
}
