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

    @Query("SELECT * FROM daily_record WHERE date = :date")
    suspend fun getDay(date: String): DailyRecord?

    /** Days changed locally since [since], oldest first. The push side of sync reads this. */
    @Query("SELECT * FROM daily_record WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<DailyRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(record: DailyRecord)

    @Query("SELECT * FROM water_entry WHERE date = :date ORDER BY loggedAt DESC")
    fun observeWaterEntries(date: String): Flow<List<WaterEntry>>

    @Query("DELETE FROM water_entry WHERE id = :id")
    suspend fun deleteWaterEntry(id: Long)

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entry WHERE date = :date")
    suspend fun waterTotal(date: String): Int

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

    @Transaction
    suspend fun syncWaterTotal(date: String) {
        val total = waterTotal(date)
        val current = getDay(date) ?: DailyRecord(date = date)
        upsertDay(current.copy(waterMl = total, updatedAt = System.currentTimeMillis()))
    }
}
