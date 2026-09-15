package app.bodyfit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per calendar day, keyed by the local date in ISO form (yyyy-MM-dd).
 *
 * Steps, move minutes, heart points and active calories are accumulated live by
 * the step tracker service. Water is the denormalised sum of that day's
 * [WaterEntry] rows, kept in sync by [HealthDao.addWater] and
 * [HealthDao.removeWater] so weekly trends need a single table scan.
 *
 * Distance is not stored: it is derived from steps and the user's height by
 * [app.bodyfit.sensor.Metrics.distanceKm].
 */
@Entity(tableName = "daily_record")
data class DailyRecord(
    @PrimaryKey val date: String,
    val steps: Int = 0,
    val moveMinutes: Int = 0,
    val heartPoints: Int = 0,
    val activeKcal: Double = 0.0,
    val waterMl: Int = 0,
    /**
     * Local wall-clock time of the last change, used to decide what still needs pushing.
     * The server stamps its own time on write; this one is only a tiebreak hint, because a
     * phone's clock can be wrong or deliberately moved.
     */
    val updatedAt: Long = 0L,
)

/**
 * One row per hour of a day, keyed by the local date and the hour 0 to 23.
 *
 * Holds the same sensor fields as [DailyRecord] so the trends screen can break a day
 * down. A day's hourly rows sum to that day's totals for any day tracked since the
 * table was added; earlier days have no hourly rows at all.
 *
 * Water is not stored here. [WaterEntry] already carries the time each drink was
 * logged, so the hourly split is read from those rows instead of duplicated.
 *
 * Rows older than the longest trends window are pruned, so this table stays at about
 * 90 x 24 rows rather than growing for the life of the install.
 */
@Entity(
    tableName = "hourly_record",
    primaryKeys = ["date", "hour"],
)
data class HourlyRecord(
    val date: String,
    /** Local hour of the day, 0 to 23. */
    val hour: Int,
    val steps: Int = 0,
    val moveMinutes: Int = 0,
    val heartPoints: Int = 0,
    val activeKcal: Double = 0.0,
)

/** A single drink the user logged. Kept individually so an entry can be undone. */
@Entity(
    tableName = "water_entry",
    indices = [Index("date")],
)
data class WaterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val amountMl: Int,
    val loggedAt: Long,
) {
    companion object {
        /**
         * Millilitres logged in each hour of a day, as 24 slots starting at midnight.
         *
         * Hours come from [loggedAt] rather than a stored column, so this is the water
         * counterpart of [HourlyRecord]. An entry whose timestamp falls outside the day
         * is clamped into it rather than dropped, which keeps the slots summing to the
         * day's total even if a clock change moved a drink past midnight.
         */
        fun byHour(entries: List<WaterEntry>): IntArray {
            val buckets = IntArray(24)
            entries.forEach { entry ->
                buckets[Dates.hourOf(entry.loggedAt).coerceIn(0, 23)] += entry.amountMl
            }
            return buckets
        }
    }
}
