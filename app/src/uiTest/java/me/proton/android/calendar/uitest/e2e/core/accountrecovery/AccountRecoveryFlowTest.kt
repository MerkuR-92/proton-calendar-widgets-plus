package me.proton.android.calendar.uitest.e2e.core.accountrecovery

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.BaseTest
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.accountrecovery.test.MinimalAccountRecoveryNotificationTest
import me.proton.core.auth.test.usecase.WaitForPrimaryAccount
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.eventmanager.domain.repository.EventMetadataRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.notification.domain.repository.NotificationRepository
import me.proton.test.fusion.FusionConfig
import org.junit.Rule
import javax.inject.Inject

@HiltAndroidTest
class AccountRecoveryFlowTest : BaseTest(), MinimalAccountRecoveryNotificationTest {
    @get:Rule(order = Rule.DEFAULT_ORDER - 2)
    val grantPermissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = Rule.DEFAULT_ORDER - 1)
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    @Inject
    override lateinit var accountStateHandler: AccountStateHandler

    @Inject
    override lateinit var apiProvider: ApiProvider

    @Inject
    override lateinit var eventManagerProvider: EventManagerProvider

    @Inject
    override lateinit var eventMetadataRepository: EventMetadataRepository

    @Inject
    override lateinit var notificationRepository: NotificationRepository

    @Inject
    override lateinit var waitForPrimaryAccount: WaitForPrimaryAccount

    init {
        FusionConfig.Compose.testRule.set(CustomComposeContentTestRule(composeTestRule))
        FusionConfig.Compose.useUnmergedTree.set(true)
    }

    override fun verifyAfterLogin() {
        waitForPrimaryAccount()
    }
}

private class CustomComposeContentTestRule(composeTestRule: ComposeTestRule) :
    ComposeContentTestRule, ComposeTestRule by composeTestRule {
    override fun setContent(composable: @Composable () -> Unit) {
        // no-op
    }
}
