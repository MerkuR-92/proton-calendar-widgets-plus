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
import me.proton.core.user.domain.entity.Role
import me.proton.core.user.domain.extension.isOrganizationUser
import me.proton.core.usersettings.domain.entity.OrganizationSettings
import me.proton.core.usersettings.domain.repository.OrganizationRepository
import javax.inject.Inject

class IsProtonMeetAddEnabledUseCase @Inject constructor(
    private val featureFlagManager: FeatureFlagManager,
    private val calendarsRepository: CalendarsRepository,
    private val organizationRepository: OrganizationRepository,
    private val userManager: UserManager,
    private val logger: Logger,
) {
    // cached per-user, per-session
    private var cachedOrgAccessControlResult: Pair<UserId, Boolean>? = null

    fun clearCache() {
        cachedOrgAccessControlResult = null
    }

    suspend operator fun invoke(userId: UserId, isAuto: Boolean): Boolean {
        if (!CalendarFeatureFlag.ProtonMeetAddManual.isEnabled(userId)) return false
        if (!isOrgAccessControlAllowed(userId)) return false
        return if (isAuto) {
            CalendarFeatureFlag.ProtonMeetAddAuto.isEnabled(userId)
                    && isAutoAddUserSettingEnabled(userId)
        } else {
            true
        }
    }

    private suspend fun isAutoAddUserSettingEnabled(userId: UserId) = calendarsRepository.flowIsAutoAddConferenceLinkOn(userId.id).firstOrNull() ?: false

    private suspend fun isOrgAccessControlAllowed(userId: UserId): Boolean {
        cachedOrgAccessControlResult?.let { (cachedUserId, result) ->
            if (cachedUserId == userId) return result
        }

        val user = userManager.getUserOrNull(userId, logger)
        val result = when {
            // non-org: not applicable
            user?.isOrganizationUser() != true -> true
            // admins: not applicable
            user.role == Role.OrganizationAdmin -> true
            else -> try {
                organizationRepository.getOrganizationSettings(
                    sessionUserId = userId,
                    refresh = false
                ).isProtonMeetEnabled()
            } catch (e: Throwable) {
                logger.e("Failed to fetch org settings", e)
                false
            }
        }
        cachedOrgAccessControlResult = userId to result
        return result
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
