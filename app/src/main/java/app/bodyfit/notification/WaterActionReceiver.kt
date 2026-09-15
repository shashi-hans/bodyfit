package app.bodyfit.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bodyfit.data.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the water buttons on the lock-screen card. The tracker service is
 * watching today's row, so it redraws the card as soon as the write lands.
 */
class WaterActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_WATER) return
        val amountMl = intent.getIntExtra(EXTRA_AMOUNT_ML, 0)
        if (amountMl <= 0) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HealthRepository(appContext).logWater(amountMl)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD_WATER = "app.bodyfit.ADD_WATER"
        const val EXTRA_AMOUNT_ML = "amount_ml"
    }
}
