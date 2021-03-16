package me.proton.android.calendar.domain.usecase

import biweekly.ICalendar
import biweekly.io.TimezoneInfo
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import com.google.crypto.tink.subtle.Base64
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.usecase.SendEmailDirect
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.io.ByteArrayInputStream

class SendEmailUseCase(
    private val logger: Logger,
    private val sendEmailDirectUseCase: SendEmailDirect,
    private val userManager: UserManager,
    private val database: AppDatabase,
    private val json: Json,
    private val transformEventUseCase: TransformEventUseCase,
    private val calendarsRepository: CalendarsRepository,
    private val valueStoreProvider: ValueStoreProvider,
    private val crypto: Crypto,
    private val editCreateEventUseCase: EditCreateEventUseCase
): UseCase {

    suspend fun executeToOrganizer(
        userId: UserId,
        responseICalendar: ICalendar,
        originalTimeZoneInfo: TimezoneInfo?,
        userAttendee: Attendee,
        organizerEmail: String,
        participationStatus: ParticipationStatus,
        subject: String,
        body: String
    ): UseCase.Result {

        val ics = getResponseIcs(responseICalendar, userAttendee, participationStatus, originalTimeZoneInfo)

        val senderAddressesId = database.addressesDao().select(userId.id, canonicalizeProtonEmail(userAttendee.email)).map {
            it.toAddress(json)
        }.firstOrNull()?.id ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer failed to get address ID for sender") // TODO better error

        // TODO remove hack with overwriting sender email & name
        val senderAddress = userManager.getAddresses(userId).find {
            it.addressId.id == senderAddressesId
        }?.copy(email = userAttendee.email, displayName = userAttendee.commonName) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer failed to get address for sender") // TODO better error

        val attachmentBytes = ics.toByteArray()

        val sendEmailArguments = SendEmailDirect.Arguments(
            subject,
            body,
            INVITE_EMAIL_MIME_TYPE,
            listOf(organizerEmail),
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE,
                    ByteArrayInputStream(attachmentBytes)
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddress, sendEmailArguments)) {
            is SendEmailDirect.Result.Success -> return UseCase.Result.Success<Unit>()
            else -> UseCase.Result.Error("SendEmailUseCase executeToOrganizer failed to send email to organizer: $sendEmailResult")
        }
    }

    suspend fun executeToAttendees(
        userId: UserId,
        eventId: String,
        attendees: List<Attendee>,
        subject: String,
        body: String,
        isCreate: Boolean,
        editedEvent: Event? = null
    ): UseCase.Result {
        val newEventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to select event entity")
        val sharedEventId = newEventEntity.sharedEventId ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees sharedEventID was null")
        val calendarId = newEventEntity.calendarId

        val calendarPrivateKeys = database.calendarKeysDao().select(calendarId).filter { it.isActive }.map { it.privateKey }.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees: there are no active keys for calendar")
        val calendarPassphraseList = database.passphrasesDao().select(calendarId)
        if (calendarPassphraseList.isNullOrEmpty()) return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees: there are no passphrase for calendar")
        val calendarPassphrase = calendarPassphraseList.map { it.toPassphrase(json) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId.id).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees: there is no valid cached Calendar Passphrase")

        val sharedSessionKey = Base64.encode(crypto.decryptSessionKey(newEventEntity.sharedKeyPacket, calendarPrivateKeys, keyPassphrase.toByteArray())?.key)

        val event =
            if (isCreate) transformEventUseCase.execute(newEventEntity) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to transform event entity")
            else editedEvent ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees edited event was null")

        if (isCreate) {
            // Add attendees
            attendees.forEach {
                event.iCalEvent.addAttendee(it)
            }
        }

        val ics = getInviteIcs(
            event,
            sharedEventId,
            sharedSessionKey
        )

        val member = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees: there is no valid first Member when creating Event")
        val senderAddressesId = database.addressesDao().select(userId.id, canonicalizeProtonEmail(member.email)).map {
            it.toAddress(json)
        }.firstOrNull()?.id ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to get address ID for sender") // TODO better error

        // TODO remove hack with overwriting sender email & name
        val senderAddress = userManager.getAddresses(userId).find {
            it.addressId.id == senderAddressesId
        }?.copy(email = member.email) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to get address for sender") // TODO better error

        val attachmentBytes = ics.toByteArray()

        val attendeeEmails = attendees.mapNotNull { it.extractEmail() }
        val sendEmailArguments = SendEmailDirect.Arguments(
            subject,
            body,
            INVITE_EMAIL_MIME_TYPE,
            attendeeEmails,
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE,
                    ByteArrayInputStream(attachmentBytes)
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddress, sendEmailArguments)) {
            is SendEmailDirect.Result.Success -> {

                if (!isCreate) return UseCase.Result.Success<Unit>()

                // Edit same event to add attendees if mail(s) have been sent

                val editEventResult = editCreateEventUseCase.execute(userId, calendarId, event)

                if (editEventResult is UseCase.Result.InvalidParams) {
                    logger.e("SendEmailUseCase executeToAttendees invalid params in edit event: ${editEventResult.message}")
                }
                if (editEventResult is UseCase.Result.Error) {
                    logger.e("SendEmailUseCase executeToAttendees error in edit event: ${editEventResult.message}")
                }

                // If this edit fails then that's too bad, attendees will be notified but the organizer won't see them in the event so hopefully she will retry on her own will

                return UseCase.Result.Success<Unit>()
            }
            else -> UseCase.Result.Error("SendEmailUseCase executeToAttendees failed to send email to organizer: $sendEmailResult")
        }
    }
}
