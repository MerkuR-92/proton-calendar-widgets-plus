package me.proton.android.calendar.mocks

import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity

object CalendarMocks {

    fun getCalendarEntity(): CalendarEntity {
        return CalendarEntity(
            id = calendarId,
            name = calendarName,
            description = calendarDescription,
            color = calendarColor,
            display = calendarDisplay,
            flags = calendarFlags,
            type = calendarType,
            fkUserId = userId.id
        )
    }

    fun getCalendarSettingsEntity(): CalendarSettingsEntity {
        return CalendarSettingsEntity(
            id = calendarSettingsId,
            calendarId = calendarId,
            defaultEventDuration = defaultEventDuration,
            defaultPartDayNotifications = emptyList(), // TODO Test default part day notifications
            defaultFullDayNotifications = emptyList() // TODO Test default full day notifications
        )
    }

    fun getCalendarUserSettingsEntity(): CalendarUserSettingsEntity {
        return CalendarUserSettingsEntity(
            fkUserId = userId.id,
            weekLength = weekLength,
            displayWeekNumber = displayWeekNumber,
            autoDetectPrimaryTimezone = autoDetectPrimaryTimezone,
            primaryTimezone = defaultTimezone,
            displaySecondaryTimezone = 0, // TODO
            secondaryTimezone = null, // TODO
            viewPreference = viewPreference,
            defaultCalendarId = calendarId
        )
    }
}
