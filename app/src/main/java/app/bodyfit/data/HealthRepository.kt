package app.bodyfit.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Single entry point to the on-device store. Screens, the tracker service and the
 * notification all read and write through this class so there is one copy of the
 * day-rollover and water-total rules.
 */
class HealthRepository(context: Context) {

    private val dao = HealthDatabase.get(context).healthDao()
    private val trackerState = TrackerStateRepository(context.applicationContext)

    /** Goal and body-measurement store, used directly by the goals screen for writes. */
    val userSettings = UserSettingsRepository(context.applicationContext)

    val settings: Flow<UserSettings> = userSettings.settings

    suspend fun currentSettings(): UserSettings = userSettings.current()

    fun observeDay(date: String): Flow<DailyRecord> =
        dao.observeDay(date).map { it ?: DailyRecord(date = date) }

    /** The seven days of the week containing [anchor], Monday first, gaps filled with empty rows. */
    fun observeWeek(anchor: LocalDate = LocalDate.now()): Flow<List<DailyRecord>> {
        val keys = Dates.weekKeys(anchor)
        return dao.observeRange(keys.first(), keys.last()).map { rows ->
            val byDate = rows.associateBy { it.date }
            keys.map { byDate[it] ?: DailyRecord(date = it) }
        }
    }

    fun observeWaterEntries(date: String = Dates.today()): Flow<List<WaterEntry>> =
        dao.observeWaterEntries(date)

    /** Every recorded day. Streaks and the health score read the whole history. */
    fun observeAllDays(): Flow<List<DailyRecord>> = dao.observeAllDays()

    suspend fun allDays(): List<DailyRecord> = dao.allDaysOnce()

    /** Snapshot for the backup writer. */
    suspend fun backupJson(): String = Backup.toJson(
        days = allDays(),
        hours = dao.allHoursOnce(),
        water = dao.allWaterEntries(),
        settings = currentSettings(),
    )

    /**
     * Writes a backup file back into the database and the settings store.
     *
     * Returns the number of days restored. Throws [IllegalArgumentException] with a message
     * worth showing the user when the file is not a backup this build can read.
     *
     * A version 1 file carries no hourly rows. Restoring one leaves its days without a
     * breakdown, which the trends screen already draws as an empty day rather than a gap.
     */
    suspend fun restoreJson(json: String): Int {
        val snapshot = Backup.fromJson(json, currentSettings())
        dao.restore(snapshot.days, snapshot.hours, snapshot.water)
        userSettings.replace(snapshot.settings)
        return snapshot.days.size
    }

    /**
     * The 24 hourly rows of [date], gaps filled, so a chart draws a full day whether or
     * not the tracker was running for all of it.
     */
    fun observeHours(date: String): Flow<List<HourlyRecord>> =
        dao.observeHours(date).map { rows ->
            val byHour = rows.associateBy { it.hour }
            (0..23).map { byHour[it] ?: HourlyRecord(date = date, hour = it) }
        }

    suspend fun recordActivity(
        date: String,
        hour: Int,
        steps: Int,
        moveMinutes: Int,
        heartPoints: Int,
        kcal: Double,
    ) {
        if (steps == 0 && moveMinutes == 0 && heartPoints == 0 && kcal == 0.0) return
        dao.addActivity(date, hour, steps, moveMinutes, heartPoints, kcal)
    }

    /** Drops hourly detail the trends screen can no longer reach. Called on day rollover. */
    suspend fun pruneHourly(today: LocalDate = LocalDate.now()) {
        dao.pruneHoursBefore(today.minusDays(HOURLY_RETENTION_DAYS).toString())
    }

    suspend fun logWater(amountMl: Int, date: String = Dates.today()) {
        if (amountMl <= 0) return
        dao.addWater(date, amountMl, System.currentTimeMillis())
    }

    suspend fun undoWater(entry: WaterEntry) = dao.removeWater(entry)

    fun observeSessions(date: String = Dates.today()): Flow<List<ExerciseSession>> =
        dao.observeSessions(date)

    /** Marks an exercise as running, which stops the tracker scoring the same minutes twice. */
    suspend fun startSession(at: Long = System.currentTimeMillis()) {
        trackerState.setSessionStartedAt(at)
    }

    /**
     * Records a finished exercise and folds it into the day.
     *
     * A session under [MIN_SESSION_SECONDS] is discarded: it is a mis-tap, and logging a
     * four-second run would put a stray row in the list and a rounding error in the totals.
     * Returns the session written, or null when it was too short.
     */
    suspend fun stopSession(type: ExerciseType, startedAt: Long, seconds: Int): ExerciseSession? {
        trackerState.setSessionStartedAt(0L)
        if (seconds < MIN_SESSION_SECONDS) return null
        val minutes = seconds / 60.0
        val weight = currentSettings().weightKg
        val session = ExerciseSession(
            date = Dates.today(),
            type = type.name,
            startedAt = startedAt,
            seconds = seconds,
            kcal = type.kcal(minutes, weight),
            heartPoints = type.heartPoints(minutes),
        )
        dao.addSession(session, moveMinutes = minutes.toInt())
        return session
    }

    /** Takes a session back off the day, for a mis-tap noticed after the fact. */
    suspend fun deleteSession(session: ExerciseSession) {
        dao.removeSession(session, moveMinutes = (session.seconds / 60.0).toInt())
    }

    companion object {
        /** Comfortably past the 30-day trends window, so nothing reachable is ever dropped. */
        private const val HOURLY_RETENTION_DAYS = 90L

        /** Below this a session is a mis-tap, not an exercise. */
        const val MIN_SESSION_SECONDS = 20
    }
}
