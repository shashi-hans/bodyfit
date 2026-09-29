package app.bodyfit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Sex { MALE, FEMALE, UNSPECIFIED }

/** Goals and body measurements the user controls. Defaults follow WHO activity guidance. */
data class UserSettings(
    /**
     * What to call the user on the Today screen. Blank until they type one.
     *
     * Never sent anywhere and never used in a calculation. It exists so the app can greet
     * the person using it, which is why a blank one is normal rather than a gap to fill.
     */
    val name: String = "",
    val heightCm: Int = 170,
    val weightKg: Int = 70,
    val stepGoal: Int = 10_000,
    val waterGoalMl: Int = 2_500,
    /** Active calories only, matching what the tracker records. */
    val calorieGoal: Int = 500,
    val heartPointGoal: Int = 21,
    val moveMinuteGoal: Int = 30,
    val weeklyStepGoal: Int = 70_000,
    val weeklyHeartPointGoal: Int = 150,
    /**
     * The three drink sizes offered as one-tap buttons, smallest first.
     *
     * Three because an Android notification shows at most three action buttons, and the
     * lock-screen card and the Today screen offer the same set.
     */
    val cupSizesMl: List<Int> = DEFAULT_CUP_SIZES_ML,
    /** Used only by the indicative health score. Optional: the score works without them. */
    val age: Int = 30,
    val smoker: Boolean = false,
    /** Only affects the resting-burn estimate. "unspecified" uses the midpoint constant. */
    val sex: Sex = Sex.UNSPECIFIED,
    /**
     * Whether the background tracker runs. Android requires a visible notification
     * for a service that reads sensors in the background, so this switch controls
     * both the counting and the lock-screen card together.
     */
    val trackerEnabled: Boolean = true,
    /**
     * Whether the first-run setup has been answered.
     *
     * False on a fresh install, and the app shows the setup screen instead of itself until
     * it is true. Every figure the app reports is scaled by height or weight, so a screen
     * of numbers derived from untouched defaults would look like measurements of the user
     * while being measurements of nobody.
     *
     * Not carried in a backup, for the same reason the tracker switch is not: it is a
     * property of this install, and a restore happens from inside an app already set up.
     */
    val setupComplete: Boolean = false,
    /**
     * Whether the background-access step has been put in front of the user once.
     *
     * Separate from [setupComplete] because it is asked after the permission prompts, and
     * because it can be skipped: the app still counts without it, just less reliably. Once
     * seen it is never shown again, whatever was chosen. The page behind the menu is where
     * someone who skipped goes back to it.
     */
    val backgroundPromptSeen: Boolean = false,
) {
    companion object {
        val STEP_GOAL_RANGE = 2_000..30_000
        val WATER_GOAL_RANGE = 500..6_000
        val CALORIE_GOAL_RANGE = 100..2_000
        val HEART_POINT_GOAL_RANGE = 5..80
        val MOVE_MINUTE_GOAL_RANGE = 10..180
        val HEIGHT_RANGE = 120..220
        val WEIGHT_RANGE = 30..200
        val CUP_SIZES_ML = listOf(100, 150, 200, 250, 300, 350, 400, 500, 750, 1_000)
        val DEFAULT_CUP_SIZES_ML = listOf(200, 250, 500)
        const val CUP_COUNT = 3
        val CUP_RANGE = 50..1_000

        /**
         * Returns exactly [CUP_COUNT] distinct in-range sizes, smallest first.
         *
         * Out-of-range values are clamped, duplicates dropped, and missing slots filled from
         * [DEFAULT_CUP_SIZES_ML], so a short or odd list from storage or a backup still
         * gives three usable buttons.
         */
        fun normalizeCups(sizes: List<Int>): List<Int> =
            (sizes.map { it.coerceIn(CUP_RANGE) } + DEFAULT_CUP_SIZES_ML + CUP_SIZES_ML)
                .distinct()
                .take(CUP_COUNT)
                .sorted()
        val AGE_RANGE = 12..100

        /** Long enough for any name worth greeting, short enough not to break the header. */
        const val NAME_MAX_CHARS = 24
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads and writes [UserSettings]. Backed by DataStore, so every write is atomic. */
class UserSettingsRepository(private val context: Context) {

    private object Keys {
        val HEIGHT = intPreferencesKey("height_cm")
        val WEIGHT = intPreferencesKey("weight_kg")
        val STEP_GOAL = intPreferencesKey("step_goal")
        val WATER_GOAL = intPreferencesKey("water_goal_ml")
        val CALORIE_GOAL = intPreferencesKey("calorie_goal")
        val HEART_POINT_GOAL = intPreferencesKey("heart_point_goal")
        val MOVE_MINUTE_GOAL = intPreferencesKey("move_minute_goal")
        val WEEKLY_STEP_GOAL = intPreferencesKey("weekly_step_goal")
        val WEEKLY_HEART_POINT_GOAL = intPreferencesKey("weekly_heart_point_goal")
        /** Single-cup key from before three sizes. Read once as the first size, never written. */
        val LEGACY_DEFAULT_CUP = intPreferencesKey("default_cup_ml")
        val CUP_SIZES = stringPreferencesKey("cup_sizes_ml")
        val TRACKER_ENABLED = booleanPreferencesKey("tracker_enabled")
        val AGE = intPreferencesKey("age")
        val SMOKER = booleanPreferencesKey("smoker")
        val SEX = stringPreferencesKey("sex")
        val NAME = stringPreferencesKey("name")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val BACKGROUND_PROMPT_SEEN = booleanPreferencesKey("background_prompt_seen")
    }

    val settings: Flow<UserSettings> = context.settingsStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            name = prefs[Keys.NAME] ?: defaults.name,
            heightCm = prefs[Keys.HEIGHT] ?: defaults.heightCm,
            weightKg = prefs[Keys.WEIGHT] ?: defaults.weightKg,
            stepGoal = prefs[Keys.STEP_GOAL] ?: defaults.stepGoal,
            waterGoalMl = prefs[Keys.WATER_GOAL] ?: defaults.waterGoalMl,
            calorieGoal = prefs[Keys.CALORIE_GOAL] ?: defaults.calorieGoal,
            heartPointGoal = prefs[Keys.HEART_POINT_GOAL] ?: defaults.heartPointGoal,
            moveMinuteGoal = prefs[Keys.MOVE_MINUTE_GOAL] ?: defaults.moveMinuteGoal,
            weeklyStepGoal = prefs[Keys.WEEKLY_STEP_GOAL] ?: defaults.weeklyStepGoal,
            weeklyHeartPointGoal = prefs[Keys.WEEKLY_HEART_POINT_GOAL] ?: defaults.weeklyHeartPointGoal,
            cupSizesMl = prefs[Keys.CUP_SIZES]?.let(::parseCups)
                ?: prefs[Keys.LEGACY_DEFAULT_CUP]?.let { UserSettings.normalizeCups(listOf(it, 500)) }
                ?: defaults.cupSizesMl,
            trackerEnabled = prefs[Keys.TRACKER_ENABLED] ?: defaults.trackerEnabled,
            age = prefs[Keys.AGE] ?: defaults.age,
            smoker = prefs[Keys.SMOKER] ?: defaults.smoker,
            sex = prefs[Keys.SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: defaults.sex,
            // Absent on an install that predates the setup screen. Such a phone has
            // already been through About you if it ever wrote a body measurement, so it
            // is treated as set up rather than walled behind questions it answered long
            // ago. A genuinely fresh install has written neither key.
            setupComplete = prefs[Keys.SETUP_COMPLETE]
                ?: (prefs[Keys.HEIGHT] != null || prefs[Keys.WEIGHT] != null),
            // Counted as seen only by an install that predates the setup screen: it has
            // been running for a while and should not be walled behind a question it never
            // had the chance to answer. A phone that has just been through setup has not
            // seen it, and setup writes height and weight, so those cannot be the test.
            backgroundPromptSeen = prefs[Keys.BACKGROUND_PROMPT_SEEN]
                ?: (
                    prefs[Keys.SETUP_COMPLETE] == null &&
                        (prefs[Keys.HEIGHT] != null || prefs[Keys.WEIGHT] != null)
                    ),
        )
    }

    suspend fun current(): UserSettings = settings.first()

    /**
     * Writes every goal and body measurement from [value] in one edit, used by restore.
     *
     * Each field goes through the same range clamp the individual setters apply, so a file
     * carrying an out-of-range number lands on the same bound a person typing it would hit.
     * The tracker switch is not written: whether this phone is counting is a property of the
     * phone, not of the backup.
     */
    suspend fun replace(value: UserSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.HEIGHT] = value.heightCm.coerceIn(UserSettings.HEIGHT_RANGE)
            prefs[Keys.WEIGHT] = value.weightKg.coerceIn(UserSettings.WEIGHT_RANGE)
            prefs[Keys.STEP_GOAL] = value.stepGoal.coerceIn(UserSettings.STEP_GOAL_RANGE)
            prefs[Keys.WATER_GOAL] = value.waterGoalMl.coerceIn(UserSettings.WATER_GOAL_RANGE)
            prefs[Keys.CALORIE_GOAL] = value.calorieGoal.coerceIn(UserSettings.CALORIE_GOAL_RANGE)
            prefs[Keys.HEART_POINT_GOAL] = value.heartPointGoal.coerceIn(UserSettings.HEART_POINT_GOAL_RANGE)
            prefs[Keys.MOVE_MINUTE_GOAL] = value.moveMinuteGoal.coerceIn(UserSettings.MOVE_MINUTE_GOAL_RANGE)
            prefs[Keys.WEEKLY_STEP_GOAL] = value.weeklyStepGoal.coerceAtLeast(1)
            prefs[Keys.WEEKLY_HEART_POINT_GOAL] = value.weeklyHeartPointGoal.coerceAtLeast(1)
            prefs[Keys.CUP_SIZES] = formatCups(value.cupSizesMl)
            prefs[Keys.AGE] = value.age.coerceIn(UserSettings.AGE_RANGE)
            prefs[Keys.SMOKER] = value.smoker
            prefs[Keys.SEX] = value.sex.name
            prefs[Keys.NAME] = cleanName(value.name)
        }
    }

    suspend fun setName(value: String) {
        context.settingsStore.edit { it[Keys.NAME] = cleanName(value) }
    }

    /**
     * Opens the app without asking the questions, for a restore that answered them.
     *
     * The body measurements are already written by the restore itself, so this only lifts
     * the gate.
     */
    suspend fun markBackgroundPromptSeen() {
        context.settingsStore.edit { it[Keys.BACKGROUND_PROMPT_SEEN] = true }
    }

    suspend fun markSetupComplete() {
        context.settingsStore.edit { it[Keys.SETUP_COMPLETE] = true }
    }

    /**
     * Writes the first-run answers and opens the app, in one edit.
     *
     * One edit rather than five, so a process death midway cannot leave the app unlocked
     * with only half the body measurements it was unlocked for.
     */
    suspend fun completeSetup(
        name: String,
        heightCm: Int,
        weightKg: Int,
        age: Int,
        sex: Sex,
    ) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.NAME] = cleanName(name)
            prefs[Keys.HEIGHT] = heightCm.coerceIn(UserSettings.HEIGHT_RANGE)
            prefs[Keys.WEIGHT] = weightKg.coerceIn(UserSettings.WEIGHT_RANGE)
            prefs[Keys.AGE] = age.coerceIn(UserSettings.AGE_RANGE)
            prefs[Keys.SEX] = sex.name
            prefs[Keys.SETUP_COMPLETE] = true
        }
    }

    /**
     * Trimmed and capped, so a stray paste cannot push the greeting off the header.
     *
     * Line breaks become spaces rather than being stripped: a name pasted from a form may
     * carry one, and dropping it would join two words that were never one.
     */
    private fun cleanName(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().take(UserSettings.NAME_MAX_CHARS)

    suspend fun setHeightCm(value: Int) = putInt(Keys.HEIGHT, value.coerceIn(UserSettings.HEIGHT_RANGE))
    suspend fun setWeightKg(value: Int) = putInt(Keys.WEIGHT, value.coerceIn(UserSettings.WEIGHT_RANGE))
    suspend fun setStepGoal(value: Int) = putInt(Keys.STEP_GOAL, value.coerceIn(UserSettings.STEP_GOAL_RANGE))
    suspend fun setWaterGoal(value: Int) = putInt(Keys.WATER_GOAL, value.coerceIn(UserSettings.WATER_GOAL_RANGE))
    suspend fun setCalorieGoal(value: Int) =
        putInt(Keys.CALORIE_GOAL, value.coerceIn(UserSettings.CALORIE_GOAL_RANGE))
    suspend fun setHeartPointGoal(value: Int) =
        putInt(Keys.HEART_POINT_GOAL, value.coerceIn(UserSettings.HEART_POINT_GOAL_RANGE))

    suspend fun setMoveMinuteGoal(value: Int) =
        putInt(Keys.MOVE_MINUTE_GOAL, value.coerceIn(UserSettings.MOVE_MINUTE_GOAL_RANGE))

    suspend fun setWeeklyStepGoal(value: Int) = putInt(Keys.WEEKLY_STEP_GOAL, value.coerceAtLeast(1))
    suspend fun setWeeklyHeartPointGoal(value: Int) = putInt(Keys.WEEKLY_HEART_POINT_GOAL, value.coerceAtLeast(1))
    suspend fun setCupSizes(value: List<Int>) {
        context.settingsStore.edit { it[Keys.CUP_SIZES] = formatCups(value) }
    }

    private fun parseCups(stored: String): List<Int> =
        UserSettings.normalizeCups(stored.split(',').mapNotNull { it.trim().toIntOrNull() })

    private fun formatCups(sizes: List<Int>): String = UserSettings.normalizeCups(sizes).joinToString(",")

    suspend fun setAge(value: Int) = putInt(Keys.AGE, value.coerceIn(UserSettings.AGE_RANGE))

    suspend fun setSex(value: Sex) {
        context.settingsStore.edit { it[Keys.SEX] = value.name }
    }

    suspend fun setSmoker(value: Boolean) {
        context.settingsStore.edit { it[Keys.SMOKER] = value }
    }

    suspend fun setTrackerEnabled(value: Boolean) {
        context.settingsStore.edit { it[Keys.TRACKER_ENABLED] = value }
    }

    private suspend fun putInt(key: Preferences.Key<Int>, value: Int) {
        context.settingsStore.edit { it[key] = value }
    }
}
