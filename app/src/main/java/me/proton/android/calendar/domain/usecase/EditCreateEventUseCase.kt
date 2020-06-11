package me.proton.android.calendar.domain.usecase

import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.parameter.Related
import biweekly.property.Status
import biweekly.property.Trigger
import biweekly.util.Duration
import com.google.gson.Gson
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.printToString
import me.proton.android.calendar.common.wrapInICalendar
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateEventApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.android.calendar.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class EditCreateEventUseCase(
    private val logger: Logger,
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val database: AppDatabase): UseCase {

    suspend fun execute(userId: String, calendarId: String, newEvent: Event/*TODO take only eventId?*/) : UseCase.Result {

        // TODO figure out member-id, it's hardcoded below
        // TODO userId
        val valueStore = valueStoreProvider.provideValueStore(userId)

        logger.v("executing EditCreateEventUseCase")
        logger.v("from icalendar: ${newEvent.iCalendar.printToString()}")

        // 1. split original event according to the matrix
        val calendarSplit = ICalUtils.splitICalendarIntoParts(newEvent.iCalendar)

        logger.e("shared split: ${calendarSplit.sharedPart.printToString()}")

        // 2. get Member's AddressKey for signing
        val member = database.membersDao().select(calendarId).first()
        val userAddresses = database.addressesDao().select(userId, member.email).map { it.toAddress(gson) } // TODO in the future we will have dropdown with memberID, but now we take first
        val memberAddressKey = userAddresses.first().primaryKey ?: return UseCase.Result.InvalidParams("there is no valid AddressKey for Member when creating Event") // TODO Valentin how to select address? how to select address-key?

        // 3. get CalendarKey for encrypting
        val calendarKey = database.calendarKeysDao().select(calendarId).first { it.isActive && it.isPrimary }
        val calendarPassphrase = database.passphrasesDao().select(calendarId).map { it.toPassphrase(gson) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: UseCase.Result.InvalidParams("there is no valid cached Calendar Passphrase")

        // 4. sign and encrypt Shared Parts
        val sharedPartICalString = calendarSplit.sharedPart.printToString()
        val signatureOfSharedPart = crypto.signTextDetached(sharedPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())

        val sharedPartToEncryptICalString = calendarSplit.sharedPartToEncrypt.printToString()
        val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, crypto.getArmoredPublicKey(calendarKey.privateKey) ?: return UseCase.Result.InvalidParams("could not extract Calendar Public Key for encrypting"))
        val signatureOfEncryptedSharedPart = crypto.signTextDetached(sharedPartToEncryptICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())

        val encryptedSharedPartCiphertext = Ciphertext.from(encryptedSharedPart!!)

        // 5. sign and encrypt Calendar Parts (optional)

        val calendarPartICalString = calendarSplit.calendarPart?.printToString()
        val signatureOfCalendarPart = calendarPartICalString?.run { crypto.signTextDetached(calendarPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()) }

        val calendarPartToEncryptICalString = calendarSplit.calendarPartToEncrypt?.printToString()
        val encryptedCalendarPart = calendarPartToEncryptICalString?.run { crypto.encryptText(calendarPartToEncryptICalString, crypto.getArmoredPublicKey(calendarKey.privateKey) ?: return UseCase.Result.InvalidParams("could not extract Calendar Public Key for encrypting")) }
        val signatureOfEncryptedCalendarPart = calendarPartToEncryptICalString?.run { crypto.signTextDetached(calendarPartToEncryptICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()) }

        val encryptedCalendarPartCiphertext = encryptedCalendarPart?.run { Ciphertext.from(encryptedCalendarPart) }

        // 6. sign Personal Part (optional)

        val personalPartICalString = calendarSplit.personalPart?.printToString()
        val signatureOfPersonalPart = personalPartICalString?.run { crypto.signTextDetached(personalPartICalString, memberAddressKey.privateKey, (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray())  }

        // 7. encrypt Attendees Part (optional)

        // TODO GET RID OF THIS, GET MEMBER-ID
        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
        val calendarId = newEvent.calendar.id // TODO get all members for this calendar and get 1st one
        val TODOmemberId = calendarId // TODO TAKE IT FROM MEMBER!!!


        // 8. assemble API request
        val requestBody = CreateEventApiRequest(
            memberId = TODOmemberId, // TODO

            permissions = 6, // Permissions: 2 (enum[number]) - Bitmap
//            1 - Can invite -- attendees can invite other attendees!
//            2 - Can modify event
//            4 - Can see attendees list

            sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,

            sharedEventContent = listOf(
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
            ),

            calendarKeyPacket = encryptedCalendarPartCiphertext?.encodedKeyPacket,

            calendarEventContent = listOfNotNull(
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
            ).ifEmpty { null },

            personalEventContent = if (personalPartICalString != null && signatureOfPersonalPart != null) {
                Event.PersonalEvent(
                        2,
                        personalPartICalString,
                        signatureOfPersonalPart,
                    "", // on server, "author" will be extracted from MemberID and this value ignored
                    TODOmemberId // TODO
                )
            } else null
        )

        val createResponse = calendarsApi.createEvent(calendarId, requestBody)
        return when (createResponse) {
            is ApiResponse.Success -> {
                //             TODO insert event into local DB
                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error(createResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(createResponse.exception.message ?: "(no exception message)")
        }

    }

}
