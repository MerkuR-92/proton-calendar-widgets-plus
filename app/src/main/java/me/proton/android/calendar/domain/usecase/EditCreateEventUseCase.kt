package me.proton.android.calendar.domain.usecase

import biweekly.parameter.ParticipationStatus
import com.proton.gopenpgp.crypto.SessionKey
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.util.kotlin.takeIfNotEmpty
import me.proton.core.util.kotlin.toInt

class EditCreateEventUseCase(
    private val logger: Logger,
    private val json: Json,
    private val calendarsApi: CalendarsApi,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val database: AppDatabase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase
): UseCase {

    suspend fun execute(userId: UserId, calendarId: String, newEvent: Event) : UseCase.Result {

        // TODO figure out member-id, it's hardcoded below
        val valueStore = valueStoreProvider.provideValueStore(userId.id)

        logger.d("executing EditCreateEventUseCase from newEvent: ${newEvent}")
        logger.d("executing EditCreateEventUseCase from icalendar: ${newEvent.iCalendar.printToString()}")

        // 1. split original event according to the matrix
        val calendarSplit = ICalUtils.splitICalendarIntoParts(newEvent.iCalendar)

        //logger.v("shared split: ${calendarSplit.sharedPart.printToString()}")

        // 2. get Member's AddressKey for signing
        val member = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid first Member when creating Event")
        val userAddresses = database.addressesDao().select(userId.id, member.email).map { it.toAddress(json) } // TODO in the future we will have dropdown with memberID, but now we take first
        val memberAddressKey = userAddresses.firstOrNull()?.primaryKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid AddressKey for Member when creating Event") // TODO how to select address? how to select address-key?

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

        logger.d("old event entity: $oldEventEntity")
        logger.d("shared key packet raw: ${oldEventEntity?.sharedKeyPacket}")
        logger.d("calendar key packet raw: ${oldEventEntity?.calendarKeyPacket}")

        logger.d("shared key packet decrypted algo: ${oldSharedSessionKey?.algo}")
        logger.d("calendar key packet decrypted algo: ${oldCalendarSessionKey?.algo}")

        // 5. sign and encrypt Shared Parts
        val sharedPartICalString = calendarSplit.sharedPart.printToString()

        val signatureOfSharedPart = crypto.signTextDetached(sharedPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())

        val sharedPartToEncryptICalString = calendarSplit.sharedPartToEncrypt.printToString()

        val encryptedSharedPartCiphertext = if (oldSharedSessionKey != null) {
            val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, oldSharedSessionKey)
            Ciphertext.from(null, encryptedSharedPart!!)
        } else {
            val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, crypto.getArmoredPublicKey(calendarPrimaryPrivateKey)
                ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
            Ciphertext.from(encryptedSharedPart!!)
        }
        val signatureOfEncryptedSharedPart = crypto.signTextDetached(sharedPartToEncryptICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())

        // 6. sign and encrypt Calendar Parts (optional)
        val calendarPartICalString = calendarSplit.calendarPart?.printToString()
        val signatureOfCalendarPart = calendarPartICalString?.run { crypto.signTextDetached(calendarPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()) }

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
        val signatureOfEncryptedCalendarPart = calendarPartToEncryptICalString?.run { crypto.signTextDetached(calendarPartToEncryptICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()) }

        // 7. sign Personal Part (optional)
        val personalPartICalString = calendarSplit.personalPart?.printToString()
        val signatureOfPersonalPart = personalPartICalString?.run { crypto.signTextDetached(personalPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())  }

        // 8. sign and encrypt Attendees Part (optional)
        val attendeesPartICalString = calendarSplit.attendeesPart?.printToString()

        val attendeesEventContent =
            if (attendeesPartICalString != null) {
                val encryptedAttendeesPartCiphertext = if (oldSharedSessionKey != null) {
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, oldSharedSessionKey)
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                } else {
                    val sharedSessionKey = crypto.decryptSessionKey(encryptedSharedPartCiphertext.encodedKeyPacket ?: return UseCase.Result.InvalidParams("encoded shared key packet was null when encrypting attendees"), calendarPrivateKeys, keyPassphrase.toByteArray())
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, sharedSessionKey ?: return UseCase.Result.InvalidParams("shared session key was null when encrypting attendees"))
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                }
                val signatureOfEncryptedAttendeesPart = crypto.signTextDetached(attendeesPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())

                listOf(
                    Event.EventPart.Attendee(
                        3,
                        encryptedAttendeesPartCiphertext.encodedDataPacket,
                        signatureOfEncryptedAttendeesPart!!, // TODO
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    )
                )
            } else null

        // 9. assemble API request, depending on action we're taking
        val sharedEventContent = listOf(
            Event.EventPart.Shared(
                2,
                sharedPartICalString,
                signatureOfSharedPart!!, // TODO
                "" // on server, "author" will be extracted from MemberID and this value ignored
            ),
            Event.EventPart.Shared(
                3,
                encryptedSharedPartCiphertext.encodedDataPacket,
                signatureOfEncryptedSharedPart!!, // TODO
                "" // on server, "author" will be extracted from MemberID and this value ignored
            )
        )

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
            newEvent.iCalEvent.attendees.forEach {
                val status = it.participationStatus.toInt()
                val xpmToken = it.getParameter(X_PM_TOKEN) ?: ICalUtils.generateXPmToken(canonicalizeProtonEmail(it.email), newEvent.uid)
                attendees.add(
                    Event.AttendeeStatusEvent(null, xpmToken, status, null)
                )
            }
        }

        val organizerEmail = newEvent.iCalEvent.organizer?.extractEmail()
        val isOrganizer =
            if (organizerEmail != null) {
                val canonicalUserEmails = database.addressesDao().select(userId.id).map { canonicalizeProtonEmail(it.email) }
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
