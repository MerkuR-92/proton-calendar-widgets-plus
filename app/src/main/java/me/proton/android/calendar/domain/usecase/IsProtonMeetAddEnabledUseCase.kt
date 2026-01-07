package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.common.getUserOrNull
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.extension.isOrganizationUser
import me.proton.core.usersettings.domain.entity.OrganizationSettings
import me.proton.core.usersettings.domain.repository.OrganizationRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class IsProtonMeetAddEnabledUseCase @Inject constructor(
    private val featureFlagManager: FeatureFlagManager,
    private val calendarsRepository: CalendarsRepository,
    private val organizationRepository: OrganizationRepository,
    private val userManager: UserManager,
    private val logger: Logger,
) {
    private data class OrgSettingsEntry(
        val settings: OrganizationSettings,
        val expiresAt: Instant = Instant.now().plus(5, ChronoUnit.MINUTES),
    ) {
        fun valueIfValid() = settings.takeIf { expiresAt > Instant.now() }
    }
    private val orgSettingsCache = mutableMapOf<UserId, OrgSettingsEntry>()

    suspend operator fun invoke(userId: UserId, isAuto: Boolean): Boolean {
        return if (isAuto) {
            CalendarFeatureFlag.ProtonMeetAddAuto.isEnabled(userId)
                    && isAutoAddUserSettingEnabled(userId)
                    && isAutoAddOrgSettingEnabled(userId)
        } else {
            CalendarFeatureFlag.ProtonMeetAddManual.isEnabled(userId)
        }
    }

    private suspend fun isAutoAddUserSettingEnabled(userId: UserId) = calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id).firstOrNull() ?: false

    private suspend fun isAutoAddOrgSettingEnabled(userId: UserId): Boolean {
        val user = userManager.getUserOrNull(userId, logger)
        // Ignore this gating for non-org users
        if (user?.isOrganizationUser() != true) return true
        return try {
            val cached = orgSettingsCache[userId]?.valueIfValid()
            if (cached != null) {
                cached.isProtonMeetEnabled()
            } else {
                val settings = organizationRepository.getOrganizationSettings(
                    sessionUserId = userId,
                    refresh = false
                )
                orgSettingsCache[userId] = OrgSettingsEntry(settings)
                settings.isProtonMeetEnabled()
            }
        } catch (e: Throwable) {
            logger.e("Failed to fetch org settings", e)
            false
        }
    }

    private fun OrganizationSettings.isProtonMeetEnabled() = allowedProducts?.any { it.equals("Meet", ignoreCase = true) } ?: false

    private suspend fun CalendarFeatureFlag.isEnabled(userId: UserId) =
        featureFlagManager.getOrDefault(
            userId,
            featureId,
            FeatureFlag.default(
                featureId.id,
                fallbackValue
            )
        ).value
}
