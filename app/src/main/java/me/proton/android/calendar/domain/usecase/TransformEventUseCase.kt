package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.sanitise
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event


class TransformEventUseCase(
    private val gson: Gson,
    private val database: AppDatabase,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val crypto: Crypto,
    private val iCal: ICalUtils
) : UseCase { // TODO ADD TEST

    private lateinit var verificationStatuses: MutableList<Event.SignatureVerification>

    suspend fun execute(eventEntity: EventEntity) : Event? {

        val calendarEntity = database.calendarsDao().selectById(eventEntity.calendarId) ?: return null
        val userId = calendarEntity.fkUserId
        val calendarKey = database.calendarKeysDao().select(eventEntity.calendarId).firstOrNull { it.isActive && it.isPrimary } ?: return null // TODO this "fixes" crashes when being connected to vpn and not having login/bootstrap performerd -- but why do we lose CalendarKeys from db?
        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(gson) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return null

        val calendarParts = mutableListOf<String>()

        verificationStatuses = mutableListOf()

        // process Shared Events
        eventEntity.sharedEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Shared>(it)
        }.forEach { sharedEvent ->
            calendarParts.add(
                getPlainText(
                    eventEntity.sharedKeyPacket,
                    calendarKey.privateKey,
                    keyPassphrase,
                    sharedEvent))
        }

        // process Calendar Events
        eventEntity.calendarEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Calendar>(it)
        }.forEach { calendarEvent ->
            calendarParts.add(
                getPlainText(
                    eventEntity.calendarKeyPacket,
                    calendarKey.privateKey,
                    keyPassphrase,
                    calendarEvent))
        }

        // process Personal Events
        eventEntity.personalEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Personal>(it)
        }.forEach { personalEvent ->
            calendarParts.add(getPlainText(
                null, // personal parts are only signed
                calendarKey.privateKey,
                keyPassphrase,
                personalEvent))
        }

        // process Attendees Events
        eventEntity.attendeesEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Attendee>(it)
        }.forEach { attendeeEvent ->
            calendarParts.add(
                getPlainText(
                eventEntity.sharedKeyPacket,
                calendarKey.privateKey,
                keyPassphrase,
                attendeeEvent))
        }

        if (calendarParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts)

        if (iCalendar == null || iCalendar.events.isEmpty() || iCalendar.events.first().sanitise() == false) return null

        // Cross reference unencrypted Attendees and encrypted AttendeesEvents data to update participation status
        if (!iCalendar.events.first().attendees.isNullOrEmpty()) {
            val attendees = eventEntity.attendees.map {
                Json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
//                gson.fromJson(it, Event.AttendeeStatusEvent::class.java)
            }
            iCalendar.events.first().attendees.forEach { attendee ->
                val attendeeToken = attendee.getParameter("X-PM-TOKEN")
                val status = attendees.find { it.token == attendeeToken }?.participationStatus
                if (status != null) attendee.participationStatus = status
            }
        }

        TimberLogger.v("merged calendar: ${iCalendar.printToString()}")

        // TODO move sanitising to helper function?
        iCalendar.adjustIncomingAllDayEvent()

        return Event(
                id = eventEntity.id,
                calendar = Calendar(
                    calendarEntity.id,
                    calendarEntity.name,
                    calendarEntity.color,
                    calendarEntity.flags,
                    calendarEntity.display == 1
                ),
                iCalendar = iCalendar,
                verificationStatus = if (verificationStatuses.all { it == Event.SignatureVerification.SUCCESS }) {
                    Event.SignatureVerification.SUCCESS
                } else if (verificationStatuses.any { it == Event.SignatureVerification.FAILURE }) {
                    Event.SignatureVerification.FAILURE
                } else if (verificationStatuses.any { it == Event.SignatureVerification.NO_KEYS }) {
                    Event.SignatureVerification.NO_KEYS
                } else null
            )

    }

    /**
     * Get plaintext payload or decrypt & check signature if necessary.
     */
    private suspend fun getPlainText(keyPacket: String?,
                                     privateKey: String,
                                     keyPassphrase: String,
                                     eventPart: Event.EventPart
    ): String {

         val isEncrypted = eventPart.isEncrypted
         val data = eventPart.data
         val author = eventPart.author
         val signature = eventPart.signature

        val decryptedText = if (isEncrypted && keyPacket != null) {
            val cipherText = Ciphertext.from(keyPacket, data)
            crypto.decryptText(cipherText.asArmoredPGPMessage(), privateKey, keyPassphrase.toByteArray())
        } else null

        // TODO consider creating flag for disabling verification, OR maybe when we create repository cache,
        //  too many open cursors won't be a problem anymore
        val verificationKeys = database.publicKeysDao().select(author).map { it.publicKey }
        if (verificationKeys.isEmpty()) {
            verificationStatuses.add(Event.SignatureVerification.NO_KEYS)
        } else {
            val signatureOk = if (decryptedText != null) {
                crypto.verifyTextDetached(decryptedText, signature, verificationKeys)
            } else {
                crypto.verifyTextDetached(data, signature, verificationKeys)
            }

            if (signatureOk) {
                verificationStatuses.add(Event.SignatureVerification.SUCCESS)
            } else {
                verificationStatuses.add(Event.SignatureVerification.FAILURE)
                logger.v("signature not okay for ${decryptedText}")
            }
        }

        if (decryptedText != null) {
            logger.v("decrypted shared event: " + decryptedText)
        } else {
            logger.v("not-decrypted shared event: " + data)
        }

        return decryptedText ?: data
    }
}
