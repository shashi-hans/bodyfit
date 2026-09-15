package app.bodyfit.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DatesTest {

    @Test
    fun `hour labels read as clock times, not as bare numbers`() {
        assertEquals("12a", Dates.hourLabel(0))
        assertEquals("6a", Dates.hourLabel(6))
        assertEquals("12p", Dates.hourLabel(12))
        assertEquals("6p", Dates.hourLabel(18))
        assertEquals("11p", Dates.hourLabel(23))
    }

    @Test
    fun `the last hour of the day wraps to midnight`() {
        assertEquals("11p - 12a", Dates.hourRangeLabel(23))
        assertEquals("6a - 7a", Dates.hourRangeLabel(6))
    }

    @Test
    fun `a logged time lands in its local hour`() {
        val at = LocalDateTime.of(2026, 9, 15, 14, 37)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(14, Dates.hourOf(millis))
    }
}
