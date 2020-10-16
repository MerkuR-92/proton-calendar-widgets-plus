package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
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

    suspend fun execute(eventEntity: EventEntity) : Event? {

        val calendarEntity = database.calendarsDao().selectById(eventEntity.calendarId) ?: return null
        val userId = calendarEntity.fkUserId
        val calendarKey = database.calendarKeysDao().select(eventEntity.calendarId).firstOrNull { it.isActive && it.isPrimary } ?: return null // TODO this "fixes" crashes when being connected to vpn and not having login/bootstrap performerd -- but why do we lose CalendarKeys from db?
        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(gson) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return null

        val calendarParts = mutableListOf<String>()

        val verificationStatuses: MutableList<Event.SignatureVerification> = mutableListOf()

        // process Shared Events
        val sharedEvents = eventEntity.sharedEvents.map {
            gson.fromJson(it, Event.SharedEvent::class.java)
        }

        sharedEvents.forEach {
            //logger.v("shared event ${it}")

            val decryptedText = if (it.isEncrypted) {
                val cipherText = Ciphertext.from(eventEntity.sharedKeyPacket, it.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), calendarKey.privateKey, keyPassphrase.toByteArray())
            } else null

            // TODO consider creating flag for disabling verification, OR maybe when we create repository cache,
            //  too many open cursors won't be a problem anymore
            val verificationKeys = database.publicKeysDao().select(it.author).map { it.publicKey }
            if (verificationKeys.isEmpty()) {
                verificationStatuses.add(Event.SignatureVerification.NO_KEYS)
            } else {
                val signatureOk = if (decryptedText != null) {
                    crypto.verifyTextDetached(decryptedText, it.signature, verificationKeys)
                } else {
                    crypto.verifyTextDetached(it.data, it.signature, verificationKeys)
                }

                if (signatureOk) {
                    verificationStatuses.add(Event.SignatureVerification.SUCCESS)
                } else {
                    verificationStatuses.add(Event.SignatureVerification.FAILURE)
                    logger.v("signature not okay for ${decryptedText}")
                }
            }

            calendarParts.add(decryptedText ?: it.data)

            if (decryptedText != null) {
                logger.v("decrypted shared event: " + decryptedText)
            } else {
                logger.v("not-decrypted shared event: " + it.data)
            }

        }

        // process Calendar Events
        val calendarEvents = eventEntity.calendarEvents.map {
            gson.fromJson(it, Event.CalendarEvent::class.java)
        }

        calendarEvents.forEach {
            val decryptedText = if (it.isEncrypted && eventEntity.calendarKeyPacket != null) {
                val cipherText = Ciphertext.from(eventEntity.calendarKeyPacket, it.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), calendarKey.privateKey, keyPassphrase.toByteArray())
            } else null

            val verificationKeys = database.publicKeysDao().select(it.author).map { it.publicKey }
            if (verificationKeys.isEmpty()) {
                verificationStatuses.add(Event.SignatureVerification.NO_KEYS)
            } else {
                val signatureOk = if (decryptedText != null) {
                    crypto.verifyTextDetached(decryptedText, it.signature, verificationKeys)
                } else {
                    crypto.verifyTextDetached(it.data, it.signature, verificationKeys)
                }

                if (signatureOk) {
                    verificationStatuses.add(Event.SignatureVerification.SUCCESS)
                } else {
                    verificationStatuses.add(Event.SignatureVerification.FAILURE)
                    logger.v("signature not okay for ${decryptedText}")
                }
            }

            calendarParts.add(decryptedText ?: it.data)

            if (decryptedText != null) {
                logger.v("decrypted calendar event: " + decryptedText)
            } else {
                logger.v("not-decrypted calendar event: " + it.data)
            }

        }

        // process Personal Events, those are only signed
        val personalEvents = eventEntity.personalEvents.map {
            gson.fromJson(it, Event.PersonalEvent::class.java)
        }

        personalEvents.forEach {
            val verificationKeys = database.publicKeysDao().select(it.author).map { it.publicKey }
            if (verificationKeys.isEmpty()) {
                verificationStatuses.add(Event.SignatureVerification.NO_KEYS)
            } else {
                val signatureOk = crypto.verifyTextDetached(it.data, it.signature, verificationKeys)
                if (signatureOk) {
                    verificationStatuses.add(Event.SignatureVerification.SUCCESS)
                } else {
                    verificationStatuses.add(Event.SignatureVerification.FAILURE)
                }
                logger.v("signature ok for personal event: " + signatureOk)
                logger.v("not-decrypted personal event: " + it.data)
            }

            calendarParts.add(it.data)
        }

        // process Attendees Events
        val attendeesEvents = eventEntity.attendeesEvents.map {
            gson.fromJson(it, Event.AttendeeEvent::class.java)
        }

        attendeesEvents.forEach {
            //logger.v("shared event ${it}")

            val decryptedText = if (it.isEncrypted) {
                val cipherText = Ciphertext.from(eventEntity.sharedKeyPacket, it.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), calendarKey.privateKey, keyPassphrase.toByteArray())
            } else null

            // TODO consider creating flag for disabling verification, OR maybe when we create repository cache,
            //  too many open cursors won't be a problem anymore
            val verificationKeys = database.publicKeysDao().select(it.author).map { it.publicKey }
            if (verificationKeys.isEmpty()) {
                verificationStatuses.add(Event.SignatureVerification.NO_KEYS)
            } else {
                val signatureOk = if (decryptedText != null) {
                    crypto.verifyTextDetached(decryptedText, it.signature, verificationKeys)
                } else {
                    crypto.verifyTextDetached(it.data, it.signature, verificationKeys)
                }

                if (signatureOk) {
                    verificationStatuses.add(Event.SignatureVerification.SUCCESS)
                } else {
                    verificationStatuses.add(Event.SignatureVerification.FAILURE)
                    logger.v("signature not okay for ${decryptedText}")
                }
            }

            calendarParts.add(decryptedText ?: it.data)

            if (decryptedText != null) {
                logger.v("decrypted attendee event: " + decryptedText)
            } else {
                logger.v("not-decrypted attendee event: " + it.data)
            }

        }

        if (calendarParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts)

        if (iCalendar == null || iCalendar.events.isEmpty() || iCalendar.events.first().sanitise() == false) return null

        TimberLogger.v("merged calendar: ${iCalendar.printToString()}")

        // TODO move sanitising to helper function?
        iCalendar.adjustIncomingAllDayEvent()

//        iCalendar.events.first().let {
//
//        }
//
//        iCalendar.setDefaultTimeZone(iCalendar.events.first().)

//        TimberLogger.v("after merging: ${iCalendar!!.printToString()}")
//        TimberLogger.v("verification statueses: ${verificationStatuses}")



        return Event(
                id = eventEntity.id,
                calendar = Calendar(
                    calendarEntity.id,
                    calendarEntity.name,
                    calendarEntity.color,
                    calendarEntity.isActive,
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



}
