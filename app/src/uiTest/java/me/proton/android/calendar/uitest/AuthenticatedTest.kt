package me.proton.android.calendar.uitest

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.userLoginRule
import me.proton.core.test.quark.data.User
import org.junit.Before
import org.junit.rules.RuleChain
import kotlin.time.Duration.Companion.seconds

@HiltAndroidTest
open class AuthenticatedTest : BaseTest() {

    open val userLoginRule = userLoginRule(User(), true)

    override val baseChain: RuleChain
        get() = super.baseChain.around(userLoginRule)

    @Before
    fun waitForLogin() {
        HomeRobot.verify(30.seconds) {
            robotDisplayed()
        }
    }
}