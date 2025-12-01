package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.MeetingDetailsResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ProtonMeetApi
import me.proton.android.calendar.domain.utils.ProtonMeetCrypto
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.crypto.common.pgp.VerificationStatus
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

class GetProtonMeetDetailsUseCase @Inject constructor(
    private val meetApi: ProtonMeetApi,
    private val protonMeetCrypto: ProtonMeetCrypto,
    private val userManager: UserManager,
    private val logger: Logger,
) {

    sealed interface MeetingDetailsResult {
        data class Success(val sessionKey: SessionKey) : MeetingDetailsResult
        data object Error : MeetingDetailsResult
    }

    suspend fun execute(meetingLinkName: String, userId: UserId): MeetingDetailsResult {
        val meeting = when (val resp = meetApi.getProtonMeetDetails(userId, meetingLinkName)) {
            is ApiResponse.Error -> {
                logger.e("Get proton meet error: ${resp.error}")
                return MeetingDetailsResult.Error
            }
            is ApiResponse.Exception -> {
                logger.e("Get proton meet exception: ${resp.exception.message}")
                return MeetingDetailsResult.Error
            }
            is ApiResponse.Success<MeetingDetailsResponse> -> resp.data.meeting ?: run {
                logger.e("No meeting returned, code: ${resp.data.code}")
                return MeetingDetailsResult.Error
            }
        }

        val encryptedPassword = meeting.passwordArmored
        if (encryptedPassword.isNullOrEmpty()) {
            logger.e("Meeting password missing")
            return MeetingDetailsResult.Error
        }

        val user = userManager.getUser(userId)

        val passwordDecrypted = protonMeetCrypto.decryptPassword(user, encryptedPassword)
        if (passwordDecrypted.status != VerificationStatus.Success) {
            logger.e("Meeting password failed to decrypt: ${passwordDecrypted.status}")
            return MeetingDetailsResult.Error
        }

        val password = passwordDecrypted.text

        val sessionKey = protonMeetCrypto.decryptSessionKey(
            encryptedSessionKeyB64 = meeting.sessionKey,
            password = password,
            saltB64 = meeting.salt,
        ) ?: run {
            logger.e("Failed to decrypt session key")
            return MeetingDetailsResult.Error
        }

        return MeetingDetailsResult.Success(sessionKey)
    }
}
