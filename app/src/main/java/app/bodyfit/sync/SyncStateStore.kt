package app.bodyfit.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** The two sync watermarks. Deliberately not in the database: they are not user data. */
interface SyncStateStore {
    suspend fun pullCursor(): String?
    suspend fun setPullCursor(cursor: String)

    /** Highest local `updatedAt` the server has accepted. */
    suspend fun pushedThrough(): Long
    suspend fun setPushedThrough(value: Long)
}

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

class DataStoreSyncState(private val context: Context) : SyncStateStore {

    private object Keys {
        val CURSOR = stringPreferencesKey("pull_cursor")
        val PUSHED_THROUGH = longPreferencesKey("pushed_through")
    }

    override suspend fun pullCursor(): String? =
        context.syncStore.data.first()[Keys.CURSOR]

    override suspend fun setPullCursor(cursor: String) {
        context.syncStore.edit { it[Keys.CURSOR] = cursor }
    }

    override suspend fun pushedThrough(): Long =
        context.syncStore.data.first()[Keys.PUSHED_THROUGH] ?: 0L

    override suspend fun setPushedThrough(value: Long) {
        context.syncStore.edit { it[Keys.PUSHED_THROUGH] = value }
    }
}

/** Non-persistent store, used by the sync tests and by a signed-out session. */
class InMemorySyncState : SyncStateStore {
    private var cursor: String? = null
    private var pushed = 0L

    override suspend fun pullCursor(): String? = cursor
    override suspend fun setPullCursor(cursor: String) { this.cursor = cursor }
    override suspend fun pushedThrough(): Long = pushed
    override suspend fun setPushedThrough(value: Long) { pushed = value }
}
