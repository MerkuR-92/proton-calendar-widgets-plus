package me.proton.android.calendar.uitest.e2e.core.plan

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.BaseTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.NotificationPermissionRule
import me.proton.core.auth.test.robot.AddAccountRobot
import me.proton.core.plan.test.MinimalSubscriptionTests
import me.proton.core.test.quark.data.User
import org.junit.rules.RuleChain
import kotlin.time.Duration.Companion.seconds

@HiltAndroidTest
class SubscriptionTests : BaseTest(), MinimalSubscriptionTests {
    override val baseChain: RuleChain = super.baseChain.around(NotificationPermissionRule())


    override fun startSubscription(user: User) {
        AddAccountRobot
            .clickSignIn()
            .login(user)

        HomeRobot.verify(60.seconds) {
            robotDisplayed()
        }

        HomeRobot
            .clickHamburgerButton()
            .clickSubscriptions()
    }
}
