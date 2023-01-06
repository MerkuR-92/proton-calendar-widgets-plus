package me.proton.android.calendar.data.entity

import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.model.Notification

@Serializable
data class NotificationEntity(
    @SerialName("Type")
    val type: Int, // 0: Email reminder, 1: Desktop reminder
    @SerialName("Trigger")
    val trigger: String // RFC5545 encoded trigger
) {
    fun parseTrigger(): Trigger? {
        return try {
            Trigger(Duration.parse(trigger), Related.START)
        } catch(e: IllegalArgumentException) {
            null
        }
    }

    fun toNotification(): Notification? = parseTrigger()?.let {
        when (type) {
            0 -> {
                Notification.Email(it)
            }
            1 -> {
                Notification.Display(it)
            }
            else -> null
        }
    }

    fun toVAlarm(): VAlarm? = parseTrigger()?.let {
        when (type) {
            0 -> {
                VAlarm.email(it, null, null)
            }
            1 -> {
                VAlarm.display(it, null)
            }
            else -> null
        }
    }

    companion object {
        fun fromNotification(notification: Notification) = when(notification) {
            is Notification.Email -> NotificationEntity(0, notification.trigger.duration.toString())
            is Notification.Display -> NotificationEntity(1, notification.trigger.duration.toString())
        }
    }
}