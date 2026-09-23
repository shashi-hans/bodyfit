package app.bodyfit.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.backupStore: DataStore<Preferences> by preferencesDataStore(name = "auto_backup")

/** Undated, because the weekly backup rewrites one file rather than adding to a pile. */
private const val DEFAULT_FILE_NAME = "bodyfit-weekly-backup.json"

/** Its own folder, so a file manager shows the backup apart from anything else the app keeps. */
private const val DEFAULT_DIR_NAME = "backup"

/**
 * The folder the default backup sits in, as MediaStore spells it.
 *
 * Android 10 onwards an app cannot create a folder at the root of shared storage; only the
 * standard collections are writable without All files access, which Play grants to file
 * managers and little else. Downloads is the one of those meant for a file the user keeps,
 * so `backup` is a folder inside it.
 */
private val DEFAULT_RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_DIR_NAME"

/**
 * Where the weekly backup writes, and when it last ran.
 *
 * With nothing chosen it writes to `Download/backup` in shared storage, which needs no
 * permission and no prompt on Android 10 and later, so the backup exists from the moment the
 * app is installed. It sits outside the app, so uninstalling or clearing the app leaves it
 * in place and a file manager can copy it off the phone.
 *
 * The cost of being outside the app is that any app the user grants storage access to can
 * read it, and it carries the whole history. The page says so.
 *
 * A chosen destination is a document URI instead. Taking persistable permission on it is
 * what lets a background worker write there days later and after a reboot, without any
 * storage permission.
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
     * Writes [json] over the default file in `Download/backup`, creating it the first time.
     *
     * The same row is reused rather than a second file inserted, because MediaStore answers a
     * repeated insert with `bodyfit-weekly-backup (1).json` and the user would end up with a
     * year of them. On Android 9 and older there is no Downloads collection, so the write
     * goes straight to the folder; it needs no permission there either, because everything
     * below API 29 still has legacy storage for its own inserts into public directories.
     */
    fun writeDefault(json: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = existingDefaultUri(collection) ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, DEFAULT_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
                },
            ) ?: error("could not create $DEFAULT_RELATIVE_PATH/$DEFAULT_FILE_NAME")
            // "wt" truncates, so a shorter backup cannot leave the tail of the last one behind.
            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                ?: error("could not open the backup file for writing")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DEFAULT_DIR_NAME,
            )
            dir.mkdirs()
            File(dir, DEFAULT_FILE_NAME).writeText(json)
        }
    }

    /** The row for a backup already written, so it is overwritten rather than duplicated. */
    private fun existingDefaultUri(collection: Uri): Uri? =
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf("$DEFAULT_RELATIVE_PATH%", DEFAULT_FILE_NAME),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            } else {
                null
            }
        }

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

    /** A path worth showing: the full one is long and says nothing useful. */
    fun defaultLocationLabel(): String =
        "${Environment.DIRECTORY_DOWNLOADS} / $DEFAULT_DIR_NAME / $DEFAULT_FILE_NAME"

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
        val target = settings.targetOnce()

        return try {
            val json = HealthRepository(applicationContext).backupJson()
            if (target == null) {
                settings.writeDefault(json)
            } else {
                // "wt" truncates first. Without it a shorter backup would leave the tail of
                // the previous one behind and produce a file that is not valid JSON.
                applicationContext.contentResolver.openOutputStream(target, "wt")?.use {
                    it.write(json.toByteArray())
                } ?: error("could not open the backup file for writing")
            }
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
