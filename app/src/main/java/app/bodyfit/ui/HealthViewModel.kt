package app.bodyfit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.bodyfit.data.DailyRecord
import app.bodyfit.data.Dates
import app.bodyfit.data.HealthRepository
import app.bodyfit.data.HourlyRecord
import app.bodyfit.data.Sex
import app.bodyfit.data.UserSettings
import app.bodyfit.data.WaterEntry
import app.bodyfit.sensor.StepTrackerService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for every screen. One view model keeps the four tabs looking at the same day,
 * and [onResumed] moves them all forward when the app is opened after midnight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository(application)
    private val dateKey = MutableStateFlow(Dates.today())

    /**
     * The day every screen is showing. Exposed so composables take the date as an input
     * instead of reading the clock themselves, which makes their derived values cacheable
     * and stops them going stale when the day rolls over.
     */
    val activeDate: StateFlow<String> = dateKey.asStateFlow()

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val today: StateFlow<DailyRecord> = dateKey
        .flatMapLatest { repository.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyRecord(date = Dates.today()))

    val week: StateFlow<List<DailyRecord>> = dateKey
        .flatMapLatest { repository.observeWeek(Dates.parse(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whole history, for streaks and the health score. */
    val allDays: StateFlow<List<DailyRecord>> = repository.observeAllDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val waterEntries: StateFlow<List<WaterEntry>> = dateKey
        .flatMapLatest { repository.observeWaterEntries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The day the trends screen is breaking down by hour, or null while no bar is tapped.
     *
     * Kept apart from [dateKey] because it follows a tap on the chart, which can land on
     * any day of the window, while every other screen stays on the active date.
     */
    private val hourlyDate = MutableStateFlow<String?>(null)

    /** The 24 hours of [hourlyDate], gaps filled. Empty while no day is chosen. */
    val hourly: StateFlow<List<HourlyRecord>> = hourlyDate
        .flatMapLatest { date -> date?.let { repository.observeHours(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Drinks logged on [hourlyDate], the source of the water bars in the hourly chart. */
    val hourlyWater: StateFlow<List<WaterEntry>> = hourlyDate
        .flatMapLatest { date -> date?.let { repository.observeWaterEntries(it) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showHoursFor(date: String?) {
        hourlyDate.value = date
    }

    init {
        // Without this the screens stay on yesterday for as long as the app is left open,
        // while the tracker keeps writing to today's row. Waking on the boundary itself
        // rather than polling means one wake-up a day.
        viewModelScope.launch {
            while (true) {
                delay(Dates.millisUntilTomorrow())
                dateKey.value = Dates.today()
            }
        }
    }

    fun onResumed() {
        val current = Dates.today()
        if (dateKey.value != current) dateKey.value = current
    }

    fun logWater(amountMl: Int) = viewModelScope.launch {
        repository.logWater(amountMl, dateKey.value)
    }

    fun undoWater(entry: WaterEntry) = viewModelScope.launch {
        repository.undoWater(entry)
    }

    fun setStepGoal(value: Int) = viewModelScope.launch { repository.userSettings.setStepGoal(value) }
    fun setWaterGoal(value: Int) = viewModelScope.launch { repository.userSettings.setWaterGoal(value) }
    fun setCalorieGoal(value: Int) = viewModelScope.launch { repository.userSettings.setCalorieGoal(value) }
    fun setHeartPointGoal(value: Int) = viewModelScope.launch { repository.userSettings.setHeartPointGoal(value) }
    fun setMoveMinuteGoal(value: Int) = viewModelScope.launch { repository.userSettings.setMoveMinuteGoal(value) }
    fun setWeeklyStepGoal(value: Int) = viewModelScope.launch { repository.userSettings.setWeeklyStepGoal(value) }
    fun setWeeklyHeartPointGoal(value: Int) = viewModelScope.launch {
        repository.userSettings.setWeeklyHeartPointGoal(value)
    }

    fun setHeight(value: Int) = viewModelScope.launch { repository.userSettings.setHeightCm(value) }
    fun setAge(value: Int) = viewModelScope.launch { repository.userSettings.setAge(value) }
    fun setSmoker(value: Boolean) = viewModelScope.launch { repository.userSettings.setSmoker(value) }
    fun setSex(value: Sex) = viewModelScope.launch { repository.userSettings.setSex(value) }
    fun setWeight(value: Int) = viewModelScope.launch { repository.userSettings.setWeightKg(value) }
    fun setDefaultCup(value: Int) = viewModelScope.launch { repository.userSettings.setDefaultCup(value) }

    /** Serialises everything to JSON for the backup file. */
    suspend fun backupJson(): String = repository.backupJson()

    /** Writes a chosen backup file back in. Returns the number of days restored. */
    suspend fun restoreJson(json: String): Int = repository.restoreJson(json)

    /** Turning the tracker off stops the service, which also removes the lock-screen card. */
    fun setTrackerEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.userSettings.setTrackerEnabled(enabled)
        val context = getApplication<Application>()
        if (enabled) StepTrackerService.start(context) else StepTrackerService.stop(context)
    }
}
