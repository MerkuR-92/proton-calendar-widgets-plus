package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PersonalEventContentApiRequest
import me.proton.android.calendar.data.api.UpdateEventPersonalPartApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId

class UpdatePersonalPartUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val crypto: Crypto,
    private val json: Json,
    private val valueStoreProvider: ValueStoreProvider
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PERSONAL_PART"
    }

    suspend fun execute(userId: UserId, calendarId: String, eventId: String, personalPartICalString: String): UseCase.Result {

        val member = database.membersDao().select(calendarId).firstOrNull()
            ?: return UseCase.Result.InvalidParams("there is no valid first Member when updating Event personal part")

        var personalEventContentApiRequest: PersonalEventContentApiRequest? = null

        if (personalPartICalString.isNotEmpty()) {
            val userAddresses = database.addressesDao().select(userId.id, member.email)
                .map { it.toAddress(json) } // TODO in the future we will have dropdown with memberID, but now we take first
            val memberAddressKey =
                userAddresses.firstOrNull()?.primaryKey
                    ?: return UseCase.Result.InvalidParams("there is no valid AddressKey for Member when updating Event personal part") // TODO how to select address? how to select address-key?
            val valueStore = valueStoreProvider.provideValueStore(userId.id)
            val signatureOfPersonalPart = personalPartICalString.run {
                crypto.signTextDetached(
                    personalPartICalString,
                    memberAddressKey.privateKey,
                    (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()
                )
            } ?: return UseCase.Result.InvalidParams("failed to sign personal part when updating Event personal part")
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
