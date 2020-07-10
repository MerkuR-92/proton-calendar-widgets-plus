package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import com.proton.gopenpgp.crypto.SessionKey
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.printToString
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.android.calendar.domain.model.Event

class EditCreateEventUseCase(
    private val logger: Logger,
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val database: AppDatabase): UseCase {

    suspend fun execute(userId: String, calendarId: String, newEvent: Event) : UseCase.Result {

        // TODO figure out member-id, it's hardcoded below
        // TODO userId
        val valueStore = valueStoreProvider.provideValueStore(userId)

        logger.e("executing EditCreateEventUseCase from newEvent: ${newEvent}")
        logger.e("executing EditCreateEventUseCase from icalendar: ${newEvent.iCalendar.printToString()}")

        // TODO SEQUENCE ID has to be already incremented

        // 1. split original event according to the matrix
        val calendarSplit = ICalUtils.splitICalendarIntoParts(newEvent.iCalendar)

        //logger.e("shared split: ${calendarSplit.sharedPart.printToString()}")

        // 2. get Member's AddressKey for signing
        val member = database.membersDao().select(calendarId).first()
        val userAddresses = database.addressesDao().select(userId, member.email).map { it.toAddress(gson) } // TODO in the future we will have dropdown with memberID, but now we take first
        val memberAddressKey = userAddresses.first().primaryKey ?: return UseCase.Result.InvalidParams("there is no valid AddressKey for Member when creating Event") // TODO Valentin how to select address? how to select address-key?

        // 3. get CalendarKey for encrypting
        val calendarKey = database.calendarKeysDao().select(calendarId).first { it.isActive && it.isPrimary }
        val calendarPassphrase = database.passphrasesDao().select(calendarId).map { it.toPassphrase(gson) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("there is no valid cached Calendar Passphrase")

        // 4. get Session Keys if they were already present in old Event
        var oldSharedSessionKey: SessionKey? = null
        var oldCalendarSessionKey: SessionKey? = null

        val oldEventEntity = database.eventsDao().selectById(newEvent.id)
        if (oldEventEntity != null) {
            oldSharedSessionKey = crypto.decryptSessionKey(oldEventEntity.sharedKeyPacket, calendarKey.privateKey, keyPassphrase.toByteArray())
            oldCalendarSessionKey = if (oldEventEntity.calendarKeyPacket != null) {
                crypto.decryptSessionKey(oldEventEntity.calendarKeyPacket, calendarKey.privateKey, keyPassphrase.toByteArray())
            } else null
        }

        TimberLogger.d("old event entity: $oldEventEntity")
        TimberLogger.d("shared key packet raw: ${oldEventEntity?.sharedKeyPacket}")
        TimberLogger.d("calendar key packet raw: ${oldEventEntity?.calendarKeyPacket}")

        TimberLogger.d("shared key packet decrypted algo: ${oldSharedSessionKey?.algo}")
        TimberLogger.d("calendar key packet decrypted algo: ${oldCalendarSessionKey?.algo}")

        // 5. sign and encrypt Shared Parts
        val sharedPartICalString = calendarSplit.sharedPart.printToString()
        val signatureOfSharedPart = crypto.signTextDetached(sharedPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())

        val sharedPartToEncryptICalString = calendarSplit.sharedPartToEncrypt.printToString()

        val encryptedSharedPartCiphertext = if (oldSharedSessionKey != null) {
            val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, oldSharedSessionKey)
            Ciphertext.from(null, encryptedSharedPart!!)
        } else {
            val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, crypto.getArmoredPublicKey(calendarKey.privateKey) ?: return UseCase.Result.InvalidParams("could not extract Calendar Public Key for encrypting"))
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
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, crypto.getArmoredPublicKey(calendarKey.privateKey) ?: return UseCase.Result.InvalidParams("could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedCalendarPart!!)
            }
        } else null
        val signatureOfEncryptedCalendarPart = calendarPartToEncryptICalString?.run { crypto.signTextDetached(calendarPartToEncryptICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()) }

        // 7. sign Personal Part (optional)
        val personalPartICalString = calendarSplit.personalPart?.printToString()
        val signatureOfPersonalPart = personalPartICalString?.run { crypto.signTextDetached(personalPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())  }

        // 8. encrypt Attendees Part (optional)

        // TODO GET RID OF THIS, GET MEMBER-ID
        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
        val calendarId = newEvent.calendar.id // TODO get all members for this calendar and get 1st one
        val TODOmemberId = calendarId // TODO TAKE IT FROM MEMBER!!!


        // 9. assemble API request, depending on action we're taking
        val sharedEventContent = listOf(
            Event.SharedEvent(
                2,
                sharedPartICalString,
                signatureOfSharedPart!!, // TODO
                "" // on server, "author" will be extracted from MemberID and this value ignored
            ),
            Event.SharedEvent(
                3,
                encryptedSharedPartCiphertext.encodedDataPacket,
                signatureOfEncryptedSharedPart!!, // TODO
                "" // on server, "author" will be extracted from MemberID and this value ignored
            )
        )

        val calendarEventContent = listOfNotNull(
            if (calendarPartICalString != null && signatureOfCalendarPart != null) {
                Event.CalendarEvent(
                    2,
                    calendarPartICalString,
                    signatureOfCalendarPart,
                    "" // on server, "author" will be extracted from MemberID and this value ignored
                )
            } else null,
            if (encryptedCalendarPartCiphertext != null && signatureOfEncryptedCalendarPart != null) {
                Event.CalendarEvent(
                    3,
                    encryptedCalendarPartCiphertext.encodedDataPacket,
                    signatureOfEncryptedCalendarPart,
                    "" // on server, "author" will be extracted from MemberID and this value ignored
                )
            } else null
        ).ifEmpty { null }

        val personalEventContent = if (personalPartICalString != null && signatureOfPersonalPart != null) {
            Event.PersonalEvent(
                2,
                personalPartICalString,
                signatureOfPersonalPart,
                "", // on server, "author" will be extracted from MemberID and this value ignored
                TODOmemberId // TODO
            )
        } else null

        val syncRequestBody = if (newEvent.isSyncedWithApi()) { // UPDATE

            if (oldEventEntity == null) return UseCase.Result.InvalidParams("could not get old Event from DB for edit")

            SyncEventsUpdateApiRequest(
                memberId = TODOmemberId, // TODO
                events = listOf(
                    SyncEventUpdateContainer(
                        id = newEvent.id,
                        event = SyncEvent(
                            permissions = 6,
                            sharedKeyPacket = null, // this is already present in existing event
                            sharedEventContent = sharedEventContent,
                            calendarKeyPacket = if (oldCalendarSessionKey == null) encryptedCalendarPartCiphertext?.encodedKeyPacket else null, // only attach newly generated Calendar KeyPacket when updating
                            calendarEventContent = calendarEventContent,
                            personalEventContent = personalEventContent
                        )
                    )
                )
            )

        } else { // CREATE
            SyncEventsUpdateApiRequest(
                memberId = TODOmemberId, // TODO
                events = listOf(
                    SyncEventCreateContainer(
                        event = SyncEvent(
                            permissions = 6,
                            sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,
                            sharedEventContent = sharedEventContent,
                            calendarKeyPacket = encryptedCalendarPartCiphertext?.encodedKeyPacket,
                            calendarEventContent = calendarEventContent,
                            personalEventContent = personalEventContent
                        )
                    )
                )
            )
        }

        return when (val syncResponse = calendarsApi.syncEvents(calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {
                //             TODO insert event into local DB: database.eventsDao().insert(syncResponse.data...
                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error(syncResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(syncResponse.exception.message ?: "(no exception message)")
        }

    }

}
