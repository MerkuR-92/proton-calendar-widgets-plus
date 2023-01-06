package me.proton.android.calendar.domain.model

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.data.entity.getDefaultAlarms
import me.proton.android.calendar.data.entity.getDefaultNotifications
import me.proton.core.util.kotlin.toBoolean

data class Calendar(
        override val id: String,
        val name: String,
        val email: String,
        val color: String,
        val flags: Int,
        val display: Boolean,
        val type: Int,
        val permissions: Int,
        val defaultPartDayNotifications: List<Notification>,
        val defaultFullDayNotifications: List<Notification>
) : BaseModel() {

        companion object {
                fun from(calendarEntity: CalendarEntity, memberEntity: MemberEntity, calendarSettingsEntity: CalendarSettingsEntity, json: Json) = Calendar(
                        id = calendarEntity.id,
                        name = calendarEntity.name,
                        email = memberEntity.email,
                        color = memberEntity.color,
                        flags = memberEntity.flags,
                        display = memberEntity.display.toBoolean(),
                        type = calendarEntity.type,
                        permissions = memberEntity.permissions,
                        defaultPartDayNotifications = calendarSettingsEntity.getDefaultNotifications(json, isAllDay = false),
                        defaultFullDayNotifications = calendarSettingsEntity.getDefaultNotifications(json, isAllDay = true)
                )
        }

        //Functions to check all three states because it can be disabled but not inactive, or inactive but not disabled
        val isActive: Boolean get() = !isDisabled && !isInactive && (flags and 1 == 1)
        val isInactive: Boolean get() = (flags and (0 + 2 + 4 + 8 + 16) >= 1)
        val isDisabled: Boolean get() = !isInactive && (flags and (32 + 64) >= 1)
        val isSuperOwnerDisabled: Boolean get() = flags and 64 == 64
        val hasIncompleteKeySetup: Boolean get() = flags and 8 == 8
        val isResetNeeded: Boolean get() = flags and 4 == 4
        val hasUpdatePassphrase: Boolean get() = flags and 2 == 2

        val isSubscribed: Boolean get() = type == 1
        val isSharedWithMe: Boolean get() = !isOwner

        val isOwner: Boolean get() = permissions and 2 == 2
        val allowEditEvents: Boolean get() = permissions and 2 == 2 // TODO Change to 16 so that we check actual write permissions
}
    // TODO fields need to be duplicated here, plus local metadata added
