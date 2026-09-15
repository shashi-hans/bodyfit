package app.bodyfit.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes the whole local history to a file the user chooses, and reads one back.
 *
 * This is the only way data leaves or re-enters the phone without an account, and it matters
 * more here than in most apps: there is no sync, so a lost or wiped phone is a lost history.
 * The file is plain JSON rather than a database copy so it can be read by anything, and so a
 * file written by one install restores into another that does not share its database.
 *
 * Both the destination and the source come from the system file picker, so no storage
 * permission is needed and the app never sees a path it was not handed.
 */
object Backup {

    const val FORMAT_VERSION = 2
    const val MIME_TYPE = "application/json"

    fun suggestedFileName(today: String = Dates.today()): String = "bodyfit-backup-$today.json"

    fun toJson(
        days: List<DailyRecord>,
        hours: List<HourlyRecord>,
        water: List<WaterEntry>,
        settings: UserSettings,
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put(
            "settings",
            JSONObject().apply {
                put("heightCm", settings.heightCm)
                put("weightKg", settings.weightKg)
                put("age", settings.age)
                put("smoker", settings.smoker)
                put("sex", settings.sex.name)
                put("stepGoal", settings.stepGoal)
                put("waterGoalMl", settings.waterGoalMl)
                put("calorieGoal", settings.calorieGoal)
                put("heartPointGoal", settings.heartPointGoal)
                put("moveMinuteGoal", settings.moveMinuteGoal)
                put("weeklyStepGoal", settings.weeklyStepGoal)
                put("weeklyHeartPointGoal", settings.weeklyHeartPointGoal)
                put("defaultCupMl", settings.defaultCupMl)
            },
        )

        root.put(
            "days",
            JSONArray().apply {
                days.forEach { day ->
                    put(
                        JSONObject().apply {
                            put("date", day.date)
                            put("steps", day.steps)
                            put("moveMinutes", day.moveMinutes)
                            put("heartPoints", day.heartPoints)
                            put("activeKcal", day.activeKcal)
                            put("waterMl", day.waterMl)
                        },
                    )
                }
            },
        )

        root.put(
            "hours",
            JSONArray().apply {
                hours.forEach { row ->
                    put(
                        JSONObject().apply {
                            put("date", row.date)
                            put("hour", row.hour)
                            put("steps", row.steps)
                            put("moveMinutes", row.moveMinutes)
                            put("heartPoints", row.heartPoints)
                            put("activeKcal", row.activeKcal)
                        },
                    )
                }
            },
        )

        root.put(
            "waterEntries",
            JSONArray().apply {
                water.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("date", entry.date)
                            put("amountMl", entry.amountMl)
                            put("loggedAt", entry.loggedAt)
                        },
                    )
                }
            },
        )

        return root.toString(2)
    }

    /** Returns the number of bytes written, or throws whatever the content resolver throws. */
    fun write(context: Context, target: Uri, json: String): Int {
        val bytes = json.toByteArray()
        context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
            ?: error("could not open $target for writing")
        return bytes.size
    }

    /** Everything a backup file carries, ready to be written back to the database. */
    data class Snapshot(
        val days: List<DailyRecord>,
        val hours: List<HourlyRecord>,
        val water: List<WaterEntry>,
        val settings: UserSettings,
    )

    /**
     * Parses a backup file back into rows.
     *
     * Throws [IllegalArgumentException] with a message worth showing the user when the file
     * is not a backup, or was written by a newer format this build cannot read. A file that
     * parses but carries an odd row is not rejected wholesale: a row missing its date is
     * skipped, because losing one day is better than refusing a year of history.
     *
     * Settings are merged onto [current] rather than replacing it, so a field added since
     * the file was written keeps the value in use instead of silently resetting.
     */
    fun fromJson(json: String, current: UserSettings = UserSettings()): Snapshot {
        val root = try {
            JSONObject(json)
        } catch (e: org.json.JSONException) {
            throw IllegalArgumentException("This file is not a Body Fit backup.", e)
        }
        val format = root.optInt("format", 0)
        require(format > 0) { "This file is not a Body Fit backup." }
        require(format <= FORMAT_VERSION) {
            "This backup was written by a newer version of Body Fit. Update the app first."
        }

        val settingsJson = root.optJSONObject("settings")
        val settings = if (settingsJson == null) {
            current
        } else {
            current.copy(
                heightCm = settingsJson.optInt("heightCm", current.heightCm),
                weightKg = settingsJson.optInt("weightKg", current.weightKg),
                age = settingsJson.optInt("age", current.age),
                smoker = settingsJson.optBoolean("smoker", current.smoker),
                sex = settingsJson.optString("sex")
                    .let { name -> runCatching { Sex.valueOf(name) }.getOrNull() } ?: current.sex,
                stepGoal = settingsJson.optInt("stepGoal", current.stepGoal),
                waterGoalMl = settingsJson.optInt("waterGoalMl", current.waterGoalMl),
                calorieGoal = settingsJson.optInt("calorieGoal", current.calorieGoal),
                heartPointGoal = settingsJson.optInt("heartPointGoal", current.heartPointGoal),
                moveMinuteGoal = settingsJson.optInt("moveMinuteGoal", current.moveMinuteGoal),
                weeklyStepGoal = settingsJson.optInt("weeklyStepGoal", current.weeklyStepGoal),
                weeklyHeartPointGoal = settingsJson.optInt(
                    "weeklyHeartPointGoal",
                    current.weeklyHeartPointGoal,
                ),
                defaultCupMl = settingsJson.optInt("defaultCupMl", current.defaultCupMl),
            )
        }

        val daysJson = root.optJSONArray("days") ?: JSONArray()
        val days = (0 until daysJson.length()).mapNotNull { index ->
            val row = daysJson.optJSONObject(index) ?: return@mapNotNull null
            val date = row.optString("date").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DailyRecord(
                date = date,
                steps = row.optInt("steps"),
                moveMinutes = row.optInt("moveMinutes"),
                heartPoints = row.optInt("heartPoints"),
                activeKcal = row.optDouble("activeKcal", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                waterMl = row.optInt("waterMl"),
            )
        }

        // Absent from a version 1 file. A restore then leaves those days without an hourly
        // breakdown, which the trends screen already draws as an empty day rather than a gap.
        val hoursJson = root.optJSONArray("hours") ?: JSONArray()
        val hours = (0 until hoursJson.length()).mapNotNull { index ->
            val row = hoursJson.optJSONObject(index) ?: return@mapNotNull null
            val date = row.optString("date").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val hour = row.optInt("hour", -1)
            if (hour !in 0..23) return@mapNotNull null
            HourlyRecord(
                date = date,
                hour = hour,
                steps = row.optInt("steps"),
                moveMinutes = row.optInt("moveMinutes"),
                heartPoints = row.optInt("heartPoints"),
                activeKcal = row.optDouble("activeKcal", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            )
        }

        val waterJson = root.optJSONArray("waterEntries") ?: JSONArray()
        val water = (0 until waterJson.length()).mapNotNull { index ->
            val row = waterJson.optJSONObject(index) ?: return@mapNotNull null
            val date = row.optString("date").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val amount = row.optInt("amountMl")
            if (amount <= 0) return@mapNotNull null
            WaterEntry(date = date, amountMl = amount, loggedAt = row.optLong("loggedAt"))
        }

        return Snapshot(days = days, hours = hours, water = water, settings = settings)
    }

    /** Reads a file the user picked. Throws whatever the content resolver throws. */
    fun read(context: Context, source: Uri): String =
        context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
            ?: error("could not open $source for reading")
}
