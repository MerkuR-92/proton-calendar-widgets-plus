package me.proton.android.calendar.mocks

import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.util.kotlin.toBoolean

object CalendarMocks {

    fun getCalendarEntity(id: String = calendarId, isDisabled: Boolean = false, isHidden: Boolean = false): CalendarEntity {
        return CalendarEntity(
            id = id,
            name = calendarName,
            description = calendarDescription,
            color = calendarColor,
            display = if (isHidden) 0 else calendarDisplay,
            flags = if (isDisabled) CalendarFlags.DISABLED.value else calendarFlags,
            type = calendarType,
            fkUserId = userId.id
        )
    }

    fun getCalendarSettingsEntity(id: String = calendarId): CalendarSettingsEntity {
        return CalendarSettingsEntity(
            id = calendarSettingsId,
            calendarId = id,
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

    fun getCalendar(hasDisabledCalendar: Boolean = false, isHidden: Boolean = false): Calendar {
        return Calendar(
            calendarId,
            calendarName,
            calendarColor,
            if (hasDisabledCalendar) CalendarFlags.DISABLED.value else calendarFlags,
            if (isHidden) false else calendarDisplay.toBoolean(),
            calendarType
        )
    }

    fun getMemberEntity(memberEmail: String = userEmail): MemberEntity {
        return MemberEntity(
            id = memberId,
            permissions = MemberEntity.Permission.SUPEROWNER.value,
            email = memberEmail,
            calendarId = calendarId
        )
    }
}
