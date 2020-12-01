package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.ICalUtils.sanitise
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.adjustIncomingAllDayEvent
import me.proton.android.calendar.common.printToString
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
    private lateinit var decryptionStatuses: MutableList<Event.DecryptionStatus>

    suspend fun execute(eventEntity: EventEntity) : Event? {

        val calendarEntity = database.calendarsDao().selectById(eventEntity.calendarId) ?: return null
        val userId = calendarEntity.fkUserId
        val calendarKey = database.calendarKeysDao().select(eventEntity.calendarId).firstOrNull { it.isActive && it.isPrimary }
        if (calendarKey == null) {
            logger.e("TransformEventUseCase, calendarKey is null")
            return null
        }
        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(gson) }.firstOrNull() { it.isActive }
        if (calendarPassphrase == null) {
            logger.e("TransformEventUseCase, calendarPassphrase is null")
            return null
        }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id)
        if (keyPassphrase == null) {
            logger.e("TransformEventUseCase, keyPassphrase is null")
            return null
        }

        val calendarParts = mutableListOf<String>()

        verificationStatuses = mutableListOf()
        decryptionStatuses = mutableListOf()

        // process Shared Events
        eventEntity.sharedEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Shared>(it)
        }.forEach { sharedEvent ->
            getPlainText(
                eventEntity.sharedKeyPacket,
                calendarKey.privateKey,
                keyPassphrase,
                sharedEvent)?.let { calendarParts.add(it) }
        }

        // process Calendar Events
        eventEntity.calendarEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Calendar>(it)
        }.forEach { calendarEvent ->
            getPlainText(
                eventEntity.calendarKeyPacket,
                calendarKey.privateKey,
                keyPassphrase,
                calendarEvent)?.let { calendarParts.add(it) }
        }

        // process Personal Events
        eventEntity.personalEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Personal>(it)
        }.forEach { personalEvent ->
            getPlainText(
                null, // personal parts are only signed
                calendarKey.privateKey,
                keyPassphrase,
                personalEvent)?.let { calendarParts.add(it) }
        }

        // process Attendees Events
        eventEntity.attendeesEvents.map {
            Json.decodeFromJsonElement<Event.EventPart.Attendee>(it)
        }.forEach { attendeeEvent ->
            getPlainText(
                eventEntity.sharedKeyPacket,
                calendarKey.privateKey,
                keyPassphrase,
                attendeeEvent)?.let { calendarParts.add(it) }
        }

        if (calendarParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts)

        if (iCalendar == null || iCalendar.events.isEmpty() || iCalendar.events.first().sanitise() == false) return null

        // Cross reference unencrypted Attendees and encrypted AttendeesEvents data to update participation status
        if (!iCalendar.events.first().attendees.isNullOrEmpty()) {
            val attendees = eventEntity.attendees.map {
                Json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
            }
            iCalendar.events.first().attendees.forEach { attendee ->
                val attendeeToken = attendee.getParameter("X-PM-TOKEN")
                val status = attendees.find { it.token == attendeeToken }?.participationStatus
                if (status != null) attendee.participationStatus = status
            }
        }

        logger.v("merged calendar: ${iCalendar.printToString()}")

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
            verificationStatus = when {
                verificationStatuses.all { it == Event.SignatureVerification.SUCCESS } -> {
                    Event.SignatureVerification.SUCCESS
                }
                verificationStatuses.any { it == Event.SignatureVerification.FAILURE } -> {
                    Event.SignatureVerification.FAILURE
                }
                verificationStatuses.any { it == Event.SignatureVerification.SIGNED_BUT_NO_KEYS } -> {
                    Event.SignatureVerification.SIGNED_BUT_NO_KEYS
                }
                else -> null
            },
            decryptionStatus = when {
                decryptionStatuses.all { it == Event.DecryptionStatus.SUCCESS } -> {
                    Event.DecryptionStatus.SUCCESS
                }
                decryptionStatuses.any { it == Event.DecryptionStatus.FAILURE } -> {
                    Event.DecryptionStatus.FAILURE
                }
                else -> null
            }
        )

    }

    /**
     * Get plaintext payload or decrypt & check signature if necessary.
     */
    private suspend fun getPlainText(keyPacket: String?,
                                     privateKey: String,
                                     keyPassphrase: String,
                                     eventPart: Event.EventPart
    ): String? {

        // decrypt if necessary
        val plainText = if (eventPart.isEncrypted) {
            if (keyPacket != null) {
                val cipherText = Ciphertext.from(keyPacket, eventPart.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), privateKey, keyPassphrase.toByteArray())
            } else null
        } else {
            eventPart.data
        }

        if (plainText != null) {

            decryptionStatuses.add(Event.DecryptionStatus.SUCCESS)

            // verify signature if necessary
            if (eventPart.isSigned) {

                if (eventPart.signature != null) {

                    // TODO introduce verification keys cache
                    val verificationKeys = database.publicKeysDao().select(eventPart.author).map { it.publicKey }
                    if (verificationKeys.isEmpty()) {
                        verificationStatuses.add(Event.SignatureVerification.SIGNED_BUT_NO_KEYS)
                    } else {

                        val signatureOk = eventPart.signature?.let {
                            crypto.verifyTextDetached(plainText, it, verificationKeys)
                        } ?: false

                        if (signatureOk) {
                            verificationStatuses.add(Event.SignatureVerification.SUCCESS)
                        } else {
                            verificationStatuses.add(Event.SignatureVerification.FAILURE)
                            logger.v("signature not okay for ${plainText}")
                        }
                    }

                } else {
                    logger.e("EventPart ${eventPart.javaClass} is signed but there is no signature")
                    verificationStatuses.add(Event.SignatureVerification.FAILURE)
                }

            } else {
                verificationStatuses.add(Event.SignatureVerification.NOT_SIGNED)
            }
        } else if (eventPart.isEncrypted) {
            decryptionStatuses.add(Event.DecryptionStatus.FAILURE)
        }

        return plainText
    }
}
