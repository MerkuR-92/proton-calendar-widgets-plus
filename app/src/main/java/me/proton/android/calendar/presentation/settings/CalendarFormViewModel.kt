package me.proton.android.calendar.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import biweekly.component.VAlarm
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.tryCast
import me.proton.android.calendar.common.CalendarForm.DEFAULT_ALL_DAY_ALARM
import me.proton.android.calendar.common.CalendarForm.DEFAULT_PART_DAY_ALARM
import me.proton.android.calendar.common.CalendarForm.EVENT_DEFAULT_DURATION_MINUTES
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager

class CalendarFormViewModel(
    application: Application,
    private val json: Json,
    private val resourceProvider: ResourceProvider,
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val userManager: UserManager,
    private val accountManager: AccountManager,
    private val createCalendarUseCase: CreateCalendarUseCase
) : AndroidViewModel(application) {

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

    private val _calendarName = MutableLiveData("")
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

    var userEmails: List<String>? = null

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun resetFormValues() {
        _calendarName.value = ""
        _calendarColor.value = ""
        _calendarEmail.value = ""
        _defaultEventDuration.value = EVENT_DEFAULT_DURATION_MINUTES.first()
        _defaultPartDayAlarms.value = arrayListOf()
        _defaultAllDayAlarms.value = arrayListOf()
        _calendarId = null
        calendarFormSnackState.value = null
        calendarSettingsSnackState.value = null
        calendarEdited = false
        calendarSettingsEdited = false
        userEmails = null
    }

    suspend fun initUpdateCalendarForm(calendarId: String) {

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in CalendarFormViewModel initUpdateCalendarForm")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        _calendarId = calendarId

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

        // Calendar default email (can't be updated for existing calendar)
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

    suspend fun initCreateCalendarForm(calendarColor: String) {

        // Set default calendar color (picked randomly from the colors array)
        handleCalendarColor(calendarColor)

        // Set default event duration
        handleDefaultEventDuration(EVENT_DEFAULT_DURATION_MINUTES.first())

        // Set default part day event notification (15 minutes before)
        handleAlarmChange(DEFAULT_PART_DAY_ALARM, false)

        // Set default all day event notification (1 day before at 9am)
        handleAlarmChange(DEFAULT_ALL_DAY_ALARM, true)

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in CalendarFormViewModel initCreateCalendarForm")
            calendarFormSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        // Save user emails for calendar email picker dialog
        userEmails = userManager.getAddresses(userId).filter { it.enabled && it.canSend && it.canReceive }.map { it.email }

        val defaultUserEmail = userManager.getUser(userId).email // TODO Can be null, what do we take next ?
        defaultUserEmail?.let { handleCalendarEmail(defaultUserEmail) }

        // Make sure to set those values to false after having initialized the form with default values
        calendarEdited = false
        calendarSettingsEdited = false
    }

    fun hasFormBeenEdited(): Boolean {
        return calendarEdited || calendarSettingsEdited
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

    fun handleCalendarEmail(calendarEmail: String) {
        if (_calendarEmail.value == calendarEmail) return
        calendarEdited = true
        _calendarEmail.value = calendarEmail
    }

    suspend fun handleSaveCalendarForm(returnToSettings: Boolean) {
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
                    // Clear loading state
                    calendarFormState.value = CalendarFormState.Idle
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

            // Create calendar
            val createDefaultCalendarResult = createCalendarUseCase.execute(
                userId = userId,
                name = _calendarName.value!!,
                color = _calendarColor.value!!,
                email = _calendarEmail.value!!
            )
            if (createDefaultCalendarResult !is UseCase.Result.Success<*>) {
                calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_create_calendar_error)
                )
            }

            if (createDefaultCalendarResult is UseCase.Result.Success<*>) {
                createDefaultCalendarResult.returnValue.tryCast<String> {
                    // Update newly created calendar settings
                    val updateCalendarSettingsUseCaseResult = updateCalendarSettingsUseCase.updateCalendarSettings(
                        userId,
                        this,
                        _defaultEventDuration.value,
                        _defaultPartDayAlarms.value,
                        _defaultAllDayAlarms.value
                    )
                    if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*>) {
                        calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                            resourceProvider.provideString(R.string.snack_create_calendar_error)
                        )
                    }
                }
            }

            if (returnToSettings) {
                // Use settings snack state here to display snack in calendar settings view
                calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_create_calendar_success)
                )
            } else {
                // Use form snack state here to display snack in month view
                calendarFormSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_create_calendar_success)
                )
            }

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
