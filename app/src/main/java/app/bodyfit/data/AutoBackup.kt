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

private val DEFAULT_FILE_NAME = Backup.autoBackupFileName()

/**
 * The name a new backup is written under before it replaces the old one.
 *
 * Shaped like MediaStore's own numbered copies, so the sweep for stray copies also clears
 * one left behind by a process killed between the write and the rename.
 */
private const val PENDING_FILE_NAME = "bodyfit-backup (writing).json"

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
 * Matches the backup folder exactly. A prefix match would also take in a folder such as
 * `Download/backup-old`, and the sweep for stray copies would then delete files there.
 * MediaStore stores the path with a trailing slash; both spellings are accepted.
 */
private val IN_BACKUP_FOLDER =
    "(${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} = ?)"
private val BACKUP_FOLDER_ARGS = arrayOf("$DEFAULT_RELATIVE_PATH/", DEFAULT_RELATIVE_PATH)

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
     * The old file is never opened for writing. The new content goes into a pending row
     * first, and only once it is complete is the old row deleted and the new one given the
     * default name. A full disk or a killed process mid-write therefore leaves the previous
     * backup whole, which matters because it is the user's only copy. Android 9 and older
     * take the app's own external folder instead, for the reason given at that branch.
     */
    suspend fun writeDefault(json: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val old = rememberedDefaultUri()?.takeIf { stillExists(it) } ?: existingDefaultUri(collection)
            val fresh = insertPending(resolver, collection)
            try {
                resolver.openOutputStream(fresh, "w")?.use { it.write(json.toByteArray()) }
                    ?: error("could not open the backup file for writing")
                resolver.update(fresh, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(fresh, null, null) }
                throw e
            }
            // From here a complete copy exists under the pending name, so losing the process
            // costs at most a file with the wrong name, never the backup itself.
            if (old != null) resolver.delete(old, null, null)
            runCatching {
                resolver.update(
                    fresh,
                    ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, DEFAULT_FILE_NAME) },
                    null,
                    null,
                )
            }
            rememberDefaultUri(fresh)
            deleteOurOtherCopies(collection, keep = fresh)
        } else {
            // Android 9 and older have no Downloads collection, and writing to the public
            // folder there needs WRITE_EXTERNAL_STORAGE, which this app does not ask for:
            // one storage prompt on every install, to serve a shrinking minority, is a
            // worse trade than a backup that lives inside the app on those phones. It is
            // lost on uninstall there, which is why the page offers a file of your own.
            val dir = context.getExternalFilesDir(DEFAULT_DIR_NAME)
                ?: File(context.filesDir, DEFAULT_DIR_NAME)
            dir.mkdirs()
            // Written beside the old file and renamed over it, so a failed write leaves the
            // previous backup in place.
            val pending = File(dir, PENDING_FILE_NAME)
            pending.writeText(json)
            if (!pending.renameTo(File(dir, DEFAULT_FILE_NAME))) {
                pending.delete()
                error("could not replace the backup file")
            }
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
     * Creates the row the next backup is written into, hidden from other apps until done.
     *
     * A pending row that is never finished is removed by MediaStore on its own after a
     * week, so an interrupted write cannot leave a half file where a user would find it.
     */
    private fun insertPending(resolver: android.content.ContentResolver, collection: Uri): Uri =
        resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, PENDING_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, Backup.MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            },
        ) ?: error("could not create $DEFAULT_RELATIVE_PATH/$PENDING_FILE_NAME")

    /**
     * Removes the numbered copies MediaStore made of the default file, and nothing else.
     *
     * MediaStore answers an insert or rename to a name that already exists by inventing
     * "bodyfit-backup (1).json" rather than failing. Deleting the ones this app owns keeps
     * that to the single file the user is promised; a copy owned by a previous install of
     * the app is beyond reach, because reading another owner's row needs All files access.
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
                "$IN_BACKUP_FOLDER AND ($name = ? OR $name LIKE ?)",
                arrayOf(*BACKUP_FOLDER_ARGS, DEFAULT_FILE_NAME, "bodyfit-backup (%).json"),
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
            "$IN_BACKUP_FOLDER AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(*BACKUP_FOLDER_ARGS, DEFAULT_FILE_NAME),
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
                Backup.write(applicationContext, target, json)
            }
            settings.recordRun(System.currentTimeMillis(), error = null)
            Result.success()
        } catch (e: SecurityException) {
            // The user revoked access. Retrying cannot fix that, so the failure is recorded
            // for the backup page to show.
            settings.recordRun(System.currentTimeMillis(), error = "No longer allowed to write there")
            Result.failure()
        } catch (e: java.io.FileNotFoundException) {
            // For a file the user chose, it was moved or deleted, and retrying with backoff
            // would fail the same way forever. For the default location the same exception
            // can mean storage is briefly unavailable, which a retry does fix.
            settings.recordRun(
                System.currentTimeMillis(),
                error = if (target != null) "The backup file is no longer there" else e.message ?: "Backup failed",
            )
            if (target != null) Result.failure() else Result.retry()
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
