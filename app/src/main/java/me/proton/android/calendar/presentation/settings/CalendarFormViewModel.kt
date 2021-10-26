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
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.core.network.domain.NetworkManager

class CalendarFormViewModel(
    application: Application,
    private val networkManager: NetworkManager,
    private val json: Json,
    private val resourceProvider: ResourceProvider
) : AndroidViewModel(application) {

    private val intents = mutableMapOf<String, Intent>()

    val isConnectedToNetwork get() = networkManager.isConnectedToNetwork()

    sealed class CalendarFormSnackState {

        data class DisplaySnack(
            val message: String,
        ): CalendarFormSnackState()

        data class DisplaySnackReturnToMonth(
            val message: String,
        ): CalendarFormSnackState()
    }

    val calendarFormSnackState: MutableStateFlow<CalendarFormSnackState?> = MutableStateFlow(null)

    private val _calendarColor = MutableLiveData<String>()
    val calendarColor: LiveData<String> = _calendarColor

    private val _defaultEventDuration = MutableLiveData<Int>()
    val defaultEventDuration: LiveData<Int> = _defaultEventDuration

    private val _defaultPartDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultPartDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultPartDayAlarms

    private val _defaultAllDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultAllDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultAllDayAlarms

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun handleAlarmChange(alarm: VAlarm, isAllDay: Boolean, isDelete: Boolean = false) {
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

    fun setDefaultAlarms(defaultNotifications: List<JsonElement>, isAllDay: Boolean) {
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

    fun handleCalendarColor(calendarColor: String) {
        _calendarColor.value = calendarColor
    }

    fun handleDefaultEventDuration(defaultEventDuration: Int) {
        _defaultEventDuration.value = defaultEventDuration
    }
}
