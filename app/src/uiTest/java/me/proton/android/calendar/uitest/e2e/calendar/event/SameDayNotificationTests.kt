package me.proton.android.calendar.uitest.e2e.calendar.event

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.AuthenticatedTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.robot.NotificationRobot.Notification
import me.proton.android.calendar.uitest.rule.userLoginRule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@HiltAndroidTest
class SameDayNotificationTests(
    private val notification: Notification
) : AuthenticatedTest() {

    override val userLoginRule: ExternalResource
        get() = userLoginRule(users.getUser(), false)

    @Test
    fun notificationIsAdded() {
        HomeRobot
            .clickAddEvent()
            .closeKeyboard()
            .clickAddNotification()
            .clickNotification(notification)
            .clickDone()
            .verify {
                notificationIsDisplayed(notification)
            }
    }

    companion object {
        @get:Parameterized.Parameters(name = "{0}")
        @get:JvmStatic
        val data = listOf(
            Notification.AtTimeOfEvent,
            Notification.TenMinutes,
            Notification.ThirtyMinutes,
            Notification.OneHour,
            Notification.OneWeek,
        )
    }
}
