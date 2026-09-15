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
    private val settings = UserSettings(heightCm = 179, weightKg = 75, age = 34, sex = Sex.MALE)

    private fun parsed() = JSONObject(Backup.toJson(days, hours, water, settings))

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
            "weeklyHeartPointGoal", "defaultCupMl",
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
        val root = JSONObject(Backup.toJson(emptyList(), emptyList(), emptyList(), UserSettings()))
        assertEquals(0, root.getJSONArray("days").length())
        assertEquals(0, root.getJSONArray("hours").length())
        assertEquals(0, root.getJSONArray("waterEntries").length())
    }

    @Test
    fun `the suggested name is dated so successive exports do not overwrite`() {
        assertEquals("bodyfit-backup-2026-09-08.json", Backup.suggestedFileName("2026-09-08"))
    }
}
