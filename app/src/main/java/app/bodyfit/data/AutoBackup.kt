package app.bodyfit.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.backupStore: DataStore<Preferences> by preferencesDataStore(name = "auto_backup")

/**
 * Where the weekly backup writes, and when it last ran.
 *
 * The destination is a document URI the user picked once. Taking persistable permission on
 * it is what lets a background worker write there days later and after a reboot, without
 * any storage permission and without the app choosing a location of its own.
 */
class AutoBackupSettings(private val context: Context) {

    private object Keys {
        val TARGET = stringPreferencesKey("target_uri")
        val LAST_RUN = longPreferencesKey("last_run")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }

    val target: Flow<Uri?> = context.backupStore.data.map { prefs ->
        prefs[Keys.TARGET]?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    val lastRun: Flow<Long> = context.backupStore.data.map { it[Keys.LAST_RUN] ?: 0L }

    val lastError: Flow<String?> = context.backupStore.data.map { it[Keys.LAST_ERROR] }

    suspend fun targetOnce(): Uri? = target.first()

    /**
     * Remembers where to write and holds on to the right to do so.
     *
     * Without [takePersistableUriPermission] the grant dies with the process and the first
     * scheduled run a day later fails with a security error nobody is present to see.
     */
    suspend fun setTarget(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        context.backupStore.edit {
            it[Keys.TARGET] = uri.toString()
            it[Keys.LAST_ERROR] = ""
        }
    }

    suspend fun clearTarget() {
        context.backupStore.edit {
            it[Keys.TARGET] = ""
            it[Keys.LAST_ERROR] = ""
        }
    }

    suspend fun recordRun(at: Long, error: String?) {
        context.backupStore.edit {
            if (error == null) it[Keys.LAST_RUN] = at
            it[Keys.LAST_ERROR] = error ?: ""
        }
    }
}

/**
 * Writes the whole history over the chosen file, once a week.
 *
 * The same file is overwritten rather than a new one written each time, so the backup does
 * not quietly fill a drive with 52 copies a year. The cost is that a corrupted write loses
 * the previous copy too, which is why the file is only replaced once the new content has
 * been serialised in full.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AutoBackupSettings(applicationContext)
        val target = settings.targetOnce() ?: return Result.success()

        return try {
            val json = HealthRepository(applicationContext).backupJson()
            // "wt" truncates first. Without it a shorter backup would leave the tail of the
            // previous one behind and produce a file that is not valid JSON.
            applicationContext.contentResolver.openOutputStream(target, "wt")?.use {
                it.write(json.toByteArray())
            } ?: error("could not open the backup file for writing")
            settings.recordRun(System.currentTimeMillis(), error = null)
            Result.success()
        } catch (e: SecurityException) {
            // The user revoked access, or moved or deleted the file. Retrying cannot fix
            // either, so the failure is recorded for the backup page to show.
            settings.recordRun(System.currentTimeMillis(), error = "No longer allowed to write there")
            Result.failure()
        } catch (e: Exception) {
            settings.recordRun(System.currentTimeMillis(), error = e.message ?: "Backup failed")
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "weekly-backup"

        /**
         * Schedules the weekly write, replacing any existing schedule.
         *
         * Requires the battery not to be critically low: a backup is worth postponing by a
         * few hours and is not worth the last of a flat battery.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
