package app.bodyfit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    @Query("SELECT * FROM daily_record WHERE date = :date")
    fun observeDay(date: String): Flow<DailyRecord?>

    @Query("SELECT * FROM daily_record WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DailyRecord>>

    /**
     * Every day on record, oldest first. Streaks and the health score both need the
     * whole history; a year of tracking is about 365 small rows, so paging would cost more
     * than it saves.
     */
    @Query("SELECT * FROM daily_record ORDER BY date")
    fun observeAllDays(): Flow<List<DailyRecord>>

    /** One-shot read of the same rows, for the backup writer. */
    @Query("SELECT * FROM daily_record ORDER BY date")
    suspend fun allDaysOnce(): List<DailyRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM daily_record)")
    suspend fun hasAnyDay(): Boolean

    /** Every table the backup carries, read in one transaction so the rows agree. */
    @Transaction
    suspend fun backupRows(): BackupRows = BackupRows(
        days = allDaysOnce(),
        hours = allHoursOnce(),
        water = allWaterEntries(),
        sessions = allSessionsOnce(),
    )

    @Query("SELECT * FROM daily_record WHERE date = :date")
    suspend fun getDay(date: String): DailyRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(record: DailyRecord)

    @Query("SELECT * FROM water_entry WHERE date = :date ORDER BY loggedAt DESC")
    fun observeWaterEntries(date: String): Flow<List<WaterEntry>>

    @Query("DELETE FROM water_entry WHERE id = :id")
    suspend fun deleteWaterEntry(id: Long)

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entry WHERE date = :date")
    suspend fun waterTotal(date: String): Int

    /** When the most recent drink on any day was logged, or null when none ever was. */
    @Query("SELECT MAX(loggedAt) FROM water_entry")
    suspend fun lastWaterLoggedAt(): Long?

    /** Every drink ever logged. Only the backup writer needs this. */
    @Query("SELECT * FROM water_entry ORDER BY loggedAt")
    suspend fun allWaterEntries(): List<WaterEntry>

    @Insert
    suspend fun insertWaterEntry(entry: WaterEntry): Long

    @Query("SELECT * FROM hourly_record WHERE date = :date ORDER BY hour")
    fun observeHours(date: String): Flow<List<HourlyRecord>>

    @Query("SELECT * FROM hourly_record WHERE date = :date AND hour = :hour")
    suspend fun getHour(date: String, hour: Int): HourlyRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHour(record: HourlyRecord)

    /** Drops hourly detail older than [cutoff], which no trends window can reach. */
    @Query("DELETE FROM hourly_record WHERE date < :cutoff")
    suspend fun pruneHoursBefore(cutoff: String)

    /**
     * Adds the tracker's latest slice of activity to [date], creating the row if needed.
     *
     * The day total and the [hour] breakdown are written together, so the hourly rows of
     * a tracked day always sum back to the day's own figures.
     */
    @Transaction
    suspend fun addActivity(
        date: String,
        hour: Int,
        steps: Int,
        moveMinutes: Int,
        heartPoints: Int,
        kcal: Double,
    ) {
        val current = getDay(date) ?: DailyRecord(date = date)
        upsertDay(
            current.copy(
                steps = current.steps + steps,
                moveMinutes = current.moveMinutes + moveMinutes,
                heartPoints = current.heartPoints + heartPoints,
                activeKcal = current.activeKcal + kcal,
                updatedAt = System.currentTimeMillis(),
            )
        )
        val slot = getHour(date, hour) ?: HourlyRecord(date = date, hour = hour)
        upsertHour(
            slot.copy(
                steps = slot.steps + steps,
                moveMinutes = slot.moveMinutes + moveMinutes,
                heartPoints = slot.heartPoints + heartPoints,
                activeKcal = slot.activeKcal + kcal,
            )
        )
    }

    /** Logs a drink and refreshes the day's denormalised water total. */
    @Transaction
    suspend fun addWater(date: String, amountMl: Int, loggedAt: Long) {
        insertWaterEntry(WaterEntry(date = date, amountMl = amountMl, loggedAt = loggedAt))
        syncWaterTotal(date)
    }

    /** Removes a logged drink and refreshes the day's denormalised water total. */
    @Transaction
    suspend fun removeWater(entry: WaterEntry) {
        deleteWaterEntry(entry.id)
        syncWaterTotal(entry.date)
    }

    @Query("DELETE FROM water_entry WHERE date IN (:dates)")
    suspend fun deleteWaterForDates(dates: List<String>)

    /** Every hourly row on record, for the backup writer. */
    @Query("SELECT * FROM hourly_record ORDER BY date, hour")
    suspend fun allHoursOnce(): List<HourlyRecord>

    @Query("DELETE FROM hourly_record WHERE date IN (:dates)")
    suspend fun deleteHoursForDates(dates: List<String>)

    /** Every session on record, for the backup writer. */
    @Query("SELECT * FROM exercise_session ORDER BY date, startedAt")
    suspend fun allSessionsOnce(): List<ExerciseSession>

    @Query("DELETE FROM exercise_session WHERE date IN (:dates)")
    suspend fun deleteSessionsForDates(dates: List<String>)

    @Query("SELECT * FROM exercise_session WHERE date = :date ORDER BY startedAt DESC")
    fun observeSessions(date: String): Flow<List<ExerciseSession>>

    @Insert
    suspend fun insertSession(session: ExerciseSession): Long

    @Query("DELETE FROM exercise_session WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    /**
     * Records a finished exercise and folds it into the day in one transaction.
     *
     * Its minutes and heart points land on the day's row like a walked minute would, so
     * the goals on the Today screen count exercise the tracker cannot see. Steps are not
     * touched: a ride produces none, and a run's are already counted by the sensor.
     *
     * The hour the session started takes the same contribution, so the Day trend draws the
     * run in the hour it happened. Without that the day total and the 24 bars under it add
     * up to different numbers, and the chart quietly disagrees with the card above it. The
     * whole session is billed to its starting hour rather than split across the hours it
     * spanned: a session is minutes, not hours, and splitting it would invent a precision
     * the row does not carry.
     */
    @Transaction
    suspend fun addSession(session: ExerciseSession, moveMinutes: Int) {
        insertSession(session)
        val current = getDay(session.date) ?: DailyRecord(date = session.date)
        upsertDay(
            current.copy(
                moveMinutes = current.moveMinutes + moveMinutes,
                heartPoints = current.heartPoints + session.heartPoints,
                activeKcal = current.activeKcal + session.kcal,
                updatedAt = System.currentTimeMillis(),
            )
        )
        val hour = Dates.hourOf(session.startedAt)
        val slot = getHour(session.date, hour) ?: HourlyRecord(date = session.date, hour = hour)
        upsertHour(
            slot.copy(
                moveMinutes = slot.moveMinutes + moveMinutes,
                heartPoints = slot.heartPoints + session.heartPoints,
                activeKcal = slot.activeKcal + session.kcal,
            )
        )
    }

    /** Removes a session and takes its contribution back off the day and its hour. */
    @Transaction
    suspend fun removeSession(session: ExerciseSession, moveMinutes: Int) {
        deleteSessionById(session.id)
        val current = getDay(session.date) ?: return
        upsertDay(
            current.copy(
                moveMinutes = (current.moveMinutes - moveMinutes).coerceAtLeast(0),
                heartPoints = (current.heartPoints - session.heartPoints).coerceAtLeast(0),
                activeKcal = (current.activeKcal - session.kcal).coerceAtLeast(0.0),
                updatedAt = System.currentTimeMillis(),
            )
        )
        // The hour is worked out again from the start time in today's timezone. After a
        // change of timezone that can name a different day, and then the hour is left as it
        // is rather than taken off the wrong one.
        if (Dates.of(session.startedAt) != session.date) return
        val hour = Dates.hourOf(session.startedAt)
        val slot = getHour(session.date, hour) ?: return
        upsertHour(
            slot.copy(
                moveMinutes = (slot.moveMinutes - moveMinutes).coerceAtLeast(0),
                heartPoints = (slot.heartPoints - session.heartPoints).coerceAtLeast(0),
                activeKcal = (slot.activeKcal - session.kcal).coerceAtLeast(0.0),
            )
        )
    }

    /**
     * Writes a backup's rows over the days it covers, in one transaction.
     *
     * Only the dates in the file are touched: a day the phone recorded but the file does not
     * carry is left alone, so restoring an old backup never erases newer tracking. Within a
     * restored day the file wins outright, because a half-merged day would be neither what
     * was backed up nor what was tracked.
     *
     * Water entries, hourly rows and sessions for those days are replaced rather than added
     * to, so restoring the same file twice cannot double a day's total.
     *
     * Sessions are written as rows only. Their minutes, calories and heart points are not
     * folded back into the day, because the day rows in the file already include them and
     * folding again would count every session twice.
     */
    @Transaction
    suspend fun restore(
        days: List<DailyRecord>,
        hours: List<HourlyRecord>,
        water: List<WaterEntry>,
        sessions: List<ExerciseSession>,
    ) {
        val now = System.currentTimeMillis()
        val dates = days.map { it.date }
        val dateSet = dates.toSet()
        if (dates.isNotEmpty()) {
            deleteWaterForDates(dates)
            deleteHoursForDates(dates)
            deleteSessionsForDates(dates)
        }
        water.filter { it.date in dateSet }.forEach { insertWaterEntry(it.copy(id = 0)) }
        hours.filter { it.date in dateSet }.forEach { upsertHour(it) }
        sessions.filter { it.date in dateSet }.forEach { insertSession(it.copy(id = 0)) }
        // A day total with no drinks behind it, from a file whose entries were skipped or
        // never written, becomes one entry at noon. The total then survives the next drink
        // logged that day, which recomputes the day from its entries.
        val datesWithDrinks = water.map { it.date }.toSet()
        days.filter { it.waterMl > 0 && it.date !in datesWithDrinks }.forEach { day ->
            insertWaterEntry(
                WaterEntry(date = day.date, amountMl = day.waterMl, loggedAt = Dates.noonOf(day.date)),
            )
        }
        days.forEach { day ->
            upsertDay(day.copy(waterMl = waterTotal(day.date), updatedAt = now))
        }
    }

    @Transaction
    suspend fun syncWaterTotal(date: String) {
        val total = waterTotal(date)
        val current = getDay(date) ?: DailyRecord(date = date)
        upsertDay(current.copy(waterMl = total, updatedAt = System.currentTimeMillis()))
    }
}

/** A consistent read of every table a backup carries. */
data class BackupRows(
    val days: List<DailyRecord>,
    val hours: List<HourlyRecord>,
    val water: List<WaterEntry>,
    val sessions: List<ExerciseSession>,
)
