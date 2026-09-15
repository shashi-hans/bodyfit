package app.bodyfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An export nobody can read back is not a backup. These pin the read side: what survives a
 * round trip, and what a file that is damaged, foreign or from the future does.
 */
class BackupRestoreTest {

    private val days = listOf(
        DailyRecord("2026-09-07", steps = 7_429, moveMinutes = 58, heartPoints = 50, activeKcal = 328.05, waterMl = 2_500),
        DailyRecord("2026-09-08", steps = 13_207, moveMinutes = 74, heartPoints = 83, activeKcal = 484.0, waterMl = 750),
    )
    private val hours = listOf(
        HourlyRecord("2026-09-08", hour = 7, steps = 2_100, moveMinutes = 14, heartPoints = 9, activeKcal = 41.5),
        HourlyRecord("2026-09-08", hour = 18, steps = 3_400, moveMinutes = 22, heartPoints = 16, activeKcal = 70.0),
    )
    private val water = listOf(
        WaterEntry(1, "2026-09-08", 500, 1_788_800_000_000),
        WaterEntry(2, "2026-09-08", 250, 1_788_800_100_000),
    )
    private val settings = UserSettings(heightCm = 179, weightKg = 75, age = 34, sex = Sex.MALE, stepGoal = 12_000)

    private fun roundTrip(): Backup.Snapshot =
        Backup.fromJson(Backup.toJson(days, hours, water, settings), UserSettings())

    @Test
    fun `a file written by this build reads back unchanged`() {
        val restored = roundTrip()

        assertEquals(days, restored.days)
        assertEquals(2, restored.water.size)
        assertEquals(500, restored.water[0].amountMl)
        assertEquals(1_788_800_000_000, restored.water[0].loggedAt)
        assertEquals("2026-09-08", restored.water[1].date)
    }

    @Test
    fun `goals and body measurements come back`() {
        val restored = roundTrip().settings

        assertEquals(179, restored.heightCm)
        assertEquals(75, restored.weightKg)
        assertEquals(34, restored.age)
        assertEquals(12_000, restored.stepGoal)
    }

    @Test
    fun `a field the file predates keeps the value already in use`() {
        // Whether this phone is counting is a property of the phone, not of the backup, so
        // the writer never emits it and a restore must not reset it.
        val current = UserSettings(trackerEnabled = false)
        val restored = Backup.fromJson(Backup.toJson(days, hours, water, settings), current)

        assertEquals(false, restored.settings.trackerEnabled)
    }

    @Test
    fun `sex survives the round trip, because the resting burn estimate needs it`() {
        val restored = Backup.fromJson(
            Backup.toJson(days, hours, water, settings),
            UserSettings(sex = Sex.UNSPECIFIED),
        )

        assertEquals(Sex.MALE, restored.settings.sex)
    }

    @Test
    fun `the hourly breakdown survives, so the Day trend comes back too`() {
        val restored = roundTrip()

        assertEquals(2, restored.hours.size)
        assertEquals(7, restored.hours[0].hour)
        assertEquals(2_100, restored.hours[0].steps)
        assertEquals(70.0, restored.hours[1].activeKcal, 0.0001)
    }

    @Test
    fun `a version 1 file still restores, just without its hourly detail`() {
        val v1 = """{"format":1,"days":[{"date":"2026-09-07","steps":7429}]}"""

        val restored = Backup.fromJson(v1)

        assertEquals(1, restored.days.size)
        assertTrue(restored.hours.isEmpty())
    }

    @Test
    fun `an hour outside 0 to 23 is skipped rather than stored`() {
        val json = """
            {
              "format": 2,
              "days": [{"date": "2026-09-07", "steps": 100}],
              "hours": [
                {"date": "2026-09-07", "hour": 0, "steps": 40},
                {"date": "2026-09-07", "hour": 24, "steps": 60},
                {"date": "2026-09-07", "steps": 60}
              ]
            }
        """.trimIndent()

        val restored = Backup.fromJson(json)

        assertEquals(1, restored.hours.size)
        assertEquals(0, restored.hours[0].hour)
    }

    @Test
    fun `a file that is not a backup is refused with a message worth showing`() {
        listOf("", "not json at all", "{}", """{"days":[]}""").forEach { text ->
            val failure = runCatching { Backup.fromJson(text) }.exceptionOrNull()
            assertTrue("accepted $text", failure is IllegalArgumentException)
            assertTrue("empty message for $text", !failure!!.message.isNullOrBlank())
        }
    }

    @Test
    fun `a backup from a newer build is refused rather than half read`() {
        val future = """{"format":${Backup.FORMAT_VERSION + 1},"days":[]}"""
        val failure = runCatching { Backup.fromJson(future) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("newer"))
    }

    @Test
    fun `one damaged row is skipped, the rest of the history survives`() {
        val json = """
            {
              "format": 1,
              "days": [
                {"date": "2026-09-07", "steps": 7429},
                {"steps": 999},
                {"date": "2026-09-08", "steps": 13207}
              ],
              "waterEntries": [
                {"date": "2026-09-08", "amountMl": 500, "loggedAt": 1788800000000},
                {"amountMl": 250, "loggedAt": 1788800100000},
                {"date": "2026-09-08", "amountMl": 0, "loggedAt": 1788800200000}
              ]
            }
        """.trimIndent()

        val restored = Backup.fromJson(json)

        assertEquals(2, restored.days.size)
        assertEquals(listOf("2026-09-07", "2026-09-08"), restored.days.map { it.date })
        assertEquals(1, restored.water.size)
        assertNotEquals(0, restored.water[0].amountMl)
    }

    @Test
    fun `a day missing its optional fields restores as an empty day, not a crash`() {
        val restored = Backup.fromJson("""{"format":1,"days":[{"date":"2026-09-07"}]}""")

        assertEquals(1, restored.days.size)
        assertEquals(DailyRecord("2026-09-07"), restored.days[0])
    }
}
