package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.ICalUtils.sanitise
import me.proton.android.calendar.common.adjustIncomingAllDayEvent
import me.proton.android.calendar.common.printToString
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event


class TransformEventUseCase(
    private val json: Json,
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
        val calendarPrivateKeys = database.calendarKeysDao().select(eventEntity.calendarId).filter { it.isActive }.map { it.privateKey }
        if (calendarPrivateKeys.isNullOrEmpty()) {
            logger.e("TransformEventUseCase, calendarKey is null")
            return null
        }
        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(json) }.firstOrNull() { it.isActive }
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

        coroutineScope {

            // process Shared Events
            val processedSharedEvents = async {
                eventEntity.sharedEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Shared>(it)
                }.map { sharedEvent ->
                    getPlainText(
                        eventEntity.sharedKeyPacket,
                        calendarPrivateKeys,
                        keyPassphrase,
                        sharedEvent
                    )
                }
            }

            // process Calendar Events
            val processedCalendarEvents = async {
                eventEntity.calendarEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Calendar>(it)
                }.map { calendarEvent ->
                    getPlainText(
                        eventEntity.calendarKeyPacket,
                        calendarPrivateKeys,
                        keyPassphrase,
                        calendarEvent
                    )
                }
            }

            // process Personal Events
            val processedPersonalEvents = async {
                eventEntity.personalEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Personal>(it)
                }.map { personalEvent ->
                    getPlainText(
                        null, // personal parts are only signed
                        calendarPrivateKeys,
                        keyPassphrase,
                        personalEvent
                    )
                }
            }

            // process Attendees Events
            val processedAttendeesEvemts = async {
                eventEntity.attendeesEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Attendee>(it)
                }.map { attendeeEvent ->
                    getPlainText(
                        eventEntity.sharedKeyPacket,
                        calendarPrivateKeys,
                        keyPassphrase,
                        attendeeEvent
                    )
                }
            }

            listOf(processedSharedEvents, processedCalendarEvents, processedPersonalEvents, processedAttendeesEvemts)
                .awaitAll().flatten().forEach { processResult ->
                    processResult.plainText?.let { calendarParts.add(it) }
                    decryptionStatuses.add(processResult.decryptionStatus)
                    verificationStatuses.add(processResult.signatureVerification)
                }

        }

        if (calendarParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts)

        if (iCalendar == null || iCalendar.events.isEmpty() || iCalendar.events.first().sanitise() == false) return null

        // Cross reference unencrypted Attendees and encrypted AttendeesEvents data to update participation status
        if (!iCalendar.events.first().attendees.isNullOrEmpty()) {
            val attendees = eventEntity.attendees.map {
                json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
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

    private data class ProcessResult(
        val plainText: String?,
        val decryptionStatus: Event.DecryptionStatus,
        val signatureVerification: Event.SignatureVerification
    )

    /**
     * Get plaintext payload or decrypt & check signature if necessary.
     */
    private suspend fun getPlainText(keyPacket: String?,
                                     privateKeys: List<String>,
                                     keyPassphrase: String,
                                     eventPart: Event.EventPart
    ): ProcessResult {

        // decrypt if necessary
        val plainText = if (eventPart.isEncrypted) {
            if (keyPacket != null) {
                val cipherText = Ciphertext.from(keyPacket, eventPart.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), privateKeys, keyPassphrase.toByteArray())
            } else null
        } else {
            eventPart.data
        }

        return if (plainText != null) {

            var signatureVerification: Event.SignatureVerification = Event.SignatureVerification.FAILURE

            // verify signature if necessary
            if (eventPart.isSigned) {

                if (eventPart.signature != null) {

                    // TODO introduce verification keys cache
                    val verificationKeys = database.publicKeysDao().select(eventPart.author).map { it.publicKey }
                    if (verificationKeys.isEmpty()) {
                        signatureVerification = Event.SignatureVerification.SIGNED_BUT_NO_KEYS
                    } else {

                        val signatureOk = eventPart.signature?.let {
                            crypto.verifyTextDetached(plainText, it, verificationKeys)
                        } ?: false

                        if (signatureOk) {
                            signatureVerification = Event.SignatureVerification.SUCCESS
                        } else {
                            signatureVerification = Event.SignatureVerification.FAILURE
                            logger.v("signature not okay for ${plainText}")
                        }
                    }

                } else {
                    logger.e("EventPart ${eventPart.javaClass} is signed but there is no signature")
                    signatureVerification = Event.SignatureVerification.FAILURE
                }

            } else {
                signatureVerification = Event.SignatureVerification.NOT_SIGNED
            }

            ProcessResult(plainText, Event.DecryptionStatus.SUCCESS, signatureVerification)
        } else {
            ProcessResult(null, Event.DecryptionStatus.FAILURE, Event.SignatureVerification.FAILURE)
        }

    }
}
