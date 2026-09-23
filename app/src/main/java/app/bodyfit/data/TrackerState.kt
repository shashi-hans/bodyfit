package app.bodyfit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trackerStore: DataStore<Preferences> by preferencesDataStore(name = "tracker_state")

/**
 * Baseline for the hardware step counter, and whether a timed exercise is running.
 *
 * `TYPE_STEP_COUNTER` reports steps since the device booted, so the tracker keeps
 * the last reading and banks the difference. A reading lower than the last one
 * means the device rebooted and the whole reading is the new delta.
 *
 * The exercise flag is here rather than in the database because the tracker service reads
 * it every five seconds and must not be waiting on a query to decide whether to score.
 */
class TrackerStateRepository(private val context: Context) {

    private object Keys {
        val LAST_RAW_COUNT = longPreferencesKey("last_raw_count")
        val SESSION_STARTED_AT = longPreferencesKey("session_started_at")
    }

    /**
     * Whether a timed exercise is in progress, which suppresses the tracker's own scoring.
     *
     * A run produces steps, and those steps would otherwise earn calories, move minutes
     * and heart points on top of the session's own figures for the same minutes. While a
     * session is running the session owns the scoring; steps are still counted, because
     * the step total should reflect the run.
     */
    val sessionActive: Flow<Boolean> =
        context.trackerStore.data.map { (it[Keys.SESSION_STARTED_AT] ?: 0L) > 0L }

    suspend fun setSessionStartedAt(value: Long) {
        context.trackerStore.edit { it[Keys.SESSION_STARTED_AT] = value }
    }

    suspend fun sessionStartedAt(): Long =
        context.trackerStore.data.map { it[Keys.SESSION_STARTED_AT] ?: 0L }.first()

    /** Last raw sensor reading, or -1 when the tracker has not seen the sensor yet. */
    suspend fun lastRawCount(): Long =
        context.trackerStore.data.map { it[Keys.LAST_RAW_COUNT] ?: -1L }.first()

    suspend fun setLastRawCount(value: Long) {
        context.trackerStore.edit { it[Keys.LAST_RAW_COUNT] = value }
    }
}
