package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PersonalEventContentApiRequest
import me.proton.android.calendar.data.api.UpdateEventPersonalPartApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Notification
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import javax.inject.Inject

class UpdatePersonalPartUseCase @Inject constructor(
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val cryptoContext: CryptoContext,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PERSONAL_PART"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String,
        eventId: String,
        personalPartICalString: String,
        notifications: List<Notification>?,
        color: String? = null
    ): UseCase.Result {

        var personalEventContentApiRequest: PersonalEventContentApiRequest? = null

        if (personalPartICalString.isNotEmpty()) {

            val member = calendarsRepository.selectCalendarUserMember(calendarId) ?: return UseCase.Result.InvalidParams("UpdatePersonalPartUseCase: member was null")
            val memberAddressKey = calendarsRepository.getAddressForMember(userId, member.addressId, member.id, member.canonicalEmail)?.keys?.primary() ?: return UseCase.Result.InvalidParams("there is no valid AddressKey for Member when updating Event personal part")

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
                personalEventContentApiRequest,
                notifications = notifications?.map { NotificationEntity.fromNotification(it) },
                color = color
            )
        )) {
            is ApiResponse.Success -> {
                val eventResponse = updateEventPersonalPartResponse.data.event
                calendarsRepository.persistEvents(eventResponse.toEventEntity())
                calendarsRepository.persistEventsMetadata(eventResponse.toEventEntityMetadata())
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
