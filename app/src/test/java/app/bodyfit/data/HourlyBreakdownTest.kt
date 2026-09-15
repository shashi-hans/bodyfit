package app.bodyfit.data

import app.bodyfit.ui.Metric
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HourlyBreakdownTest {

    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, 15, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun `drinks land in the hour they were logged`() {
        val entries = listOf(
            WaterEntry(date = "2026-09-15", amountMl = 200, loggedAt = at(7, 5)),
            WaterEntry(date = "2026-09-15", amountMl = 300, loggedAt = at(7, 55)),
            WaterEntry(date = "2026-09-15", amountMl = 500, loggedAt = at(19)),
        )

        val buckets = WaterEntry.byHour(entries)

        assertEquals(500, buckets[7])
        assertEquals(500, buckets[19])
        assertEquals(0, buckets[8])
        assertEquals(1_000, buckets.sum())
    }

    @Test
    fun `a day with no drinks has 24 empty slots`() {
        val buckets = WaterEntry.byHour(emptyList())

        assertEquals(24, buckets.size)
        assertEquals(0, buckets.sum())
    }

    @Test
    fun `hourly rows carry the same measures as a day`() {
        val hour = HourlyRecord(
            date = "2026-09-15",
            hour = 9,
            steps = 1_200,
            moveMinutes = 12,
            heartPoints = 8,
            activeKcal = 44.5,
        )
        val settings = UserSettings(heightCm = 170)

        assertEquals(1_200.0, Metric.STEPS.value(hour, settings, waterMl = 0), 0.0001)
        assertEquals(44.5, Metric.CALORIES.value(hour, settings, waterMl = 0), 0.0001)
        assertEquals(12.0, Metric.MOVE_MINUTES.value(hour, settings, waterMl = 0), 0.0001)
        assertEquals(8.0, Metric.HEART_POINTS.value(hour, settings, waterMl = 0), 0.0001)
        // 1,200 steps at a 0.7055 m stride
        assertEquals(0.8466, Metric.DISTANCE.value(hour, settings, waterMl = 0), 0.0001)
        // Water is not stored on the row: it comes from the drinks logged in that hour.
        assertEquals(750.0, Metric.WATER.value(hour, settings, waterMl = 750), 0.0001)
    }

    @Test
    fun `an hour with nothing in it reads as zero for every measure`() {
        val empty = HourlyRecord(date = "2026-09-15", hour = 3)
        val settings = UserSettings(heightCm = 170)

        Metric.entries.forEach { metric ->
            assertEquals(metric.name, 0.0, metric.value(empty, settings, waterMl = 0), 0.0001)
        }
    }
}
