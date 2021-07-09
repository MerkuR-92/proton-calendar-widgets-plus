package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PersonalEventContentApiRequest
import me.proton.android.calendar.data.api.UpdateEventPersonalPartApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.equalsNoCase

class UpdatePersonalPartUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val cryptoContext: CryptoContext,
    private val userManager: UserManager
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PERSONAL_PART"
    }

    suspend fun execute(userId: UserId, calendarId: String, eventId: String, personalPartICalString: String): UseCase.Result {

        val member = database.membersDao().select(calendarId).firstOrNull()
            ?: return UseCase.Result.InvalidParams("there is no valid first Member when updating Event personal part")

        var personalEventContentApiRequest: PersonalEventContentApiRequest? = null

        if (personalPartICalString.isNotEmpty()) {

            val memberAddressKey = userManager.getAddresses(userId, refresh = true).find {
                it.email.equalsNoCase(member.email)
            }?.keys?.primary() ?: return UseCase.Result.InvalidParams("there is no valid AddressKey for Member when updating Event personal part")

            val signatureOfPersonalPart = kotlin.runCatching {
                memberAddressKey.privateKey.signText(cryptoContext, personalPartICalString)
            }.getOrNull() ?: return UseCase.Result.InvalidParams("failed to sign personal part when updating Event personal part")

            personalEventContentApiRequest = PersonalEventContentApiRequest(
                2,
                personalPartICalString,
                signatureOfPersonalPart
            )
        }

        return when (val updateEventPersonalPartResponse = calendarsApi.updateEventPersonalPart(
            userId,
            calendarId,
            eventId,
            UpdateEventPersonalPartApiRequest(
                member.id,
                personalEventContentApiRequest
            )
        )) {
            is ApiResponse.Success -> {
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error("api error updating event personal part: $updateEventPersonalPartResponse")
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error("api error updating event personal part: ${updateEventPersonalPartResponse.exception.message ?: "(no exception message)"}")
            }
        }
    }
}
