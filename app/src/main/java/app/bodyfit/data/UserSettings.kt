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
    val defaultCupMl: Int = 250,
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
) {
    companion object {
        val STEP_GOAL_RANGE = 2_000..30_000
        val WATER_GOAL_RANGE = 500..6_000
        val CALORIE_GOAL_RANGE = 100..2_000
        val HEART_POINT_GOAL_RANGE = 5..80
        val MOVE_MINUTE_GOAL_RANGE = 10..180
        val HEIGHT_RANGE = 120..220
        val WEIGHT_RANGE = 30..200
        val CUP_SIZES_ML = listOf(100, 150, 200, 250, 300, 500)
        val AGE_RANGE = 12..100
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
        val DEFAULT_CUP = intPreferencesKey("default_cup_ml")
        val TRACKER_ENABLED = booleanPreferencesKey("tracker_enabled")
        val AGE = intPreferencesKey("age")
        val SMOKER = booleanPreferencesKey("smoker")
        val SEX = stringPreferencesKey("sex")
    }

    val settings: Flow<UserSettings> = context.settingsStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            heightCm = prefs[Keys.HEIGHT] ?: defaults.heightCm,
            weightKg = prefs[Keys.WEIGHT] ?: defaults.weightKg,
            stepGoal = prefs[Keys.STEP_GOAL] ?: defaults.stepGoal,
            waterGoalMl = prefs[Keys.WATER_GOAL] ?: defaults.waterGoalMl,
            calorieGoal = prefs[Keys.CALORIE_GOAL] ?: defaults.calorieGoal,
            heartPointGoal = prefs[Keys.HEART_POINT_GOAL] ?: defaults.heartPointGoal,
            moveMinuteGoal = prefs[Keys.MOVE_MINUTE_GOAL] ?: defaults.moveMinuteGoal,
            weeklyStepGoal = prefs[Keys.WEEKLY_STEP_GOAL] ?: defaults.weeklyStepGoal,
            weeklyHeartPointGoal = prefs[Keys.WEEKLY_HEART_POINT_GOAL] ?: defaults.weeklyHeartPointGoal,
            defaultCupMl = prefs[Keys.DEFAULT_CUP] ?: defaults.defaultCupMl,
            trackerEnabled = prefs[Keys.TRACKER_ENABLED] ?: defaults.trackerEnabled,
            age = prefs[Keys.AGE] ?: defaults.age,
            smoker = prefs[Keys.SMOKER] ?: defaults.smoker,
            sex = prefs[Keys.SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: defaults.sex,
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
            prefs[Keys.DEFAULT_CUP] = value.defaultCupMl.coerceIn(50, 1_000)
            prefs[Keys.AGE] = value.age.coerceIn(UserSettings.AGE_RANGE)
            prefs[Keys.SMOKER] = value.smoker
            prefs[Keys.SEX] = value.sex.name
        }
    }

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
    suspend fun setDefaultCup(value: Int) = putInt(Keys.DEFAULT_CUP, value.coerceIn(50, 1_000))

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
