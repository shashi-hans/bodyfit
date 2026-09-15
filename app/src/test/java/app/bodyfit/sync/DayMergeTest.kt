package app.bodyfit.sync

import app.bodyfit.data.DailyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayMergeTest {

    private fun local(
        steps: Int = 0,
        move: Int = 0,
        hp: Int = 0,
        kcal: Double = 0.0,
        water: Int = 0,
        at: Long = 100,
    ) = DailyRecord("2026-09-08", steps, move, hp, kcal, water, at)

    private fun remote(
        steps: Int = 0,
        move: Int = 0,
        hp: Int = 0,
        kcal: Double = 0.0,
        water: Int = 0,
        at: Long = 100,
    ) = DayDto("2026-09-08", steps, move, hp, kcal, water, at)

    @Test
    fun `sensor fields take the higher value whichever side is newer`() {
        // The older side counted more of the day. Last-write-wins would throw that away.
        val merged = DayMerge.merge(local(steps = 9000, at = 500), remote(steps = 4000, at = 900))
        assertEquals(9000, merged.steps)
    }

    @Test
    fun `a phone counting steps and a tablet logging water both survive`() {
        val phone = local(steps = 9000, water = 0, at = 500)
        val tablet = remote(steps = 0, water = 1500, at = 900)
        val merged = DayMerge.merge(phone, tablet)
        assertEquals(9000, merged.steps)
        assertEquals(1500, merged.waterMl)
    }

    @Test
    fun `water follows the newer side because entries can be deleted`() {
        val newerRemote = DayMerge.merge(local(water = 2000, at = 500), remote(water = 750, at = 900))
        assertEquals(750, newerRemote.waterMl)

        val newerLocal = DayMerge.merge(local(water = 2000, at = 900), remote(water = 750, at = 500))
        assertEquals(2000, newerLocal.waterMl)
    }

    @Test
    fun `every sensor field merges, not just steps`() {
        val merged = DayMerge.merge(
            local(move = 40, hp = 12, kcal = 300.0, at = 900),
            remote(move = 70, hp = 5, kcal = 410.0, at = 500),
        )
        assertEquals(70, merged.moveMinutes)
        assertEquals(12, merged.heartPoints)
        assertEquals(410.0, merged.activeKcal, 0.001)
    }

    @Test
    fun `merging leaves updatedAt alone so the two clocks stay apart`() {
        val merged = DayMerge.merge(local(steps = 10, at = 100), remote(steps = 20, at = 999_999))
        assertEquals(100, merged.updatedAt)
    }

    @Test
    fun `identical days do not count as a change`() {
        val record = local(steps = 5000, water = 1000)
        assertFalse(DayMerge.differs(record, DayMerge.toDto(record)))
        assertTrue(DayMerge.differs(record, DayMerge.toDto(record).copy(steps = 5001)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `merging two different dates is a programming error`() {
        DayMerge.merge(local(), remote().copy(date = "2026-09-09"))
    }

    @Test
    fun `a round trip through the dto keeps every field`() {
        val record = local(steps = 7429, move = 58, hp = 50, kcal = 328.055, water = 2500, at = 42)
        assertEquals(record, DayMerge.toRecord(DayMerge.toDto(record)))
    }
}
