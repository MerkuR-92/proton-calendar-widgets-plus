package me.proton.android.calendar.uitest

import androidx.test.espresso.NoMatchingViewException
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.userLoginRule
import me.proton.core.test.quark.data.User
import me.proton.core.util.kotlin.CoreLogger
import org.junit.Before
import org.junit.rules.RuleChain
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

@HiltAndroidTest
open class AuthenticatedTest : BaseTest() {

    open val userLoginRule = userLoginRule(User(), true)

    override val baseChain: RuleChain
        get() = super.baseChain.around(userLoginRule)

    @Before
    fun waitForLogin() {
        try {
            HomeRobot.verify(60.seconds) {
                robotDisplayed()
            }
        } catch (ex: NoMatchingViewException) {
            error("Could not log in before test")
        }
    }
}