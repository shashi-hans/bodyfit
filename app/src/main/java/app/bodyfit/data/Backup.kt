package app.bodyfit.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes the whole local history to a file the user chooses.
 *
 * This is the only way data leaves the phone without an account, and it matters more here
 * than in most apps: there is no sync, so a lost or wiped phone is a lost history. The file
 * is plain JSON rather than a database copy so it can be read by anything, including a
 * future importer that does not share this schema.
 *
 * The destination comes from the system file picker, so no storage permission is needed and
 * the app never sees a path it was not handed.
 */
object Backup {

    const val FORMAT_VERSION = 1
    const val MIME_TYPE = "application/json"

    fun suggestedFileName(today: String = Dates.today()): String = "body-fit-backup-$today.json"

    fun toJson(days: List<DailyRecord>, water: List<WaterEntry>, settings: UserSettings): String {
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
}
