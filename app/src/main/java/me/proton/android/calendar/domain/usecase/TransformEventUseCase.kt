package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.printToString
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import kotlinx.coroutines.flow.first


class TransformEventUseCase(
    private val gson: Gson,
    private val database: AppDatabase,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val crypto: Crypto,
    private val iCal: ICalUtils
) : UseCase { // TODO ADD TEST

    suspend fun execute(eventEntity: EventEntity) : Event? {


        val calendar = database.calendarsDao().selectById(eventEntity.calendarId) ?: return null
        val userId = calendar.fkUserId
        val calendarKey = database.calendarKeysDao().select(eventEntity.calendarId).firstOrNull { it.isActive && it.isPrimary } ?: return null // TODO this "fixes" crashes when being connected to vpn and not having login/bootstrap performerd -- but why do we lose CalendarKeys from db?
        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(gson) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return null

        val decryptedAndCorrectlySignedParts = mutableListOf<String>()

        // TODO just for testing:
        val calendarMembers = database.membersDao().select(calendar.id)
        logger.v("members: $calendarMembers}")




        // TODO Valentin: either take author's public key as argument or get it from database
        // TODO HERE WE JUST TAKE OUR OWN PUBLIC KEY
        val eventAuthorsPublicKeys = database.addressesDao().select(userId).map { it.toAddress(gson).primaryKey?.publicKey ?: ""}




        // process Shared Events
        val sharedEvents = eventEntity.sharedEvents.map {
            gson.fromJson(it, Event.SharedEvent::class.java)
        }

        sharedEvents.forEach {

            logger.v("shared event ${it}")

            val decryptedText = if (it.isEncrypted) {
                val cipherText = Ciphertext.from(eventEntity.sharedKeyPacket, it.data)
                crypto.decryptText(cipherText.asArmoredPGPMessage(), calendarKey.privateKey, keyPassphrase.toByteArray())
            } else null

            val signatureOk = if (decryptedText != null) {
                    crypto.verifyTextDetached(decryptedText, it.signature, eventAuthorsPublicKeys)
                } else {
                    crypto.verifyTextDetached(it.data, it.signature, eventAuthorsPublicKeys)
                }

            if (decryptedText != null) {
                logger.v("decrypted shared event: " + decryptedText)
            } else {
                logger.v("not-decrypted shared event: " + it.data)
            }

            if (signatureOk) {
                decryptedAndCorrectlySignedParts.add(decryptedText ?: it.data)
            } else {
                logger.e("signature not okay for ${decryptedText}")
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

            val signatureOk = if (decryptedText != null) {
                crypto.verifyTextDetached(decryptedText, it.signature, eventAuthorsPublicKeys)
            } else {
                crypto.verifyTextDetached(it.data, it.signature, eventAuthorsPublicKeys)
            }

            if (decryptedText != null) {
                logger.v("decrypted calendar event: " + decryptedText)
            } else {
                logger.v("not-decrypted calendar event: " + it.data)
            }

            if (signatureOk) {
                decryptedAndCorrectlySignedParts.add(decryptedText ?: it.data)
            }
        }

        // process Personal Events, those are only signed
        val personalEvents = eventEntity.personalEvents.map {
            gson.fromJson(it, Event.PersonalEvent::class.java)
        }

        personalEvents.forEach {
            val signatureOk = crypto.verifyTextDetached(it.data, it.signature, eventAuthorsPublicKeys)

            logger.v("signature ok for personal event: " + signatureOk)
            logger.v("not-decrypted personal event: " + it.data)

            if (signatureOk) {
                decryptedAndCorrectlySignedParts.add(it.data)
            }
        }

        if (decryptedAndCorrectlySignedParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(decryptedAndCorrectlySignedParts)

//        TimberLogger.e("after merging: ${iCalendar!!.printToString()}")

        return if (iCalendar != null && iCalendar.events.isNotEmpty()) {

            Event(
                id = eventEntity.id,
                author = eventEntity.author,
                calendar = Calendar(
                    calendar.id,
                    calendar.name,
                    calendar.color
                ),
                iCalendar = iCalendar
            )
        } else null
    }



}