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

/**
 * Undated, because the scheduled backup rewrites one file rather than adding to a pile.
 *
 * The cadence is not in the name either. It was once, and moving from weekly to daily then
 * meant either a lie in the filename or an orphaned file on every phone.
 */
private const val DEFAULT_FILE_NAME = "bodyfit-backup.json"

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
 * Where the daily backup writes, and when it last ran.
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

        /**
         * The MediaStore row the default backup was written to.
         *
         * Held so every later write goes to the same row rather than asking MediaStore to
         * find it again. A lookup that misses ends in an insert, and an insert of a name
         * that already exists is answered with "bodyfit-backup (1).json".
         */
        val DEFAULT_URI = stringPreferencesKey("default_uri")
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
     * repeated insert with `bodyfit-backup (1).json` and the user would end up with a
     * year of them. Android 9 and older take the app's own external folder instead, for
     * the reason given at that branch.
     */
    suspend fun writeDefault(json: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            // Three ways to reach the same row, cheapest first: the one written last time,
            // then a lookup by name, then a fresh insert. Only the third can produce a new
            // file, and it records what it made so the next write takes the first path.
            val remembered = rememberedDefaultUri()
            val uri = remembered?.takeIf { stillExists(it) }
                ?: existingDefaultUri(collection)
                ?: insertDefault(resolver, collection)

            // "wt" truncates, so a shorter backup cannot leave the tail of the last one behind.
            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                ?: error("could not open the backup file for writing")
            if (uri != remembered) rememberDefaultUri(uri)
        } else {
            // Android 9 and older have no Downloads collection, and writing to the public
            // folder there needs WRITE_EXTERNAL_STORAGE, which this app does not ask for:
            // one storage prompt on every install, to serve a shrinking minority, is a
            // worse trade than a backup that lives inside the app on those phones. It is
            // lost on uninstall there, which is why the page offers a file of your own.
            val dir = context.getExternalFilesDir(DEFAULT_DIR_NAME)
                ?: File(context.filesDir, DEFAULT_DIR_NAME)
            dir.mkdirs()
            File(dir, DEFAULT_FILE_NAME).writeText(json)
        }
    }

    private suspend fun rememberedDefaultUri(): Uri? =
        context.backupStore.data.first()[Keys.DEFAULT_URI]
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

    private suspend fun rememberDefaultUri(uri: Uri) {
        context.backupStore.edit { it[Keys.DEFAULT_URI] = uri.toString() }
    }

    /**
     * Whether the remembered row is still there and still ours.
     *
     * A query rather than opening the file for writing: MediaStore truncates on a "w" open,
     * so a liveness check done that way would empty the backup it was checking, and a
     * process killed in the moment between would leave nothing to restore from.
     */
    private fun stillExists(uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.Downloads._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    /**
     * Creates the file, then removes any earlier copy this app made under a numbered name.
     *
     * MediaStore answers an insert of a name that already exists by inventing
     * "bodyfit-backup (1).json" rather than failing, so a reinstall that cannot see its own
     * previous row leaves one behind. Deleting the ones this app owns keeps that to the
     * single file the user is promised; a copy owned by a previous install of the app is
     * beyond reach, because reading another owner's row needs All files access.
     */
    private fun insertDefault(resolver: android.content.ContentResolver, collection: Uri): Uri {
        val created = resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, DEFAULT_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
            },
        ) ?: error("could not create $DEFAULT_RELATIVE_PATH/$DEFAULT_FILE_NAME")
        deleteOurOtherCopies(collection, keep = created)
        return created
    }

    /**
     * Removes the numbered copies MediaStore made of the default file, and nothing else.
     *
     * Matched by the exact default name and by "bodyfit-backup (2).json" and its like,
     * never by a prefix. A dated export the user saved by hand is called
     * "bodyfit-backup-2026-09-27.json", so a pattern of "bodyfit-backup%" would sweep up
     * the very file someone kept deliberately.
     */
    private fun deleteOurOtherCopies(collection: Uri, keep: Uri) {
        val keepId = ContentUris.parseId(keep)
        val name = MediaStore.Downloads.DISPLAY_NAME
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ($name = ? OR $name LIKE ?)",
                arrayOf("$DEFAULT_RELATIVE_PATH%", DEFAULT_FILE_NAME, "bodyfit-backup (%).json"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    if (id == keepId) continue
                    context.contentResolver.delete(ContentUris.withAppendedId(collection, id), null, null)
                }
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DOWNLOADS} / $DEFAULT_DIR_NAME / $DEFAULT_FILE_NAME"
        } else {
            // Android 9 and older keep it inside the app, so saying Download would be a lie.
            "App folder / $DEFAULT_DIR_NAME / $DEFAULT_FILE_NAME"
        }

    suspend fun recordRun(at: Long, error: String?) {
        context.backupStore.edit {
            if (error == null) it[Keys.LAST_RUN] = at
            it[Keys.LAST_ERROR] = error ?: ""
        }
    }
}

/**
 * Writes the whole history over the chosen file, once a day.
 *
 * The same file is overwritten rather than a new one written each time, so the backup does
 * not quietly fill a drive with 365 copies a year. The cost is that a corrupted write loses
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
            val repository = HealthRepository(applicationContext)

            // An empty database still serialises to a valid backup, and writing that over
            // the file is how the last good copy dies: clear the app's data by accident,
            // open it once, and the daily write replaces a year of history with nothing.
            // Backups protect against exactly this, so the writer refuses rather than
            // overwrites, and the page says why.
            if (!repository.hasAnythingToBackUp()) {
                settings.recordRun(
                    System.currentTimeMillis(),
                    error = "Nothing recorded yet, so the last backup was left alone",
                )
                return Result.success()
            }

            val json = repository.backupJson()
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
        private const val NAME = "daily-backup"

        /** Superseded by [NAME]. Cancelled once so a phone is not left with both. */
        private const val LEGACY_NAME = "weekly-backup"

        /**
         * Schedules the daily write, replacing any existing schedule.
         *
         * Requires the battery not to be critically low: a backup is worth postponing by a
         * few hours and is not worth the last of a flat battery.
         *
         * Daily rather than weekly because the file is the only copy: nothing syncs, so the
         * gap between the last backup and a lost phone is the history that is gone. One
         * rewrite of one file a day costs nothing measurable and cuts that gap from seven
         * days to one.
         */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_NAME)
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
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
