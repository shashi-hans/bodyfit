package app.bodyfit.notification

import app.bodyfit.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WaterReminderTest {

    private val on = UserSettings(waterReminderEnabled = true, waterReminderMinutes = 60, waterGoalMl = 2_500)
    private val now = 1_790_000_000_000L
    private val noon = LocalTime.of(12, 0)

    @Test
    fun `due when on, in hours, under goal and no drink yet`() {
        assertTrue(WaterReminder.isDue(on, drankTodayMl = 500, lastDrinkAt = null, now = now, time = noon))
    }

    @Test
    fun `not due when switched off`() {
        assertFalse(WaterReminder.isDue(on.copy(waterReminderEnabled = false), 0, null, now, noon))
    }

    @Test
    fun `not due at night`() {
        assertFalse(WaterReminder.isDue(on, 0, null, now, LocalTime.of(22, 30)))
        assertFalse(WaterReminder.isDue(on, 0, null, now, LocalTime.of(7, 59)))
    }

    @Test
    fun `not due once the goal is met`() {
        assertFalse(WaterReminder.isDue(on, 2_500, null, now, noon))
    }

    @Test
    fun `not due well within the interval of the last drink, due near the end of it`() {
        // A 60-minute interval has 15 minutes of slack: 44 minutes after a drink is too soon,
        // 46 minutes is close enough to the next run to ring.
        assertFalse(WaterReminder.isDue(on, 500, now - 44 * 60_000L, now, noon))
        assertTrue(WaterReminder.isDue(on, 500, now - 46 * 60_000L, now, noon))
    }

    @Test
    fun `a cup tapped seconds after a reminder does not cancel the next one`() {
        val tappedAt = now - (60 * 60_000L - 15_000L)
        assertTrue(WaterReminder.isDue(on, 500, tappedAt, now, noon))
    }

    @Test
    fun `the slack is a quarter of the interval`() {
        // 30 minutes leaves 7.5 minutes of slack, so the cut-off is 22.5 minutes.
        val every30 = on.copy(waterReminderMinutes = 30)
        assertFalse(WaterReminder.isDue(every30, 500, now - 22 * 60_000L, now, noon))
        assertTrue(WaterReminder.isDue(every30, 500, now - 23 * 60_000L, now, noon))
    }

    @Test
    fun `the user's own hours decide, including a window across midnight`() {
        val evening = on.copy(waterReminderStartHour = 18, waterReminderEndHour = 2)
        assertTrue(WaterReminder.inWindow(evening, 23))
        assertTrue(WaterReminder.inWindow(evening, 1))
        assertFalse(WaterReminder.inWindow(evening, 2))
        assertFalse(WaterReminder.inWindow(evening, 12))
    }

    @Test
    fun `equal start and end hours mean all day`() {
        val allDay = on.copy(waterReminderStartHour = 9, waterReminderEndHour = 9)
        (0..23).forEach { assertTrue(WaterReminder.inWindow(allDay, it)) }
    }

    @Test
    fun `a dismissed reminder rings again only if no drink was logged since`() {
        val dismissedAt = now - 15 * 60_000L
        assertTrue(WaterReminder.isSnoozeDue(on, 500, dismissedAt - 1, dismissedAt, noon))
        assertTrue(WaterReminder.isSnoozeDue(on, 500, null, dismissedAt, noon))
        assertFalse(WaterReminder.isSnoozeDue(on, 750, dismissedAt + 60_000L, dismissedAt, noon))
    }

    @Test
    fun `a snooze respects the hours and the goal`() {
        assertFalse(WaterReminder.isSnoozeDue(on, 500, null, now, LocalTime.of(23, 0)))
        assertFalse(WaterReminder.isSnoozeDue(on, 2_500, null, now, noon))
    }

    @Test
    fun `an end of 24 runs to the end of the day and reads as 11 59 pm`() {
        val wholeDay = on.copy(waterReminderStartHour = 0, waterReminderEndHour = 24)
        (0..23).forEach { assertTrue(WaterReminder.inWindow(wholeDay, it)) }
        val lateEvening = on.copy(waterReminderStartHour = 20, waterReminderEndHour = 24)
        assertTrue(WaterReminder.inWindow(lateEvening, 23))
        assertFalse(WaterReminder.inWindow(lateEvening, 0))
        assertTrue(WaterReminder.hourLabel(24).contains("59"))
    }

    // ---- predictNext --------------------------------------------------------------------

    private val utc = java.time.ZoneOffset.UTC
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        java.time.LocalDateTime.of(2026, 10, day, hour, minute).toInstant(utc).toEpochMilli()
    private val hourly = on.copy(waterReminderStartHour = 8, waterReminderEndHour = 22)

    @Test
    fun `off says off`() {
        assertEquals(
            NextReminder.Off,
            WaterReminder.predictNext(hourly.copy(waterReminderEnabled = false), 0, null, at(1, 10), null, at(1, 9), utc),
        )
    }

    @Test
    fun `the next scheduled run when it would ring`() {
        val next = WaterReminder.predictNext(hourly, 500, null, at(1, 10), null, at(1, 9, 30), utc)
        assertEquals(NextReminder.At(at(1, 10), false), next)
    }

    @Test
    fun `a run outside the hours moves to the first run inside them`() {
        // Runs every hour from 21:30; 22:30 and the small hours are outside 8 to 22.
        val next = WaterReminder.predictNext(hourly, 500, null, at(1, 21, 30), null, at(1, 21), utc)
        assertEquals(NextReminder.At(at(1, 21, 30), false), next)
        val late = WaterReminder.predictNext(hourly, 500, null, at(1, 22, 30), null, at(1, 22, 10), utc)
        assertEquals(NextReminder.At(at(2, 8, 30), false), late)
    }

    @Test
    fun `a met goal skips the rest of today and says so`() {
        val next = WaterReminder.predictNext(hourly, 2_500, null, at(1, 10), null, at(1, 9, 30), utc)
        assertEquals(NextReminder.At(at(2, 8), true), next)
    }

    @Test
    fun `a drink just now skips the run it is too close to`() {
        // Drank at 9:55; the 10:00 run is too soon, the 11:00 run rings.
        val next = WaterReminder.predictNext(hourly, 500, at(1, 9, 55), at(1, 10), null, at(1, 9, 56), utc)
        assertEquals(NextReminder.At(at(1, 11), false), next)
    }

    @Test
    fun `a waiting snooze comes first`() {
        val next = WaterReminder.predictNext(hourly, 500, null, at(1, 11), at(1, 10, 15), at(1, 10, 1), utc)
        assertEquals(NextReminder.At(at(1, 10, 15), false), next)
    }

    @Test
    fun `no schedule yet is unknown`() {
        assertEquals(NextReminder.Unknown, WaterReminder.predictNext(hourly, 0, null, null, null, at(1, 9), utc))
    }
}
