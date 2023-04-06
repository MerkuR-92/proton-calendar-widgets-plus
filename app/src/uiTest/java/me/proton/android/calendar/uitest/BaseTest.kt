/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.calendar.uitest

import androidx.test.ext.junit.rules.ActivityScenarioRule
import dagger.hilt.android.testing.HiltAndroidRule
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.uitest.rule.HiltInjectRule
import me.proton.android.calendar.uitest.rule.MainInitializerRule
import me.proton.core.auth.domain.testing.LoginTestHelper
import me.proton.core.test.quark.Quark
import me.proton.core.test.quark.data.User.Users
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

open class BaseTest {

    @get:Rule(order = RuleOrder_00_First)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = RuleOrder_10_Initialization)
    val mainInitializerRule = MainInitializerRule()

    @get:Rule(order = RuleOrder_20_Injection)
    val hiltInjectRule = HiltInjectRule(hiltRule)

    @get:Rule(order = RuleOrder_30_ActivityLaunch)
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Inject
    lateinit var loginTestHelper: LoginTestHelper

    @Before
    open fun setup() {
        loginTestHelper.logoutAll()
    }

    @After
    open fun cleanup() {
        loginTestHelper.logoutAll()
    }

    companion object {
        const val RuleOrder_00_First = 0
        const val RuleOrder_10_Initialization = 10
        const val RuleOrder_11_Initialized = 11
        const val RuleOrder_20_Injection = 20
        const val RuleOrder_21_Injected = 21
        const val RuleOrder_30_ActivityLaunch = 30
        const val RuleOrder_31_ActivityLaunched = 31
        const val RuleOrder_99_Last = 99

        val users = Users.fromDefaultResources()
        val quark = Quark.fromDefaultResources(BuildConfig.QUARK_HOST, BuildConfig.PROXY_TOKEN)
    }
}
