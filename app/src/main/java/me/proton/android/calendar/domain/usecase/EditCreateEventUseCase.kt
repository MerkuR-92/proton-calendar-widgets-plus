package me.proton.android.calendar.domain.usecase

import android.util.Base64
import biweekly.parameter.ParticipationStatus
import com.proton.gopenpgp.crypto.SessionKey
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.isValidForEncryption
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.takeIfNotEmpty
import me.proton.core.util.kotlin.toInt

class EditCreateEventUseCase(
    private val logger: Logger,
    private val json: Json,
    private val calendarsApi: CalendarsApi,
    private val cryptoContext: CryptoContext,
    private val calendarsRepository: CalendarsRepository,
    private val userManager: UserManager,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val database: AppDatabase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase
): UseCase {

    suspend fun execute(userId: UserId, calendarId: String, newEvent: Event, createLinkedEventAsAttendee: Boolean = false) : UseCase.Result {

        // 1. split original event according to the matrix
        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(newEvent.iCalendar)

        // 2. get Member's AddressKey for signing
        val member = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid first Member when creating Event")
        val userAddresses = userManager.getAddresses(userId, refresh = false).filter { it.email.equalsNoCase(member.email) }
        val memberAddress = userAddresses.find {
            it.email.equalsNoCase(member.email)
        } ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid Member Address")

        if (!memberAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get MemberAddress valid for encryption in EditCreateEventUseCase", UseCase.Error.Crypto.UserAddressInvalidForEncryption)
        }

        val memberAddressKey = memberAddress.keys.primary()?.privateKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid Primary Address Key for Member")

        // 3. get CalendarKey for encrypting
        val calendarPrimaryPrivateKey = database.calendarKeysDao().select(calendarId).firstOrNull { it.isActiveAndPrimary }?.privateKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no active primary key for calendar when creating Event")
        val calendarPrivateKeys = database.calendarKeysDao().select(calendarId).filter { it.isActive }.map { it.privateKey }.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there are no active keys for calendar when creating Event")
        val calendarPassphraseList = database.passphrasesDao().select(calendarId)
        if (calendarPassphraseList.isNullOrEmpty()) return UseCase.Result.InvalidParams("EditCreateEventUseCase: there are no passphrase for calendar when creating Event")
        val calendarPassphrase = calendarPassphraseList.map { it.toPassphrase(json) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId.id).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid cached Calendar Passphrase")

        // 4. get Session Keys if they were already present in old Event
        var oldSharedSessionKey: SessionKey? = null
        var oldCalendarSessionKey: SessionKey? = null

        val oldEventEntity = database.eventsDao().selectById(newEvent.id)
        if (oldEventEntity != null) {
            oldSharedSessionKey = crypto.decryptSessionKey(oldEventEntity.sharedKeyPacket, calendarPrivateKeys, keyPassphrase.toByteArray())
            oldCalendarSessionKey = if (oldEventEntity.calendarKeyPacket != null) {
                crypto.decryptSessionKey(oldEventEntity.calendarKeyPacket, calendarPrivateKeys, keyPassphrase.toByteArray())
            } else null
            if (oldSharedSessionKey == null && oldEventEntity.sharedKeyPacket.isNotBlank()) {
                return UseCase.Result.InvalidParams("EditCreateEventUseCase: failed to decrypt old shared session key")
            }
            if (oldCalendarSessionKey == null && !oldEventEntity.calendarKeyPacket.isNullOrBlank()) {
                return UseCase.Result.InvalidParams("EditCreateEventUseCase: failed to decrypt old calendar session key")
            }
        }

        // 5. sign and encrypt Shared Parts
        val sharedPartICalString = calendarSplit.sharedPart.printToString()

        val signatureOfSharedPart = kotlin.runCatching { memberAddressKey.signText(cryptoContext, sharedPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfSharedPart")

        val sharedPartToEncryptICalString = calendarSplit.sharedPartToEncrypt.printToString()

        val encryptedSharedPartCiphertext =
            if (createLinkedEventAsAttendee) {
                val sharedSessionKeyProperty = newEvent.iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SESSION_KEY)?.value ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, shared session key was null")
                val sharedSessionKey = SessionKey(Base64.decode(sharedSessionKeyProperty, Base64.DEFAULT), SESSION_KEY_ALGO)
                // TODO migrate to PublicKey.encryptSessionKey(cryptoContext, sessionKeyBytes)
                val sharedKeyPacket = crypto.getKeyPacket(
                    sharedSessionKey,
                    crypto.getArmoredPublicKey(calendarPrimaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, calendar public key was null")
                )
                Ciphertext.from(sharedKeyPacket, "")
            } else if (oldSharedSessionKey != null) {
                val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, oldSharedSessionKey)
                Ciphertext.from(null, encryptedSharedPart!!)
            } else {
                val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, crypto.getArmoredPublicKey(calendarPrimaryPrivateKey)
                    ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedSharedPart!!)
            }

        val signatureOfEncryptedSharedPart = kotlin.runCatching { memberAddressKey.signText(cryptoContext, sharedPartToEncryptICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedSharedPart")

        // 6. sign and encrypt Calendar Parts (not always present)
        val calendarPartICalString = calendarSplit.calendarPart?.printToString()
        val signatureOfCalendarPart = calendarPartICalString?.run {
            kotlin.runCatching { memberAddressKey.signText(cryptoContext, calendarPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfCalendarPart")
        }

        val calendarPartToEncryptICalString = calendarSplit.calendarPartToEncrypt?.printToString()
        val encryptedCalendarPartCiphertext = if (calendarPartToEncryptICalString != null) {
            if (oldCalendarSessionKey != null) {
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, oldCalendarSessionKey)
                Ciphertext.from(null, encryptedCalendarPart!!)
            } else {
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, crypto.getArmoredPublicKey(calendarPrimaryPrivateKey)
                    ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedCalendarPart!!)
            }
        } else null

        val signatureOfEncryptedCalendarPart = calendarPartToEncryptICalString?.run {
            kotlin.runCatching { memberAddressKey.signText(cryptoContext, calendarPartToEncryptICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedCalendarPart")
        }

        // 7. sign Personal Part (not always present)
        val personalPartICalString = calendarSplit.personalPart?.printToString()
        val signatureOfPersonalPart = personalPartICalString?.run { kotlin.runCatching { memberAddressKey.signText(cryptoContext, personalPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfPersonalPart") }

        // 8. sign and encrypt Attendees Part (not always present)
        val attendeesPartICalString = calendarSplit.attendeesPart?.printToString()

        val attendeesEventContent =
            if (createLinkedEventAsAttendee) null // We don't send the attendeesEventContent part when creating a linked event as an attendee
            else if (attendeesPartICalString != null) {
                val encryptedAttendeesPartCiphertext = if (oldSharedSessionKey != null) {
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, oldSharedSessionKey)
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                } else {
                    val sharedSessionKey = crypto.decryptSessionKey(encryptedSharedPartCiphertext.encodedKeyPacket ?: return UseCase.Result.InvalidParams("encoded shared key packet was null when encrypting attendees"), calendarPrivateKeys, keyPassphrase.toByteArray())
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, sharedSessionKey ?: return UseCase.Result.InvalidParams("shared session key was null when encrypting attendees"))
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                }

                val signatureOfEncryptedAttendeesPart = kotlin.runCatching { memberAddressKey.signText(cryptoContext, attendeesPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedAttendeesPart")

                listOf(
                    Event.EventPart.Attendee(
                        3,
                        encryptedAttendeesPartCiphertext.encodedDataPacket,
                        signatureOfEncryptedAttendeesPart,
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    )
                )
            } else null

        // 9. assemble API request, depending on action we're taking
        val sharedEventContent =
            if (createLinkedEventAsAttendee) null
            else {
                listOf(
                    Event.EventPart.Shared(
                        2,
                        sharedPartICalString,
                        signatureOfSharedPart,
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    ),
                    Event.EventPart.Shared(
                        3,
                        encryptedSharedPartCiphertext.encodedDataPacket,
                        signatureOfEncryptedSharedPart,
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    )
                )
            }

        val calendarEventContent = listOfNotNull(
            if (calendarPartICalString != null && signatureOfCalendarPart != null) {
                Event.EventPart.Calendar(
                    2,
                    calendarPartICalString,
                    signatureOfCalendarPart,
                    "" // on server, "author" will be extracted from MemberID and this value ignored
                )
            } else null,
            if (encryptedCalendarPartCiphertext != null && signatureOfEncryptedCalendarPart != null) {
                Event.EventPart.Calendar(
                    3,
                    encryptedCalendarPartCiphertext.encodedDataPacket,
                    signatureOfEncryptedCalendarPart,
                    "" // on server, "author" will be extracted from MemberID and this value ignored
                )
            } else null
        ).ifEmpty { null }

        val personalEventContent = if (personalPartICalString != null && signatureOfPersonalPart != null) {
            Event.EventPart.Personal(
                2,
                personalPartICalString,
                signatureOfPersonalPart,
                "", // on server, "author" will be extracted from MemberID and this value ignored
                member.id
            )
        } else null

        val attendees = arrayListOf<Event.AttendeeStatusEvent>()
        if (attendeesEventContent != null) {
            val newEventAttendees =
                if (createLinkedEventAsAttendee) {
                    // The array must only contain one Attendee (the user itself) with his own token and answered participation status
                    val userEmails = userAddresses.map { address ->
                        canonicalizeProtonEmail(address.email, forceCanonicalization = true)
                    }
                    val userAttendee = newEvent.iCalEvent.attendees.find { attendee ->
                        userEmails.firstOrNull { userEmail ->
                            val attendeeEmail = attendee.extractEmail()
                            attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(userEmail, ignoreCase = true)
                        } != null
                    } ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, could not get user attendee from newEvent")
                    listOf(userAttendee)
                } else newEvent.iCalEvent.attendees

            newEventAttendees.forEach { attendee ->
                if (attendee.participationStatus == null) attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
                val status = attendee.participationStatus?.toInt() ?: ParticipationStatus.NEEDS_ACTION.toInt()
                attendee.extractEmail()?.let {
                    val xpmToken = attendee.getParameter(X_PM_TOKEN) ?: ICalUtilsImpl.generateXPmToken(canonicalizeProtonEmail(it), newEvent.uid) // TODO Maybe use API route ?
                    attendees.add(
                        Event.AttendeeStatusEvent(null, xpmToken, status, null)
                    )
                }
            }
        }

        val organizerEmail = newEvent.iCalEvent.organizer?.extractEmail()
        val isOrganizer =
            if (organizerEmail != null) {
                val canonicalUserEmails = userManager.getAddresses(userId).map { canonicalizeProtonEmail(it.email) }
                val canonicalOrganizerEmail = canonicalizeProtonEmail(organizerEmail)
                canonicalUserEmails.any { canonicalOrganizerEmail == it }.toInt()
            } else if (newEvent.iCalEvent.attendees.isNullOrEmpty()) 1
            else 0

        val syncRequestBody = if (newEvent.isSyncedWithApi()) { // UPDATE

            if (oldEventEntity == null) return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not get old Event from DB for edit")

            SyncEventsUpdateApiRequest(
                memberId = member.id,
                events = listOf(
                    SyncEventUpdateContainer(
                        id = newEvent.id,
                        event = SyncEvent(
                            permissions = 1,
                            isOrganizer = isOrganizer,
                            sharedKeyPacket = null, // this is already present in existing event
                            sharedEventContent = sharedEventContent,
                            calendarKeyPacket = if (oldCalendarSessionKey == null) encryptedCalendarPartCiphertext?.encodedKeyPacket else null, // only attach newly generated Calendar KeyPacket when updating
                            calendarEventContent = calendarEventContent,
                            personalEventContent = personalEventContent,
                            attendeesEventContent = attendeesEventContent,
                            attendees = attendees.takeIfNotEmpty() // TODO to remove all attendees from event, send null value
                        )
                    )
                )
            )

        } else { // CREATE
            if (createLinkedEventAsAttendee) {
                // This is a proton to proton invite
                val sharedEventId = newEvent.iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID)?.value
                SyncEventsUpdateApiRequest(
                    memberId = member.id,
                    events = listOf(
                        SyncEventCreateContainer(
                            event = SyncEvent(
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,
                                personalEventContent = personalEventContent,
                                attendees = attendees,
                                sharedEventId = sharedEventId,
                                uid = newEvent.uid
                            )
                        )
                    )
                )
            } else {
                SyncEventsUpdateApiRequest(
                    memberId = member.id,
                    events = listOf(
                        SyncEventCreateContainer(
                            event = SyncEvent(
                                permissions = 1,
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,
                                sharedEventContent = sharedEventContent,
                                calendarKeyPacket = encryptedCalendarPartCiphertext?.encodedKeyPacket,
                                calendarEventContent = calendarEventContent,
                                personalEventContent = personalEventContent,
                                attendeesEventContent =
                                if (newEvent.iCalendar.method?.isRequest == true) attendeesEventContent // If we create an event from an invitation we provide attendees
                                else null, // We first create without attendees
                                attendees =
                                if (newEvent.iCalendar.method?.isRequest == true) attendees.takeIfNotEmpty()  // If we create an event from an invitation we provide attendees
                                else null // We first create without attendees
                            )
                        )
                    )
                )
            }
        }

        return when (val syncResponse = calendarsApi.syncEvents(userId, calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {
                val eventsToInsertOrUpdate = syncResponse.data.responses.mapNotNull {
                    if (it.response.isSuccessful) {
                        it.response.event
                    } else {
                        logger.e("EditCreateEventUseCase: error in sync: ${it.response.code}: ${it.response.error}: ${it.response.errorDescription}")
                        null
                    }
                }

                calendarsRepository.persistEvents(*eventsToInsertOrUpdate.toTypedArray())
                updateAlarmsUseCase.execute(userId.id, eventsToInsertOrUpdate.map { it.id })

                // TODO we don't need it anymore, since all alarms are calculated locally
                // fetch and store alarms for just changed events
                /*eventsToInsertOrUpdate.forEach { eventEntity ->
                    val alarmsResponse = calendarsApi.getEventAlarms(userId, eventEntity.calendarId, eventEntity.id)
                    when (alarmsResponse) {
                        is ApiResponse.Success -> {
                            database.eventAlarmsDao().deleteAllByEventId(eventEntity.id)
                            alarmsResponse.data.alarms.forEach { database.eventAlarmsDao().updateOrInsert(it) }
                        }
                        is ApiResponse.Error -> logger.e("error getting alarms for created/edited event: ${alarmsResponse.error}")
                        is ApiResponse.Exception -> logger.e("exceptiom getting alarms for created/edited event: ${alarmsResponse.exception}")
                    }
                }*/

                // TODO collect and handle multiple errors
                if (syncResponse.data.responses.any { !it.response.isSuccessful }) {
                    UseCase.Result.Error("EditCreateEventUseCase: TODO one of sync responses is an error")
                } else {
                    UseCase.Result.Success(eventsToInsertOrUpdate.map { it.id })
                }
            }
            is ApiResponse.Error -> UseCase.Result.Error("EditCreateEventUseCase: error in sync events: ${syncResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("EditCreateEventUseCase: error in sync events: ${syncResponse.exception.message ?: "(no exception message)"}")
        }

    }

}
