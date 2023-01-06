package me.proton.android.calendar.domain.model

import biweekly.component.VAlarm
import biweekly.property.Action
import biweekly.property.Trigger

sealed class Notification(val trigger: Trigger) {
    class Email(trigger: Trigger) : Notification(trigger)
    class Display(trigger: Trigger) : Notification(trigger)

    fun toVAlarm(): VAlarm = if (this is Display) VAlarm.display(trigger, null) else VAlarm.email(trigger, null, null)

    companion object {
        fun fromVAlarm(vAlarm: VAlarm) = if (vAlarm.action.isDisplay) {
            Display(vAlarm.trigger)
        } else if (vAlarm.action.isEmail) {
            Email(vAlarm.trigger)
        } else null
    }
}

/**
 * After migration we can replace this object with List<Notification>?
 */
data class NotificationMigration(
    val isMigrated: Boolean,
    val notifications: List<Notification>?
)