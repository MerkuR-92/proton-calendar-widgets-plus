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

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.uitest.robot.Robot
import me.proton.android.calendar.uitest.rule.AtlasEnvironmentRule
import me.proton.android.calendar.uitest.rule.HiltInjectRule
import me.proton.android.calendar.uitest.rule.LogoutAllRule
import me.proton.android.calendar.uitest.rule.MainInitializerRule
import me.proton.android.calendar.uitest.rule.SharedPreferencesRule
import me.proton.android.calendar.uitest.rule.TimeZoneRule
import me.proton.core.auth.presentation.testing.ProtonTestEntryPoint
import me.proton.core.test.quark.Quark
import me.proton.core.test.quark.data.User.Users
import me.proton.core.util.kotlin.EMPTY_STRING
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.deserializeList
import me.proton.test.fusion.FusionConfig.targetContext
import me.proton.test.fusion.ui.espresso.EspressoWaiter
import me.proton.test.fusion.ui.espresso.wrappers.EspressoAssertions
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

open class BaseTest: EspressoWaiter {

    open val sharedPreferences = arrayOf(
        SharedPreferencesKeys.LAST_SPOTLIGHT_SHOWN to Int.MAX_VALUE as Any
    )

    open val host = "proton.black"

    open val proxyToken = EMPTY_STRING

    open val timeZone: TimeZone get() = TimeZone.getTimeZone("GMT+2")

    private val hiltRule get() = HiltAndroidRule(this)

    private val atlasRule get() = AtlasEnvironmentRule(host, proxyToken)

    open val baseChain: RuleChain =
        RuleChain
            .outerRule(hiltRule)
            .around(MainInitializerRule(targetContext))
            .around(HiltInjectRule(hiltRule))
            .around(LogoutAllRule)
            .around(atlasRule)
            .around(TimeZoneRule(timeZone))
            .around(SharedPreferencesRule(sharedPreferences))

    private val protonTestEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext<Application>(),
            ProtonTestEntryPoint::class.java,
        )
    }

    val loginTestHelper by lazy { protonTestEntryPoint.loginTestHelper }

    @get:Rule
    val ruleChain: RuleChain
        get() = baseChain.around(ActivityScenarioRule(MainActivity::class.java))

    val users = Users(
        InstrumentationRegistry.getInstrumentation().context
            .assets
            .open("users.json")
            .bufferedReader()
            .use { it.readText() }
            .deserializeList())

    val quark
        get() = Quark(
            host = atlasRule.host,
            proxyToken = atlasRule.proxyToken,
            InstrumentationRegistry.getInstrumentation().context
                .assets
                .open("internal_api.json")
                .bufferedReader()
                .use { it.readText() }
                .deserialize())

    fun <T : Robot> T.verify(
        timeout: Duration = 10000.milliseconds,
        block: T.() -> EspressoAssertions
    ): T = waitFor(timeout) { block() }

    @Before
    fun jailUnban() {
        quark.jailUnban()
    }
}
