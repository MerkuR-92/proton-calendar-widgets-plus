package me.proton.android.calendar.mocks

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.util.kotlin.toBoolean
import me.proton.core.util.kotlin.toInt

object CalendarMocks {

    fun provideCalendarEntity(id: String = calendarId): CalendarEntity {
        return CalendarEntity(
            id = id,
            name = calendarName,
            description = calendarDescription,
            type = calendarType,
            fkUserId = userId.id
        )
    }

    fun provideCalendarSettingsEntity(id: String = calendarId): CalendarSettingsEntity {
        return CalendarSettingsEntity(
            id = calendarSettingsId,
            calendarId = id,
            defaultEventDuration = defaultEventDuration,
            defaultPartDayNotifications = listOf(Json.decodeFromString<JsonElement>("{\"Type\":1,\"Trigger\":\"-PT15M\"}")), // One alarm 15 minutes before
            defaultFullDayNotifications = listOf(Json.decodeFromString<JsonElement>("{\"Type\":1,\"Trigger\":\"-PT15H\"}")) // One day before at 9am
        )
    }

    fun provideCalendarUserSettingsEntity(): CalendarUserSettingsEntity {
        return CalendarUserSettingsEntity(
            fkUserId = userId.id,
            weekLength = weekLength,
            displayWeekNumber = displayWeekNumber,
            autoDetectPrimaryTimezone = autoDetectPrimaryTimezone,
            primaryTimezone = defaultTimezone,
            displaySecondaryTimezone = 0, // TODO
            secondaryTimezone = null, // TODO
            viewPreference = viewPreference,
            defaultCalendarId = calendarId,
            autoImportInvite = 0
        )
    }

    fun provideCalendar(hasDisabledCalendar: Boolean = false, isHidden: Boolean = false): Calendar {
        return Calendar(
            calendarId,
            calendarName,
            calendarColor,
            if (hasDisabledCalendar) MemberEntity.CalendarFlags.DISABLED.value else calendarFlags,
            if (isHidden) false else calendarDisplay.toBoolean(),
            calendarType
        )
    }

    fun provideMemberEntity(memberEmail: String = userEmail, flags: Int = calendarFlags): MemberEntity {
        return MemberEntity(
            id = memberId,
            permissions = MemberEntity.Permission.SUPEROWNER.value,
            email = memberEmail,
            calendarId = calendarId,
            color = calendarColor,
            display = calendarDisplay,
            flags = flags
        )
    }
}
