package app.bodyfit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trackerStore: DataStore<Preferences> by preferencesDataStore(name = "tracker_state")

/**
 * Baseline for the hardware step counter.
 *
 * `TYPE_STEP_COUNTER` reports steps since the device booted, so the tracker keeps
 * the last reading and banks the difference. A reading lower than the last one
 * means the device rebooted and the whole reading is the new delta.
 */
class TrackerStateRepository(private val context: Context) {

    private object Keys {
        val LAST_RAW_COUNT = longPreferencesKey("last_raw_count")
    }

    /** Last raw sensor reading, or -1 when the tracker has not seen the sensor yet. */
    suspend fun lastRawCount(): Long =
        context.trackerStore.data.map { it[Keys.LAST_RAW_COUNT] ?: -1L }.first()

    suspend fun setLastRawCount(value: Long) {
        context.trackerStore.edit { it[Keys.LAST_RAW_COUNT] = value }
    }
}
