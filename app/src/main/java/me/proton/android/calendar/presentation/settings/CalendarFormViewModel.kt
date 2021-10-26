package me.proton.android.calendar.presentation.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import biweekly.component.VAlarm
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.R
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.NetworkManager

class CalendarFormViewModel(
    application: Application,
    private val networkManager: NetworkManager,
    private val json: Json,
    private val resourceProvider: ResourceProvider,
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase
) : AndroidViewModel(application) {

    private val intents = mutableMapOf<String, Intent>()

    val isConnectedToNetwork get() = networkManager.isConnectedToNetwork()

    sealed class CalendarFormSnackState {

        data class DisplaySnack(
            val message: String,
        ): CalendarFormSnackState()

        data class DisplaySnackNavigateUp(
            val message: String,
        ): CalendarFormSnackState()
    }

    val calendarFormSnackState: MutableStateFlow<CalendarFormSnackState?> = MutableStateFlow(null)
    val calendarSettingsSnackState: MutableStateFlow<CalendarFormSnackState?> = MutableStateFlow(null)

    sealed class CalendarFormState {

        object Idle: CalendarFormState()

        sealed class Processing: CalendarFormState() {
            object Saving: Processing()
        }
    }

    val calendarFormState: MutableStateFlow<CalendarFormState> = MutableStateFlow(CalendarFormState.Idle)

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private val _calendarName = MutableLiveData<String>()
    val calendarName: LiveData<String> = _calendarName

    private val _calendarEmail = MutableLiveData<String>()
    val calendarEmail: LiveData<String> = _calendarEmail

    private val _calendarColor = MutableLiveData<String>()
    val calendarColor: LiveData<String> = _calendarColor

    private val _defaultEventDuration = MutableLiveData<Int>()
    val defaultEventDuration: LiveData<Int> = _defaultEventDuration

    private val _defaultPartDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultPartDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultPartDayAlarms

    private val _defaultAllDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultAllDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultAllDayAlarms

    private var _calendarId: String? = null

    private var calendarEdited = false
    private var calendarSettingsEdited = false

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    suspend fun initUpdateCalendarForm(userId: UserId, calendarId: String) {

        _calendarId = calendarId
        _userId.value = userId

        val calendarEntity = getCalendarEntity(calendarId) ?: run {
            logger.e("CalendarEntity was null in initUpdateCalendarForm")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }

        val calendarSettings = getCalendarSettings(calendarId) ?: run {
            logger.e("CalendarSettings was null in initUpdateCalendarForm")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }

        val calendarEmail = calendarsRepository.selectMembers(calendarId).firstOrNull {
            it.hasPermission(MemberEntity.Permission.SUPEROWNER)
        }?.email

        // Calendar name
        handleCalendarName(calendarEntity.name)

        // Calendar default email (can't be updated in form)
        _calendarEmail.value = calendarEmail ?: ""

        // Calendar color
        handleCalendarColor(calendarEntity.color)

        // Default event duration
        handleDefaultEventDuration(calendarSettings.defaultEventDuration)

        // Default part day event notifications
        setDefaultAlarms(calendarSettings.defaultPartDayNotifications, isAllDay = false)

        // Default all day event notifications
        setDefaultAlarms(calendarSettings.defaultFullDayNotifications, isAllDay = true)

        // Make sure to set those values to false after having initialized the form with existing values
        calendarEdited = false
        calendarSettingsEdited = false
    }

    suspend fun initCreateCalendarForm(userId: UserId) {
        // TODO
    }

    fun hasFormBeenEdited(): Boolean {
        return calendarEdited || calendarSettingsEdited
    }

    fun handleAlarmChange(alarm: VAlarm, isAllDay: Boolean, isDelete: Boolean = false) {
        calendarSettingsEdited = true
        if (isAllDay) {
            val tmpDefaultAllDayAlarms = _defaultAllDayAlarms.value

            if (isDelete) tmpDefaultAllDayAlarms?.remove(alarm) // Remove the alarm from the list
            else {
                // Check if alarm already exist in the list
                if (tmpDefaultAllDayAlarms?.contains(alarm) == true) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_notification_already_added)
                    )
                    return
                }
                // Add the alarm to the list
                tmpDefaultAllDayAlarms?.add(alarm)
            }
            // Update LiveData value to trigger changes in view
            _defaultAllDayAlarms.value = tmpDefaultAllDayAlarms

        } else {
            val tmpDefaultPartDayAlarms = _defaultPartDayAlarms.value

            if (isDelete) tmpDefaultPartDayAlarms?.remove(alarm) // Remove the alarm from the list
            else {
                // Check if alarm already exist in the list
                if (tmpDefaultPartDayAlarms?.contains(alarm) == true) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_notification_already_added)
                    )
                    return
                }
                // Add the alarm to the list
                tmpDefaultPartDayAlarms?.add(alarm)
            }
            // Update LiveData value to trigger changes in view
            _defaultPartDayAlarms.value = tmpDefaultPartDayAlarms
        }
    }

    private fun setDefaultAlarms(defaultNotifications: List<JsonElement>, isAllDay: Boolean) {
        val alarms = ArrayList<VAlarm>()
        defaultNotifications.mapNotNull {
            if ((it as? JsonObject) != null) json.decodeFromJsonElement<CalendarSettingsEntity.AlarmEntity>(
                it
            ) else null
        }.forEach { alarm ->
            alarm.parseTrigger()?.let {
                if (alarm.type == 0) {
                    alarms.add(VAlarm.email(it, null, null))
                } else {
                    alarms.add(VAlarm.display(it, null))
                }
            }
        }
        if (isAllDay) _defaultAllDayAlarms.value = alarms
        else _defaultPartDayAlarms.value = alarms
    }

    fun handleCalendarName(calendarName: String) {
        if (_calendarName.value == calendarName) return
        calendarEdited = true
        _calendarName.value = calendarName
    }

    fun handleCalendarColor(calendarColor: String) {
        if (_calendarColor.value == calendarColor) return
        calendarEdited = true
        _calendarColor.value = calendarColor
    }

    fun handleDefaultEventDuration(defaultEventDuration: Int) {
        if (_defaultEventDuration.value == defaultEventDuration) return
        calendarSettingsEdited = true
        _defaultEventDuration.value = defaultEventDuration
    }

    suspend fun handleSaveCalendarForm() {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarFormViewModel handleSaveCalendarForm")
            return
        }
        _calendarId?.let { calendarId ->
            // Update existing calendar

            // Set loading state
            calendarFormState.value = CalendarFormState.Processing.Saving

            if (calendarEdited) {
                // Update calendar entity
                val updateCalendarUseCaseResult = updateCalendarUseCase.executeUpdate(
                    userId,
                    calendarId,
                    name = _calendarName.value,
                    color = _calendarColor.value
                )
                if (updateCalendarUseCaseResult !is UseCase.Result.Success<*>) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_update_calendar_error)
                    )
                    return
                }
            }

            if (calendarSettingsEdited) {
                // Update calendar settings
                val updateCalendarSettingsUseCaseResult = updateCalendarSettingsUseCase.updateCalendarSettings(
                    userId,
                    calendarId,
                    _defaultEventDuration.value,
                    _defaultPartDayAlarms.value,
                    _defaultAllDayAlarms.value
                )
                if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*>) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(
                            if (calendarEdited) R.string.snack_update_calendar_settings_error
                            else R.string.snack_update_calendar_error
                        )
                    )
                }
            }

            // Clear loading state
            calendarFormState.value = CalendarFormState.Idle

            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_update_calendar_success)
            )
        } ?: run {
            // Create new calendar

            // Set loading state
            calendarFormState.value = CalendarFormState.Processing.Saving

            // TODO

            // Clear loading state
            calendarFormState.value = CalendarFormState.Idle
        }
    }

    private suspend fun getCalendarEntity(calendarId: String): CalendarEntity? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarFormViewModel getCalendarEntity")
            return null
        }
        return calendarsRepository.selectCalendar(calendarId)
    }

    private suspend fun getCalendarSettings(calendarId: String): CalendarSettingsEntity? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarFormViewModel getDefaultCalendarSettings")
            return null
        }
        return calendarsRepository.selectCalendarSettings(calendarId)
    }
}
