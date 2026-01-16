package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateMeetingApiRequest
import me.proton.android.calendar.data.api.CreateMeetingApiResponse
import me.proton.android.calendar.di.BaseProtonMeetApiUrl
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ProtonMeetApi
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import okhttp3.HttpUrl
import javax.inject.Inject

private const val PlaceholderName = "New Proton Meet conference"

class GenerateProtonMeetUrlUseCase @Inject constructor(
    @BaseProtonMeetApiUrl private val meetBaseUrl: HttpUrl,
    private val meetApi: ProtonMeetApi,
    private val userAddressManager: UserAddressManager,
    private val prepareMeetPayloadUseCase: PrepareMeetPayloadUseCase,
    private val logger: Logger,
) {

    sealed interface GenerateMeetUrlResult {
        data class Success(
            val url: String,
            val meetingLinkNameConfId: String,
            val encryptedTitle: String,
            val hostAddress: String,
            val sessionKey: SessionKey,
        ) : GenerateMeetUrlResult
        data object GenericError: GenerateMeetUrlResult
        data class ApiError(val message: String): GenerateMeetUrlResult
    }

    private var cachedSuccessResult: GenerateMeetUrlResult? = null

    fun onMeetUrlSynced() {
        cachedSuccessResult = null
    }

    fun cacheExistingMeetUrl(result: GenerateMeetUrlResult.Success) {
        cachedSuccessResult = result
    }

    suspend fun execute(meetingName: String?, userId: UserId): GenerateMeetUrlResult {
        val address = userAddressManager.getAddresses(userId).firstOrNull() ?: run {
            logger.e("No address found for meet payload creation")
            return GenerateMeetUrlResult.GenericError
        }

        // Reuse previously retrieved successful result. The use-case is viewmodel-scoped so this is a valid instance for this user.
        (cachedSuccessResult as? GenerateMeetUrlResult.Success)?.let {
            return it
        }

        val prepared = when (val preparedResult = prepareMeetPayloadUseCase.execute(
            userId = userId,
            meetingName = meetingName ?: PlaceholderName, // Should get synced with the event later
        )) {
            PreparedMeetPayloadResult.NoAccountError -> {
                logger.e("Failed preparing meet payload: $preparedResult")
                return GenerateMeetUrlResult.GenericError
            }
            is PreparedMeetPayloadResult.Success -> preparedResult.payload
        }

        val body = CreateMeetingApiRequest(
            name = prepared.encryptedMeetingNameB64,
            salt = prepared.saltB64,
            sessionKey = prepared.sessionKeyB64,
            srpModulusId = prepared.srpModulusId,
            srpSalt = prepared.srpSaltB64,
            srpVerifier = prepared.srpVerifierB64,
            addressId = address.addressId.id,
            passwordArmored = prepared.armoredPw,
            // Default values, don't need to be set - the BE will sync these with the event
            type = 2,
            startTime = null,
            endTime = null,
            rrule = null,
            timezone = null,
            customPassword = 0,
            protonCalendar = 1,
        )

        val meeting = when (val resp = meetApi.getProtonMeetUrl(userId, body)) {
            is ApiResponse.Error -> {
                logger.e("Get proton meet url error: ${resp.error}")
                return GenerateMeetUrlResult.ApiError(resp.error)
            }
            is ApiResponse.Exception -> {
                logger.e("Get proton meet url exception: ${resp.exception.message}")
                return GenerateMeetUrlResult.GenericError
            }
            is ApiResponse.Success<CreateMeetingApiResponse> -> resp.data.meeting ?: run {
                logger.e("No meeting returned, code: ${resp.data.code}")
                return GenerateMeetUrlResult.GenericError
            }
        }
        val url  = meetBaseUrl.toString() + "join/id-${meeting.meetingLinkName}#pwd-${prepared.urlPasswordBase}"
        logger.i("Generated a new Proton Meet url: $url")
        val conferenceId = meeting.meetingLinkName
        return GenerateMeetUrlResult.Success(
            url = url,
            meetingLinkNameConfId = conferenceId,
            encryptedTitle = prepared.encryptedMeetingNameB64,
            sessionKey = prepared.sessionKey,
            hostAddress = address.email,
        ).also { cachedSuccessResult = it }
    }
}