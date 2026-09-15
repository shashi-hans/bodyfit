package app.bodyfit.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpanKeysTest {

    @Test
    fun `a week runs Monday to Sunday whichever day you are on`() {
        val keys = Dates.weekKeys(LocalDate.of(2026, 9, 17))

        assertEquals(7, keys.size)
        assertEquals("2026-09-14", keys.first())
        assertEquals("2026-09-20", keys.last())
    }

    @Test
    fun `a month runs the 1st to the last day, including days still to come`() {
        val keys = Dates.monthKeys(LocalDate.of(2026, 9, 15))

        assertEquals(30, keys.size)
        assertEquals("2026-09-01", keys.first())
        assertEquals("2026-09-30", keys.last())
    }

    @Test
    fun `February gets its real length in a leap year`() {
        assertEquals(29, Dates.monthKeys(LocalDate.of(2028, 2, 10)).size)
        assertEquals(28, Dates.monthKeys(LocalDate.of(2026, 2, 10)).size)
    }

    @Test
    fun `month keys are unique and in order`() {
        val keys = Dates.monthKeys(LocalDate.of(2026, 12, 31))

        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys.sorted(), keys)
        assertEquals("2026-12-31", keys.last())
    }
}
