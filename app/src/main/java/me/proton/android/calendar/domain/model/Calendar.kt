package me.proton.android.calendar.domain.model

import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.core.util.kotlin.toBoolean

data class Calendar(
        override val id: String,
        val name: String,
        val email: String,
        val color: String,
        val flags: Int,
        val display: Boolean,
        val type: Int,
        val permissions: Int
) : BaseModel() {

        companion object {
                fun from(calendarEntity: CalendarEntity, memberEntity: MemberEntity) = Calendar(
                        id = calendarEntity.id,
                        name = calendarEntity.name,
                        email = memberEntity.email,
                        color = memberEntity.color,
                        flags = memberEntity.flags,
                        display = memberEntity.display.toBoolean(),
                        type = calendarEntity.type,
                        permissions = memberEntity.permissions
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

        val isOwner: Boolean get() = permissions and 2 == 2
        val allowEditEvents: Boolean get() = permissions and 16 == 16
}
    // TODO fields need to be duplicated here, plus local metadata added
