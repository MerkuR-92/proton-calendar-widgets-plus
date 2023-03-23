package me.proton.android.calendar.presentation.holidayCalendar.viewModel

import android.app.Application
import android.graphics.Color
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import biweekly.component.VAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.usecase.JoinCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HolidayCalendarViewModel @Inject constructor(
    application: Application,
    private val resourceProvider: ResourceProvider,
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val accountManager: AccountManager,
    private val joinCalendarUseCase: JoinCalendarUseCase
) : AndroidViewModel(application) {

    sealed class HolidayCalendarSnackState {

        data class DisplaySnack(
            val message: String,
        ): HolidayCalendarSnackState()

        data class DisplaySnackNavigateUp(
            val message: String,
        ): HolidayCalendarSnackState()
    }

    val holidayCalendarSnackState: MutableStateFlow<HolidayCalendarSnackState?> = MutableStateFlow(null)
    val calendarSettingsSnackState: MutableStateFlow<HolidayCalendarSnackState?> = MutableStateFlow(null)

    sealed class HolidayCalendarState {

        object Idle: HolidayCalendarState()

        sealed class Processing: HolidayCalendarState() {
            object Saving: Processing()
        }
    }

    val holidayCalendarState: MutableStateFlow<HolidayCalendarState> = MutableStateFlow(HolidayCalendarState.Idle)

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private val _country = MutableLiveData("")
    val country: LiveData<String> = _country

    private val _calendarColor = MutableLiveData<Int>()
    val calendarColor: LiveData<Int> = _calendarColor

    private val _language = MutableLiveData<String>()
    val language: LiveData<String> = _language

    private val _defaultAllDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultAllDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultAllDayAlarms

    private val _holidayCalendars: MutableLiveData<List<ManagedHolidayCalendarEntity>> = MutableLiveData()
    val holidayCalendars: LiveData<List<ManagedHolidayCalendarEntity>> = _holidayCalendars

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var _calendarId: String? = null

    private var calendarEdited = false

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun resetValues() {
        _country.value = ""
        _language.value = ""
        _calendarColor.value = 0
        _defaultAllDayAlarms.value = arrayListOf()
        _calendarId = null
        holidayCalendarSnackState.value = null
        calendarSettingsSnackState.value = null
        calendarEdited = false
    }

    suspend fun initUpdateHolidayCalendar(calendarId: String) {

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in HolidayCalendarViewModel initUpdateHolidayCalendar")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        _calendarId = calendarId

        val calendar = getCalendar(calendarId) ?: run {
            logger.e("Calendar was null in initUpdateHolidayCalendar")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }

        // Calendar name
        _country.value = calendar.name

        // Calendar language
        _language.value = "" // TODO GET LANGUAGE FROM SOMEWHERE ?

        // Calendar color
        _calendarColor.value = Color.parseColor(calendar.color)

        // Default all day event notifications
        setDefaultAlarms(calendar.defaultFullDayNotifications)
    }

    suspend fun initCreateHolidayCalendar(calendarColor: Int, defaultLanguageCode: String) {

        // Set default calendar color (picked randomly from the colors array)
        _calendarColor.value = calendarColor

        // Set default all day event notifications (1 day before at 9am)
        _defaultAllDayAlarms.value = arrayListOf()

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in HolidayCalendarViewModel initCreateHolidayCalendar")
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        val primaryTimezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone ?: ZoneId.systemDefault().id

        getManagedHolidayCalendars()?.let { holidayCalendars ->
            _holidayCalendars.value = holidayCalendars

            // Get calendars matching the default time zone
            val countriesMatchingTimeZone = holidayCalendars.filter {
                it.timezones.contains(primaryTimezone)
            }
            // Get the calendar matching the default language
            val matchingDefaultHolidayCalendar = countriesMatchingTimeZone.firstOrNull {
                it.languageCode.equals(defaultLanguageCode, ignoreCase = true)
            } ?: countriesMatchingTimeZone.firstOrNull()
            _country.value = matchingDefaultHolidayCalendar?.country ?: ""
            _language.value = matchingDefaultHolidayCalendar?.language ?: ""
        }
    }

    fun getLanguages(): List<String> {
        return _holidayCalendars.value?.filter { it.country == _country.value }?.map { it.language } ?: emptyList()
    }

    fun hasBeenEdited(): Boolean {
        return calendarEdited
    }

    private fun setDefaultAlarms(defaultNotifications: List<Notification>) {
        val alarms = arrayListOf<VAlarm>().apply { addAll(defaultNotifications.map { it.toVAlarm() }) }

        _defaultAllDayAlarms.value = alarms
    }

    fun handleAlarmChange(alarm: VAlarm, isDelete: Boolean = false) {
        val tmpDefaultAllDayAlarms = _defaultAllDayAlarms.value

        if (isDelete) tmpDefaultAllDayAlarms?.remove(alarm) // Remove the alarm from the list
        else {
            // Check if alarm already exist in the list
            if (tmpDefaultAllDayAlarms?.contains(alarm) == true || tmpDefaultAllDayAlarms?.any { it.isTheSameAs(alarm) } == true) {
                holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_notification_already_added)
                )
                return
            }
            // Add the alarm to the list
            tmpDefaultAllDayAlarms?.add(alarm)
        }
        // Update LiveData value to trigger changes in view
        _defaultAllDayAlarms.value = tmpDefaultAllDayAlarms
    }

    fun handleCountry(country: String, defaultLanguageCode: String) {
        if (_country.value == country) return
        calendarEdited = true
        _country.value = country

        // Get the calendar matching the default language
        val matchingCountries = holidayCalendars.value?.filter { it.country == country }
        val matchingDefaultHolidayCalendar = matchingCountries?.firstOrNull {
            it.languageCode.equals(defaultLanguageCode, ignoreCase = true)
        } ?: matchingCountries?.firstOrNull()
        _language.value = matchingDefaultHolidayCalendar?.language ?: ""
    }

    fun handleLanguage(language: String) {
        if (_language.value == language) return
        calendarEdited = true
        _language.value = language
    }

    fun handleCalendarColor(calendarColor: Int) {
        if (_calendarColor.value == calendarColor) return
        calendarEdited = true
        _calendarColor.value = calendarColor
    }

    suspend fun handleSaveHolidayCalendar(returnToSettings: Boolean, selectedDate: LocalDate?): Boolean {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in HolidayCalendarViewModel getCalendar")
            return false
        }

        val holidayCalendar = holidayCalendars.value?.firstOrNull {
            it.country == _country.value && it.language == _language.value
        } ?: return false // TODO Handle

        val calendarColor = _calendarColor.value ?: return false // TODO Handle

        // Set loading state
        holidayCalendarState.value = HolidayCalendarState.Processing.Saving

        // Fetch shared calendar events
        val displayTimeZoneId = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id

        val joinCalendarResult = joinCalendarUseCase.joinHolidayCalendar(
            userId,
            holidayCalendar,
            calendarColor,
            _defaultAllDayAlarms.value,
            selectedDate ?: LocalDate.now(ZoneId.of(displayTimeZoneId)),
            displayTimeZoneId
        )

        // Clear loading state
        holidayCalendarState.value = HolidayCalendarState.Idle

        if (joinCalendarResult !is UseCase.Result.Success<*>) {
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_create_calendar_error)
            )
            return false
        }

        if (returnToSettings) {
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_create_calendar_success)
            )
        } else {
            // Use holiday calendar snack state here to display snack in month view
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_create_calendar_success)
            )
        }
        return true
    }

    private suspend fun getCalendar(calendarId: String): Calendar? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in HolidayCalendarViewModel getCalendar")
            return null
        }
        return calendarsRepository.selectCalendar(calendarId)
    }

    private suspend fun getManagedHolidayCalendars(): List<ManagedHolidayCalendarEntity>? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in HolidayCalendarViewModel getCalendar")
            return null
        }
        return calendarsRepository.getManagedHolidayCalendars(userId)
    }
}
