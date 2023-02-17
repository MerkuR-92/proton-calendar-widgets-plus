package me.proton.android.calendar.presentation.holidays.viewModel

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
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

@HiltViewModel
class HolidaysViewModel @Inject constructor(
    application: Application,
    private val resourceProvider: ResourceProvider,
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val userManager: UserManager,
    private val accountManager: AccountManager,
    private val createCalendarUseCase: CreateCalendarUseCase
) : AndroidViewModel(application) {

    sealed class HolidaysSnackState {

        data class DisplaySnack(
            val message: String,
        ): HolidaysSnackState()

        data class DisplaySnackNavigateUp(
            val message: String,
        ): HolidaysSnackState()
    }

    val holidaysSnackState: MutableStateFlow<HolidaysSnackState?> = MutableStateFlow(null)
    val calendarSettingsSnackState: MutableStateFlow<HolidaysSnackState?> = MutableStateFlow(null)

    sealed class HolidaysState {

        object Idle: HolidaysState()

        sealed class Processing: HolidaysState() {
            object Saving: Processing()
        }
    }

    val holidaysState: MutableStateFlow<HolidaysState> = MutableStateFlow(HolidaysState.Idle)

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private val _country = MutableLiveData("")
    val country: LiveData<String> = _country

    private val _calendarColor = MutableLiveData<Int>()
    val calendarColor: LiveData<Int> = _calendarColor

    private val _defaultAllDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultAllDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultAllDayAlarms

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var _calendarId: String? = null

    private var calendarEdited = false
    private var countryEdited = false

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun resetValues() {
        _country.value = ""
        _calendarColor.value = 0
        _defaultAllDayAlarms.value = arrayListOf()
        _calendarId = null
        holidaysSnackState.value = null
        calendarSettingsSnackState.value = null
        calendarEdited = false
    }

    suspend fun initUpdateHolidays(calendarId: String) {

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in HolidaysViewModel initUpdateHolidays")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidaysSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        _calendarId = calendarId

        val calendar = getCalendar(calendarId) ?: run {
            logger.e("Calendar was null in initUpdateHolidays")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidaysSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }

        // Calendar name
        _country.value = calendar.name

        // Calendar color
        _calendarColor.value = Color.parseColor(calendar.color)

        // Default all day event notifications
        setDefaultAlarms(calendar.defaultFullDayNotifications)
    }

    suspend fun initCreateHolidays(calendarColor: Int, deviceDefaultTimeZoneId: String) {
        // TODO Set actual Country
        _country.value = deviceDefaultTimeZoneId

        // Set default calendar color (picked randomly from the colors array)
        _calendarColor.value = calendarColor

        // Set default all day event notifications (1 day before at 9am)
        _defaultAllDayAlarms.value = arrayListOf()

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in HolidaysViewModel initCreateHolidays")
            holidaysSnackState.value = HolidaysSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId
    }

    fun hasBeenEdited(): Boolean {
        return calendarEdited
    }

    fun hasCountryBeenEdited(): Boolean {
        return countryEdited
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
                holidaysSnackState.value = HolidaysSnackState.DisplaySnack(
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

    fun handleCountry(country: String) {
        if (_country.value == country) return
        calendarEdited = true
        countryEdited = true
        _country.value = country
    }

    fun handleCalendarColor(calendarColor: Int) {
        if (_calendarColor.value == calendarColor) return
        calendarEdited = true
        _calendarColor.value = calendarColor
    }

    suspend fun handleSaveHolidays(returnToSettings: Boolean) {
    }

    private suspend fun getCalendar(calendarId: String): Calendar? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in HolidaysViewModel getCalendar")
            return null
        }
        return calendarsRepository.selectCalendar(calendarId)
    }
}
