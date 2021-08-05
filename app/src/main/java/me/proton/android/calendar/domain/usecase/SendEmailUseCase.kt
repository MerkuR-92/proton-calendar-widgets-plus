package me.proton.android.calendar.domain.usecase

import biweekly.ICalendar
import biweekly.io.TimezoneInfo
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.util.ICalDate
import com.google.crypto.tink.subtle.Base64
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.ICalUtilsImpl.clone
import me.proton.android.calendar.common.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.ICalUtilsImpl.getInviteIcs
import me.proton.android.calendar.common.ICalUtilsImpl.getResponseIcs
import me.proton.android.calendar.common.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.util.*

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
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val resourceProvider: ResourceProvider,
    private val cryptoContext: CryptoContext
): UseCase {

    suspend fun executeToOrganizer(
        userId: UserId,
        responseICalendar: ICalendar,
        originalTimeZoneInfo: TimezoneInfo?,
        userAttendee: Attendee,
        organizerEmail: String,
        participationStatus: ParticipationStatus,
        summary: String?,
        sendPreferences: Map<Email, SendPreferences>,
        dtStamp: Date,
        eventEntity: EventEntity?,
        isProtonProtonInvite: Boolean
    ): UseCase.Result {

        val userAttendeeEmail = userAttendee.extractEmail() ?: return UseCase.Result.InvalidParams("SendEmailUseCase userAttendee has empty email")
        val subject = getReplyMailSubject(summary)
        val body = getReplyMailBody(participationStatus, userAttendeeEmail, summary)

        val ics = if (isProtonProtonInvite && eventEntity != null) {
            val sharedEventId = eventEntity.sharedEventId ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer sharedEventID was null")
            val calendarId = eventEntity.calendarId

            val calendarPrivateKeys = database.calendarKeysDao().select(calendarId).filter { it.isActive }.map { it.privateKey }.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer: there are no active keys for calendar")
            val calendarPassphraseList = database.passphrasesDao().select(calendarId)
            if (calendarPassphraseList.isNullOrEmpty()) return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer: there are no passphrase for calendar")
            val calendarPassphrase = calendarPassphraseList.map { it.toPassphrase(json) }.first { it.isActive }
            val keyPassphrase = valueStoreProvider.provideValueStore(userId.id).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer: there is no valid cached Calendar Passphrase")

            val sharedSessionKey = Base64.encode(crypto.decryptSessionKey(eventEntity.sharedKeyPacket, calendarPrivateKeys, keyPassphrase.toByteArray())?.key)

            getResponseIcs(responseICalendar, userAttendee, participationStatus, originalTimeZoneInfo, dtStamp, isProtonProtonInvite, sharedEventId, sharedSessionKey)
        } else getResponseIcs(responseICalendar, userAttendee, participationStatus, originalTimeZoneInfo, dtStamp, isProtonProtonInvite)

        val userAttendeeCanonicalEmail = canonicalizeProtonEmail(userAttendeeEmail)
        val senderAddressId = database.addressesDao().select(userId.id).find {
            canonicalizeProtonEmail(it.email) == userAttendeeCanonicalEmail
        }?.id ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer failed to get address ID for sender") // TODO better error

        val senderAddress = kotlin.runCatching {
            userManager.getAddresses(userId, refresh = true).find {
                it.addressId.id == senderAddressId
            }
        }.getOrNull() ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToOrganizer failed to get address for sender") // TODO better error

        if (!senderAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get UserAddress valid for encryption to organizer", UseCase.Error.USER_ADDRESS_INVALID_FOR_ENCRYPTION)
        }

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
                    attachmentBytes
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddress, sendEmailArguments, sendPreferences)) {
            is SendEmailDirect.Result.Success -> return UseCase.Result.Success<Unit>()
            else -> UseCase.Result.Error("SendEmailUseCase executeToOrganizer failed to send email to organizer: $sendEmailResult")
        }
    }

    private fun getReplyMailSubject(summary: String?): String {
        return resourceProvider.provideString(R.string.event_change_answer_mail_subject_accepted, summary ?: resourceProvider.provideString(R.string.default_event_summary))
    }

    private fun getReplyMailBody(participationStatus: ParticipationStatus, userAttendeeEmail: String, summary: String?): String {
        return when (participationStatus) {
            ParticipationStatus.ACCEPTED -> resourceProvider.provideString(R.string.event_change_answer_mail_body_accepted, userAttendeeEmail, summary ?: resourceProvider.provideString(R.string.default_event_summary))
            ParticipationStatus.DECLINED -> resourceProvider.provideString(R.string.event_change_answer_mail_body_declined, userAttendeeEmail, summary ?: resourceProvider.provideString(R.string.default_event_summary))
            ParticipationStatus.TENTATIVE -> resourceProvider.provideString(R.string.event_change_answer_mail_body_tentative, userAttendeeEmail, summary ?: resourceProvider.provideString(R.string.default_event_summary))
            else -> "" // TODO Shouldn't happen ?
        }
    }

    suspend fun executeToAttendees(
        userId: UserId,
        newEvent: Event,
        isCreate: Boolean,
        editedEvent: Event? = null,
        sendPreferences: Map<Email, SendPreferences>,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean
    ): UseCase.Result {

        val mailContent = getEmailContent(newEvent, defaultTimeZone, timeFormatIs24Hours)

        val newEventEntity = calendarsRepository.selectEventEntity(newEvent.id) ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to select event entity")
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
            newEvent.iCalEvent.attendees.forEach {
                event.iCalEvent.addAttendee(it)
            }
        }

        val ics = getInviteIcs(
            event,
            sharedEventId,
            sharedSessionKey
        )

        val member = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees: there is no valid first Member when creating Event")
        val senderCanonicalEmail = canonicalizeProtonEmail(member.email)
        val senderAddressId = database.addressesDao().select(userId.id).find {
            canonicalizeProtonEmail(it.email) == senderCanonicalEmail
        }?.id ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to get address ID for sender") // TODO better error

        // TODO Check with core if refresh true can be removed
        val senderAddress = kotlin.runCatching {
            userManager.getAddresses(userId, refresh = true).find {
                it.addressId.id == senderAddressId
            }
        }.getOrNull() ?: return UseCase.Result.InvalidParams("SendEmailUseCase executeToAttendees failed to get address for sender") // TODO better error

        if (!senderAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get UserAddress valid for encryption to attendees", UseCase.Error.USER_ADDRESS_INVALID_FOR_ENCRYPTION)
        }

        val attachmentBytes = ics.toByteArray()

        val attendeeEmails = newEvent.iCalEvent.attendees.mapNotNull { it.extractEmail() }

        val sendEmailArguments = SendEmailDirect.Arguments(
            mailContent.first,
            mailContent.second,
            INVITE_EMAIL_MIME_TYPE,
            attendeeEmails,
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE,
                    attachmentBytes
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddress, sendEmailArguments, sendPreferences)) {
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

    private fun getEmailContent(newEvent: Event, defaultTimeZone: String, timeFormatIs24Hours: Boolean): Pair<String, String> {
        val eventCopy = newEvent.copy(iCalendar = newEvent.iCalendar.clone() as ICalendar)
        if (eventCopy.isAllDay()) {
            eventCopy.iCalEvent.setDateEnd(
                ICalDate(
                    eventCopy.getEnd(defaultTimeZone).toLocalDate()?.minusDays(1)
                        ?.toDate(defaultTimeZone),
                    false
                )
            )
        }
        return Pair(
            getInviteMailSubject(eventCopy, defaultTimeZone, timeFormatIs24Hours),
            getInviteMailBody(eventCopy, defaultTimeZone, timeFormatIs24Hours)
        )
    }


    private fun getInviteMailSubject(event: Event, timezone: String, timeFormatIs24Hours: Boolean): String {
        // TODO Move to UseCase once we can use strings resources there
        return if (!event.isAllDay()) {
            val dateTimeStart =
                event.formatStart(timezone, timeFormatIs24Hours)
            resourceProvider.provideString(
                R.string.event_send_invite_mail_subject_part_day,
                dateTimeStart.first,
                dateTimeStart.second,
                DateTimeUtilsImpl.formatTimeZoneId(
                    timezone,
                    event.iCalEvent.dateStart.value.toInstant(),
                    displayId = false
                )
            )
        } else if (!event.spansSingleDay(true, timeZoneId = timezone)) {
            resourceProvider.provideString(
                R.string.event_send_invite_mail_subject_all_day_multiple,
                event.formatStart(
                    timezone,
                    timeFormatIs24Hours
                ).first
            )
        } else {
            resourceProvider.provideString(
                R.string.event_send_invite_mail_subject_all_day,
                event.formatStart(
                    timezone,
                    timeFormatIs24Hours
                ).first
            )
        }
    }

    private fun getInviteMailBody(event: Event, timezone: String, timeFormatIs24Hours: Boolean): String {
        // TODO Move to UseCase once we can use strings resources there
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)
        var body = resourceProvider.provideString(
            R.string.event_send_invite_mail_body,
            event.summary ?: resourceProvider.provideString(R.string.default_event_summary),
            if (event.isAllDay() && !event.spansSingleDay(true, timeZoneId = timezone)) {
                resourceProvider.provideString(
                    R.string.event_send_invite_mail_body_all_day_multiple,
                    formattedDateStart.first,
                    formattedDateEnd.first
                )
            } else if (event.isAllDay()) {
                resourceProvider.provideString(
                    R.string.event_send_invite_mail_body_all_day_single,
                    formattedDateStart.first
                )
            } else {
                resourceProvider.provideString(
                    R.string.event_send_invite_mail_body_part_day,
                    formattedDateStart.first,
                    formattedDateStart.second,
                    DateTimeUtilsImpl.formatTimeZoneId(
                        timezone,
                        event.iCalEvent.dateStart.value.toInstant(),
                        displayId = false
                    ),
                    formattedDateEnd.first,
                    formattedDateEnd.second,
                    DateTimeUtilsImpl.formatTimeZoneId(
                        timezone,
                        event.iCalEvent.dateEnd.value.toInstant(),
                        displayId = false
                    )
                )
            }
        )
        if (event.location != null) body += resourceProvider.provideString(
            R.string.event_send_invite_mail_body_where,
            event.location
        )
        if (event.description != null) body += resourceProvider.provideString(
            R.string.event_send_invite_mail_body_description,
            event.description
        )
        return body
    }
}
