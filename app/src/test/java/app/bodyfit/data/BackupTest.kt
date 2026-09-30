package app.bodyfit.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup is the only copy of a user's history that can leave the phone, and there is
 * no sync behind it. A silently truncated file would only be discovered when someone
 * needed it, so the shape is pinned here.
 */
class BackupTest {

    private val days = listOf(
        DailyRecord("2026-09-07", steps = 7_429, moveMinutes = 58, heartPoints = 50, activeKcal = 328.05, waterMl = 2_500),
        DailyRecord("2026-09-08", steps = 13_207, moveMinutes = 74, heartPoints = 83, activeKcal = 484.0, waterMl = 3_500),
    )
    private val hours = listOf(
        HourlyRecord("2026-09-08", hour = 7, steps = 2_100, moveMinutes = 14, heartPoints = 9, activeKcal = 41.5),
        HourlyRecord("2026-09-08", hour = 18, steps = 3_400, moveMinutes = 22, heartPoints = 16, activeKcal = 70.0),
    )
    private val water = listOf(
        WaterEntry(1, "2026-09-08", 500, 1_788_800_000_000),
        WaterEntry(2, "2026-09-08", 250, 1_788_800_100_000),
    )
    private val sessions = listOf(
        ExerciseSession(
            id = 1,
            date = "2026-09-08",
            type = "RUNNING",
            startedAt = 1_788_800_200_000,
            seconds = 1_500,
            kcal = 210.5,
            heartPoints = 50,
            metres = 4_200.0,
        ),
    )
    private val settings = UserSettings(
        name = "Sam",
        heightCm = 179,
        weightKg = 75,
        age = 34,
        sex = Sex.MALE,
    )

    private fun parsed() = JSONObject(Backup.toJson(days, hours, water, sessions, settings))

    @Test
    fun `every day survives the round trip with all its fields`() {
        val out = parsed().getJSONArray("days")
        assertEquals(2, out.length())
        val second = out.getJSONObject(1)
        assertEquals("2026-09-08", second.getString("date"))
        assertEquals(13_207, second.getInt("steps"))
        assertEquals(74, second.getInt("moveMinutes"))
        assertEquals(83, second.getInt("heartPoints"))
        assertEquals(484.0, second.getDouble("activeKcal"), 0.001)
        assertEquals(3_500, second.getInt("waterMl"))
    }

    @Test
    fun `individual drinks are kept, not just the daily total`() {
        val entries = parsed().getJSONArray("waterEntries")
        assertEquals(2, entries.length())
        assertEquals(500, entries.getJSONObject(0).getInt("amountMl"))
        assertEquals(1_788_800_100_000, entries.getJSONObject(1).getLong("loggedAt"))
    }

    @Test
    fun `body measurements and goals are all present`() {
        val s = parsed().getJSONObject("settings")
        for (key in listOf(
            "heightCm", "weightKg", "age", "smoker", "stepGoal", "waterGoalMl",
            "calorieGoal", "heartPointGoal", "moveMinuteGoal", "weeklyStepGoal",
            "weeklyHeartPointGoal", "cupSizesMl",
        )) {
            assertTrue("$key missing from the backup", s.has(key))
        }
        assertEquals(179, s.getInt("heightCm"))
        assertEquals(34, s.getInt("age"))
    }

    @Test
    fun `the file carries a format version so a future importer can branch on it`() {
        val root = parsed()
        assertEquals(Backup.FORMAT_VERSION, root.getInt("format"))
        assertTrue(root.getLong("exportedAt") > 0)
    }

    @Test
    fun `an empty history still produces a valid file rather than failing`() {
        val root = JSONObject(Backup.toJson(emptyList(), emptyList(), emptyList(), emptyList(), UserSettings()))
        assertEquals(0, root.getJSONArray("days").length())
        assertEquals(0, root.getJSONArray("hours").length())
        assertEquals(0, root.getJSONArray("waterEntries").length())
        assertEquals(0, root.getJSONArray("sessions").length())
    }

    @Test
    fun `the suggested name is dated so successive exports do not overwrite`() {
        assertEquals("bodyfit-backup-2026-09-08.json", Backup.suggestedFileName("2026-09-08"))
    }

    @Test
    fun `a session survives the round trip with its distance`() {
        val out = Backup.fromJson(parsed().toString(), UserSettings()).sessions
        assertEquals(1, out.size)
        val session = out.first()
        assertEquals("RUNNING", session.type)
        assertEquals(1_500, session.seconds)
        assertEquals(50, session.heartPoints)
        assertEquals(4_200.0, session.metres, 0.001)
        assertEquals(210.5, session.kcal, 0.001)
    }

    @Test
    fun `the name survives the round trip`() {
        assertEquals("Sam", Backup.fromJson(parsed().toString(), UserSettings()).settings.name)
    }

    @Test
    fun `a file written before sessions existed restores without them`() {
        val older = parsed().apply { remove("sessions") }.toString()
        assertEquals(emptyList<ExerciseSession>(), Backup.fromJson(older, UserSettings()).sessions)
    }

    @Test
    fun `cup sizes round-trip through a backup`() {
        val json = Backup.toJson(emptyList(), emptyList(), emptyList(), emptyList(), UserSettings(cupSizesMl = listOf(150, 350, 750)))
        assertEquals(listOf(150, 350, 750), Backup.fromJson(json, UserSettings()).settings.cupSizesMl)
    }

    @Test
    fun `a single default cup from an older file becomes that size plus 500 ml`() {
        val older = """{"format":1,"settings":{"defaultCupMl":300}}"""
        assertEquals(listOf(200, 300, 500), Backup.fromJson(older, UserSettings()).settings.cupSizesMl)
    }

    @Test
    fun `short, duplicate or out-of-range cup lists still give three sizes`() {
        assertEquals(listOf(200, 250, 1_000), UserSettings.normalizeCups(listOf(5_000, 5_000)))
        assertEquals(listOf(100, 200, 250), UserSettings.normalizeCups(listOf(10)))
        assertEquals(listOf(200, 300, 350), UserSettings.normalizeCups(listOf(330, 290)))
        assertEquals(listOf(200, 250, 500), UserSettings.normalizeCups(emptyList()))
    }
}
