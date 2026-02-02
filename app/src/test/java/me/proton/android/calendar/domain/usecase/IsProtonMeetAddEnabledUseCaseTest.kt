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

    private fun stubNonOrgUser() {
        val user: User = mockk(relaxed = true)
        coEvery { userManager.getUser(userId) } returns user
        every { user.role } returns Role.NoOrganization
    }

    private fun stubOrgAdmin() {
        val user: User = mockk(relaxed = true)
        coEvery { userManager.getUser(userId) } returns user
        every { user.role } returns Role.OrganizationAdmin
    }

    private fun stubOrgMember() {
        val user: User = mockk(relaxed = true)
        coEvery { userManager.getUser(userId) } returns user
        every { user.role } returns Role.OrganizationMember
    }

    private fun stubOrgSettingsWithMeet() {
        coEvery {
            organizationRepository.getOrganizationSettings(
                sessionUserId = userId,
                refresh = false
            )
        } returns OrganizationSettings(
            allowedProducts = listOf("Mail", "Meet"),
            logoId = null,
        )
    }

    private fun stubOrgSettingsWithoutMeet() {
        coEvery {
            organizationRepository.getOrganizationSettings(
                sessionUserId = userId,
                refresh = false
            )
        } returns OrganizationSettings(
            allowedProducts = listOf("Mail"),
            logoId = null,
        )
    }

    // manual add

    @Test
    fun `manual add - non-org user returns flag value`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubNonOrgUser()

        val result = createUseCase().invoke(userId = userId, isAuto = false)

        assert(result)
        coVerify(exactly = 0) { calendarsRepository.flowIsAutoAddConferenceLinkOn(any()) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `manual add - org admin ignores org access control`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubOrgAdmin()
        stubOrgSettingsWithoutMeet()

        val result = createUseCase().invoke(userId = userId, isAuto = false)

        assert(result)
        // should not even check org settings
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `manual add - org non-admin with Meet allowed returns true`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubOrgMember()
        stubOrgSettingsWithMeet()

        val result = createUseCase().invoke(userId = userId, isAuto = false)

        assert(result)
    }

    @Test
    fun `manual add - org non-admin without Meet returns false`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubOrgMember()
        stubOrgSettingsWithoutMeet()

        val result = createUseCase().invoke(userId = userId, isAuto = false)

        assert(!result)
    }

    @Test
    fun `manual add - returns false if flag disabled`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, false)

        val result = createUseCase().invoke(userId = userId, isAuto = false)

        assert(!result)
        // Should short-circuit before org check
        coVerify(exactly = 0) { userManager.getUser(any()) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    // auto-add

    @Test
    fun `auto add - returns false if manual flag disabled`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, false)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
        //short-circuit before other checks
        coVerify(exactly = 0) { userManager.getUser(any()) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - returns false if auto flag disabled`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, false)
        stubNonOrgUser()

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
    }

    @Test
    fun `auto add - non-org user with all flags enabled returns true`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)
        stubNonOrgUser()

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(result)
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - org admin ignores org access control`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)
        stubOrgAdmin()
        stubOrgSettingsWithoutMeet()

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(result)
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `auto add - org non-admin with Meet allowed returns true`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)
        stubOrgMember()
        stubOrgSettingsWithMeet()

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(result)
    }

    @Test
    fun `auto add - org non-admin without Meet returns false`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)
        stubOrgMember()
        stubOrgSettingsWithoutMeet()

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
    }

    @Test
    fun `auto add - org non-admin settings fetch throws returns false`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)
        stubOrgMember()
        coEvery {
            organizationRepository.getOrganizationSettings(sessionUserId = userId, refresh = false)
        } throws RuntimeException("error")

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
    }

    @Test
    fun `auto add - returns false if user setting disabled`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(false)
        stubNonOrgUser()

        val result = createUseCase().invoke(userId = userId, isAuto = true)

        assert(!result)
    }

    @Test
    fun `org settings are cached - multiple calls only fetch once`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubFlag(CalendarFeatureFlag.ProtonMeetAddAuto, true)
        coEvery { calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id) } returns flowOf(true)
        stubOrgMember()
        stubOrgSettingsWithMeet()

        val useCase = createUseCase()
        useCase.invoke(userId = userId, isAuto = false)
        useCase.invoke(userId = userId, isAuto = true)
        useCase.invoke(userId = userId, isAuto = false)

        coVerify(exactly = 1) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `org settings cache is cleared when clearCache is called`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubOrgMember()
        stubOrgSettingsWithMeet()

        val useCase = createUseCase()
        useCase.invoke(userId = userId, isAuto = false)
        useCase.clearCache()
        useCase.invoke(userId = userId, isAuto = false)

        coVerify(exactly = 2) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `org settings cache works for non-org user`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubNonOrgUser()

        val useCase = createUseCase()
        useCase.invoke(userId = userId, isAuto = false)
        useCase.invoke(userId = userId, isAuto = false)

        // Non-org user: first call caches the result, second call uses cache
        coVerify(exactly = 1) { userManager.getUser(userId) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }

    @Test
    fun `org settings cache works for org admin`() = runBlocking {
        stubFlag(CalendarFeatureFlag.ProtonMeetAddManual, true)
        stubOrgAdmin()

        val useCase = createUseCase()
        useCase.invoke(userId = userId, isAuto = false)
        useCase.invoke(userId = userId, isAuto = false)

        // Org admin: first call caches the result, second call uses cache
        coVerify(exactly = 1) { userManager.getUser(userId) }
        coVerify(exactly = 0) { organizationRepository.getOrganizationSettings(any(), any()) }
    }
}
