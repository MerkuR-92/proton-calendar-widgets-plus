package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*

/**
 * We can decrypt and cache CalendarPassphrase locally, but we need to update it whenever
 * we get Server Event with Passphrase payload.
 */
class CacheCalendarPassphraseUseCase( // TODO TEST
    private val database: AppDatabase,
    private val gson: Gson,
    private val crypto: Crypto,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider
): UseCase {

    // userid: "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="
    suspend fun execute(userId: String, calendarId: String) : UseCase.Result {

        logger.v("executing CacheCalendarPassphraseUseCase, user $userId, calendar $calendarId")

        // TODO ADD MORE INVALID-PARAM UseCaseResults

        val valueStore = valueStoreProvider.provideValueStore(userId)

        val calendarMembers = database.membersDao().select(calendarId)
        val calendarPassphrase = database.passphrasesDao().select(calendarId).map { it.toPassphrase(gson) }.first { it.isActive }
        // Passphrase is linked to Calendar and is used by all CalendarKeys of that Calendar

        val member = calendarMembers.first() // TODO change to multiple members
        // also something to keep in mind is that you can have multiple members for the user in the same calendar
        // you can join a calendar using Address1 and Address2
        // you can have more than one member

        val userAddresses = database.addressesDao().select(userId, member.email).map { it.toAddress(gson) }

        val memberPassphrase = calendarPassphrase.memberPassphrases.find { it.memberId == member.id }

        val address = userAddresses.first() // TODO probably it will be multiple for more members

        // decrypt CalendarPassphrase -- actually a Passphrase for CalendarKey
        // AddressKey used to d/encrypt Passphrase for this Member might not be the primary AddressKey
        var decryptedPassphrase: String? = null
        address.keys.forEach { addressKey ->
            val decryptionResult = crypto.decryptText(
                memberPassphrase!!.passphrase,
                addressKey.privateKey,
                (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()
            )

            decryptionResult?.let {
                if (crypto.verifyTextDetached(
                        it,
                        memberPassphrase.signature,
                        listOf(crypto.getArmoredPublicKey(address.primaryKey!!.privateKey) ?: ""))) {
                    decryptedPassphrase = it
                    return@forEach
                }        
            }

        }

        return if (decryptedPassphrase.isNullOrBlank()) {
            UseCase.Result.InvalidParams("there is no AddressKey for email ${member.email} to decrypt CalendarKeyPassphrase")
        } else {
            // we cache decrypted CalendarPassphrase under CalendarPassphraseId, but actually this is
            //  Passphrase for CalendarKey, not Calendar
            valueStore.putStringInSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id, decryptedPassphrase!!)
            logger.v("success decrypting and storing passphrase for calendar $calendarId")
            UseCase.Result.Success
        }

    }

}