package me.proton.android.calendar.uitest.e2e.core.settings

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import me.proton.android.calendar.uitest.BaseTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.NotificationPermissionRule
import me.proton.core.accountrecovery.dagger.CoreAccountRecoveryFeaturesModule
import me.proton.core.accountrecovery.domain.IsAccountRecoveryEnabled
import me.proton.core.accountrecovery.domain.IsAccountRecoveryResetEnabled
import me.proton.core.usersettings.test.MinimalUserSettingsTest
import me.proton.test.fusion.FusionConfig
import org.junit.Before
import org.junit.Rule
import kotlin.time.Duration.Companion.seconds

@HiltAndroidTest
@UninstallModules(CoreAccountRecoveryFeaturesModule::class)
class AccountSettingsFlowTest : BaseTest(), MinimalUserSettingsTest {

    @get:Rule(order = Rule.DEFAULT_ORDER - 2)
    val grantPermissionRule = NotificationPermissionRule()

    @get:Rule(order = Rule.DEFAULT_ORDER - 1)
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    @BindValue
    internal val isAccountRecoveryEnabled = mockk<IsAccountRecoveryEnabled> {
        every { this@mockk.invoke(any()) } returns true
    }

    @BindValue
    internal val isAccountRecoveryResetEnabled = mockk<IsAccountRecoveryResetEnabled> {
        every { this@mockk.invoke(any()) } returns true
    }

    init {
        FusionConfig.Compose.testRule.set(composeTestRule)
    }

    @Before
    fun preventHumanVerification() {
        quark.jailUnban()
    }

    private fun startAccountSettings() = HomeRobot
        .verify(60.seconds) { robotDisplayed() }
        .clickHamburgerButton()
        .clickSettings()
        .clickAccount()

    override fun startPasswordManagement() {
        startAccountSettings().clickPasswordManagement()
    }

    override fun startRecoveryEmail() {
        startAccountSettings().clickRecoveryEmail()
    }
}
