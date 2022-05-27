package me.proton.android.calendar.domain.usecase

import android.util.Base64
import biweekly.parameter.ParticipationStatus
import com.proton.gopenpgp.crypto.SessionKey
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.CustomICalPropertyParameter
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.SESSION_KEY_ALGO
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.tryCastOrNull
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.isValidForEncryption
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.SyncEvent
import me.proton.android.calendar.data.api.SyncEventCreateContainer
import me.proton.android.calendar.data.api.SyncEventUpdateContainer
import me.proton.android.calendar.data.api.SyncEventsUpdateApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Ciphertext
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.key.PrivateKey
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.takeIfNotEmpty
import me.proton.core.util.kotlin.toInt
import javax.inject.Inject

class EditCreateEventUseCase @Inject constructor(
    private val logger: Logger,
    private val json: Json,
    private val calendarsApi: CalendarsApi,
    private val cryptoContext: CryptoContext,
    private val calendarsRepository: CalendarsRepository,
    private val userManager: UserManager,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val database: AppDatabase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val upgradeEventUseCase: UpgradeEventUseCase
): UseCase {

    suspend fun execute(userId: UserId, newEvent: Event, oldCalendarId: String = newEvent.calendar.id, createLinkedEventAsAttendee: Boolean = false) : UseCase.Result {

        val oldEventEntity = if (newEvent.isSyncedWithApi()) {
            (upgradeEventUseCase.execute(userId, newEvent.id) as? UseCase.Result.Success<*>)?.returnValue.tryCastOrNull<EventEntity>() ?: return UseCase.Result.Error("EditCreateEventUseCase could not upgrade Event")
        } else null

        val userAddresses = userManager.getAddressesOrNull(userId)?.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: User Addresses is empty")

        // 0. split Event according to the matrix
        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(newEvent.iCalendar)

        // 1. get Member's AddressKey for signing
        val newMemberKey = when (val result = getMemberKey(userAddresses, newEvent.calendar.id)) {
            is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<MemberKey>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting newMemberKey")
            else -> return result
        }

        // 2. get old CalendarKey for decrypting
        val oldCalendarKey =
            when (val result = getCalendarKey(userId.id, oldCalendarId)) {
                is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<CalendarKey>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting oldCalendarKey")
                else -> return result
            }

        // 3. get old Session Keys if they were already present in old Event
        val oldSessionKeys = if (oldEventEntity != null) {
            when (val result = extractSessionKeys(oldEventEntity, oldCalendarKey)) {
                is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<SessionKeys>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting oldSessionKeys")
                else -> return result
            }
        } else null

        val isCalendarBeingChanged = oldCalendarId != newEvent.calendar.id

        // 4. get new CalendarKey for encrypting
        val newCalendarKey = if (isCalendarBeingChanged) {
            when (val result = getCalendarKey(userId.id, newEvent.calendar.id)) {
                is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<CalendarKey>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting newCalendarKey")
                else -> return result
            }
        } else {
            oldCalendarKey
        }

        // 5. sign and encrypt Shared Parts
        val sharedPartICalString = calendarSplit.sharedPart.printToString()

        val signatureOfSharedPart = kotlin.runCatching { newMemberKey.key.signText(cryptoContext, sharedPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfSharedPart")

        val sharedPartToEncryptICalString = calendarSplit.sharedPartToEncrypt.printToString()

        val encryptedSharedPartCiphertext =
            if (createLinkedEventAsAttendee) {
                val sharedSessionKeyProperty = newEvent.iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SESSION_KEY)?.value ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, shared session key was null")
                val sharedSessionKey = SessionKey(Base64.decode(sharedSessionKeyProperty, Base64.DEFAULT), SESSION_KEY_ALGO)
                // TODO migrate to PublicKey.encryptSessionKey(cryptoContext, sessionKeyBytes)
                val sharedKeyPacket = crypto.getKeyPacket(
                    sharedSessionKey,
                    crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, calendar public key was null")
                )
                Ciphertext.from(sharedKeyPacket, "")
            } else if (oldSessionKeys?.shared != null) { // Shared Session Key was there already, encrypt it with new Calendar Key
                val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, oldSessionKeys.shared)
                val sharedSessionKey = SessionKey(oldSessionKeys.shared.key, SESSION_KEY_ALGO)
                val sharedKeyPacket = crypto.getKeyPacket(
                    sharedSessionKey,
                    crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not get public key to encrypt sharedSessionKey with oldSessionKey")
                )
                Ciphertext.from(sharedKeyPacket, encryptedSharedPart!!)
            } else { // there was no Shared Session Key, encrypt and generate it
                val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey)
                    ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedSharedPart!!)
            }

        val signatureOfEncryptedSharedPart = kotlin.runCatching { newMemberKey.key.signText(cryptoContext, sharedPartToEncryptICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedSharedPart")

        // 6. sign and encrypt Calendar Parts (not always present)
        val calendarPartICalString = calendarSplit.calendarPart?.printToString()
        val signatureOfCalendarPart = calendarPartICalString?.run {
            kotlin.runCatching { newMemberKey.key.signText(cryptoContext, calendarPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfCalendarPart")
        }

        val calendarPartToEncryptICalString = calendarSplit.calendarPartToEncrypt?.printToString()
        val encryptedCalendarPartCiphertext = if (calendarPartToEncryptICalString != null) {
            if (oldSessionKeys?.calendar != null) {
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, oldSessionKeys.calendar)
                val calendarSessionKey = SessionKey(oldSessionKeys.calendar.key, SESSION_KEY_ALGO)
                val calendarKeyPacket = crypto.getKeyPacket(
                    calendarSessionKey,
                    crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not get public key to encrypt calendarSessionKey with oldSessionKey")
                )
                Ciphertext.from(calendarKeyPacket, encryptedCalendarPart!!)
            } else {
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey)
                    ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedCalendarPart!!)
            }
        } else null

        val signatureOfEncryptedCalendarPart = calendarPartToEncryptICalString?.run {
            kotlin.runCatching { newMemberKey.key.signText(cryptoContext, calendarPartToEncryptICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedCalendarPart")
        }

        // 7. sign Personal Part (not always present)
        val personalPartICalString = calendarSplit.personalPart?.printToString()
        val signatureOfPersonalPart = personalPartICalString?.run { kotlin.runCatching { newMemberKey.key.signText(cryptoContext, personalPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfPersonalPart") }

        // 8. sign and encrypt Attendees Part (not always present)
        val attendeesPartICalString = calendarSplit.attendeesPart?.printToString()

        val attendeesEventContent =
            if (createLinkedEventAsAttendee) null // We don't send the attendeesEventContent part when creating a linked event as an attendee
            else if (attendeesPartICalString != null) {
                val encryptedAttendeesPartCiphertext = if (oldSessionKeys?.shared != null) {
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, oldSessionKeys.shared)
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                } else {
                    val sharedSessionKey = crypto.decryptSessionKey(encryptedSharedPartCiphertext.encodedKeyPacket ?: return UseCase.Result.InvalidParams("encoded shared key packet was null when encrypting attendees"), newCalendarKey.privateKeys, newCalendarKey.passphrase)
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, sharedSessionKey ?: return UseCase.Result.InvalidParams("shared session key was null when encrypting attendees"))
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                }

                val signatureOfEncryptedAttendeesPart = kotlin.runCatching { newMemberKey.key.signText(cryptoContext, attendeesPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedAttendeesPart")

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
                newMemberKey.memberId
            )
        } else null

        val attendees = arrayListOf<Event.AttendeeStatusEvent>()
        val newEventAttendees =
            if (createLinkedEventAsAttendee) {
                // The array must only contain one Attendee (the user itself) with his own token and answered participation status
                val canonicalMemberEmails = userAddresses.map { address ->
                    canonicalizeProtonEmail(address.email, forceCanonicalization = true)
                }
                val userAttendee = newEvent.iCalEvent.attendees.find { attendee ->
                    canonicalMemberEmails.firstOrNull { userEmail ->
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

        val organizerEmail = newEvent.iCalEvent.organizer?.extractEmail()
        val isOrganizer =
            if (organizerEmail != null) {
                val canonicalUserEmails = userAddresses.map { canonicalizeProtonEmail(it.email, forceCanonicalization = true) }
                val canonicalOrganizerEmail = canonicalizeProtonEmail(organizerEmail, forceCanonicalization = true)
                canonicalUserEmails.any { canonicalOrganizerEmail == it }.toInt()
            } else if (newEvent.iCalEvent.attendees.isNullOrEmpty()) 1
            else 0

        val syncRequestBody = if (newEvent.isSyncedWithApi()) {
            if (isCalendarBeingChanged) {
                // CREATE in new calendar
                SyncEventsUpdateApiRequest(
                    memberId = newMemberKey.memberId,
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
                                sharedEventId = newEvent.sharedEventId,
                                uid = newEvent.uid,
                                sourceCalendarId = oldCalendarId
                            )
                        )
                    )
                )
            } else { // UPDATE
                SyncEventsUpdateApiRequest(
                    memberId = newMemberKey.memberId,
                    events = listOf(
                        SyncEventUpdateContainer(
                            id = newEvent.id,
                            event = SyncEvent(
                                permissions = 1,
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = null, // this is already present in existing event
                                sharedEventContent = sharedEventContent,
                                calendarKeyPacket = if (oldSessionKeys?.calendar == null) encryptedCalendarPartCiphertext?.encodedKeyPacket else null, // only attach newly generated Calendar KeyPacket when updating
                                calendarEventContent = calendarEventContent,
                                personalEventContent = personalEventContent,
                                attendeesEventContent = attendeesEventContent,
                                attendees = attendees.takeIfNotEmpty() // TODO to remove all attendees from event, send null value
                            )
                        )
                    )
                )
            }
        } else { // CREATE
            if (createLinkedEventAsAttendee) {
                // This is a proton to proton invite
                val sharedEventId = newEvent.iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID)?.value
                SyncEventsUpdateApiRequest(
                    memberId = newMemberKey.memberId,
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
                    memberId = newMemberKey.memberId,
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

        return when (val syncResponse = calendarsApi.syncEvents(userId, newEvent.calendar.id, syncRequestBody)) {
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

    private data class SessionKeys(
        val shared: SessionKey?,
        val calendar: SessionKey?,
    )

    private data class CalendarKey(
        val primaryPrivateKey: String,
        val privateKeys: List<String>,
        val passphrase: ByteArray
    )

    private data class MemberKey(
        val memberId: String,
        val key: PrivateKey
    )

    private fun extractSessionKeys(eventEntity: EventEntity, calendarKey: CalendarKey): UseCase.Result {

        if (eventEntity.sharedKeyPacket == null) {
            return UseCase.Result.InvalidParams("EditCreateEventUseCase: EventEntity is not upgraded")
        }

        val sharedSessionKey = crypto.decryptSessionKey(eventEntity.sharedKeyPacket, calendarKey.privateKeys, calendarKey.passphrase)

        val calendarSessionKey = if (eventEntity.calendarKeyPacket != null) {
            crypto.decryptSessionKey(eventEntity.calendarKeyPacket, calendarKey.privateKeys, calendarKey.passphrase)
        } else null

        if (sharedSessionKey == null && eventEntity.sharedKeyPacket?.isNotBlank() == true) {
            return UseCase.Result.InvalidParams("EditCreateEventUseCase: failed to decrypt shared session key")
        }

        if (calendarSessionKey == null && !eventEntity.calendarKeyPacket.isNullOrBlank()) {
            return UseCase.Result.InvalidParams("EditCreateEventUseCase: failed to decrypt old calendar session key")
        }

        return UseCase.Result.Success(SessionKeys(sharedSessionKey, calendarSessionKey))
    }

    private suspend fun getCalendarKey(userId: String, calendarId: String): UseCase.Result {

        val calendarPrimaryPrivateKey = database.calendarKeysDao().select(calendarId).firstOrNull { it.isActiveAndPrimary }?.privateKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no active primary key for calendar")
        val calendarPrivateKeys = database.calendarKeysDao().select(calendarId).filter { it.isActive }.map { it.privateKey }.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there are no active keys for calendar")
        val calendarPassphraseList = database.passphrasesDao().select(calendarId)

        if (calendarPassphraseList.isNullOrEmpty()) return UseCase.Result.InvalidParams("EditCreateEventUseCase: there are no passphrase for calendar")

        val calendarPassphrase = calendarPassphraseList.map { it.toPassphrase(json) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid cached Calendar Passphrase")

        return UseCase.Result.Success(CalendarKey(calendarPrimaryPrivateKey, calendarPrivateKeys, keyPassphrase.toByteArray()))
    }

    private suspend fun getMemberKey(userAddresses: List<UserAddress>, calendarId: String): UseCase.Result {

        val member = database.membersDao().select(calendarId).firstOrNull() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid first Member")
        val memberAddress = userAddresses.filter { it.email.equalsNoCase(member.email) }.find {
            it.email.equalsNoCase(member.email)
        } ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid Member Address")

        if (!memberAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get MemberAddress valid for encryption in EditCreateEventUseCase", UseCase.Error.Crypto.UserAddressInvalidForEncryption)
        }

        val memberAddressKey = memberAddress.keys.primary()?.privateKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid Primary Address Key for Member")

        return UseCase.Result.Success(MemberKey(member.id, memberAddressKey))
    }

}
