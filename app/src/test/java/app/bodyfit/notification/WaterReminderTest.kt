package app.bodyfit.notification

import app.bodyfit.data.UserSettings
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
}
