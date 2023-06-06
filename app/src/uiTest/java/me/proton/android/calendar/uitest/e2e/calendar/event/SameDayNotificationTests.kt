/*
 * Copyright (c) 2023 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

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
            .apply {
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
