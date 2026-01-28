package me.proton.android.calendar.domain.usecase

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.featureflag.domain.entity.Scope
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.Role
import me.proton.core.user.domain.entity.User
import me.proton.core.usersettings.domain.entity.OrganizationSettings
import me.proton.core.usersettings.domain.repository.OrganizationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class IsProtonMeetAddEnabledUseCaseTest {

    private val featureFlagManager: FeatureFlagManager = mockk()
    private val calendarsRepository: CalendarsRepository = mockk()
    private val organizationRepository: OrganizationRepository = mockk()
    private val userManager: UserManager = mockk()
    private val logger = TestsLogger

    private val userId = UserId("user-id")

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
    }

    private fun createUseCase() = IsProtonMeetAddEnabledUseCase(
        featureFlagManager = featureFlagManager,
        calendarsRepository = calendarsRepository,
        organizationRepository = organizationRepository,
        userManager = userManager,
        logger = logger,
    )

    private fun stubFlag(featureId: CalendarFeatureFlag, value: Boolean) {
        coEvery {
            featureFlagManager.getOrDefault(
                userId = userId,
                featureId = featureId.featureId,
                default = any()
            )
        } answers {
            FeatureFlag(
                userId = userId,
                featureId = featureId.featureId,
                scope = Scope.User,
                value = value,
                defaultValue = false,
                variantName = null,
                payloadType = null,
                payloadValue = null,
            )
        }
    }

    @Test
    fun `manual add - returns flag value`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)

        val result = createUseCase().invoke(userId = userId, isAuto = false)

        assert(result)

        // should not touch auto-gated dependencies when isAuto=false
        coVerify(exactly = 0) { calendarsRepository.flowIsAutoAddConferenceLinkOn(any()) }
        coVerify(exactly = 0) { userManager.getUser(any()) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - returns false if manual flag disabled`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, false)
        // auto flag enabled, but manual flag disabled should short-circuit
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
        coVerify(exactly = 0) { userManager.getUser(any()) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - returns false if auto flag disabled`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, false)
        // even if settings would be true, flag should short-circuit
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
        coVerify(exactly = 0) { userManager.getUser(any()) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - non org user ignores org gating`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)

        // user is NOT org user -> org gating returns true without repo call
        val user: User = mockk(relaxed = true)
        coEvery { userManager.getUser(userId) } returns user
        every { user.role } returns Role.NoOrganization

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(result)
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - org user allowedProducts contains Meet enables and caches`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)

        val user: User = mockk(relaxed = true)
        coEvery { userManager.getUser(userId) } returns user
        every { user.role } returns Role.OrganizationAdmin

        val orgSettings = OrganizationSettings(
            // only field we care about in use case
            allowedProducts = listOf("Mail", "Meet"),
            logoId = null,
        )
        coEvery {
            organizationRepository.getOrganizationSettings(
                sessionUserId = userId,
                refresh = false
            )
        } returns orgSettings

        val useCase = createUseCase()

        val first = useCase.invoke(userId = userId, isAuto = true)
        val second = useCase.invoke(userId = userId, isAuto = true)

        assert(first)
        assert(second)

        // should fetch only once because it caches for ~5 minutes
        coVerify(exactly = 1) {
            organizationRepository.getOrganizationSettings(sessionUserId = userId, refresh = false)
        }
    }

    @Test
    fun `auto add - org settings fetch throws returns false`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)

        val user: User = mockk(relaxed = true)
        coEvery { userManager.getUser(userId) } returns user
        every { user.role } returns Role.OrganizationAdmin

        coEvery {
            organizationRepository.getOrganizationSettings(sessionUserId = userId, refresh = false)
        } throws RuntimeException("error")

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
    }
}
