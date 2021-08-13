package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.decryptTextOrNull
import me.proton.core.key.domain.useKeys
import me.proton.core.key.domain.verifyText
import me.proton.core.user.domain.UserManager

/**
 * We can decrypt and cache CalendarPassphrase locally, but we need to update it whenever
 * we get Server Event with Passphrase payload.
 */
class CacheCalendarPassphraseUseCase( // TODO TEST
    private val database: AppDatabase,
    private val json: Json,
    private val cryptoContext: CryptoContext,
    private val userManager: UserManager,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider
): UseCase {

    suspend fun execute(userId: UserId, calendarId: String) : UseCase.Result {

        logger.v("executing CacheCalendarPassphraseUseCase, user $userId, calendar $calendarId")
        
        val valueStore = valueStoreProvider.provideValueStore(userId.id)

        val calendarMembers = database.membersDao().select(calendarId)
        val calendarPassphrase = database.passphrasesDao().select(calendarId).map { it.toPassphrase(json) }.first { it.isActive }
        // Passphrase is linked to Calendar and is used by all CalendarKeys of that Calendar

        val member = calendarMembers.firstOrNull() ?: return UseCase.Result.InvalidParams("CacheCalendarPassphraseUseCase: there is no calendar member in ")
        // TODO change to multiple members
        // TODO also something to keep in mind is that you can have multiple members for the user in the same calendar
        // you can join a calendar using Address1 and Address2
        // you can have more than one member

        val memberPassphrase = calendarPassphrase.memberPassphrases.find { it.memberId == member.id }
            ?: return UseCase.Result.InvalidParams("CacheCalendarPassphraseUseCase: there is no user address")

        val memberAddress = userManager.getAddresses(userId).find {
            it.email.equals(member.email, ignoreCase = true)
        } ?: return UseCase.Result.Error("CacheCalendarPassphraseUseCase: No valid Member Address found")

        // decrypt CalendarPassphrase -- actually a Passphrase for CalendarKey
        // AddressKey used to d/encrypt Passphrase for this Member might not be the primary AddressKey
        val plaintextPassphrase = memberAddress.useKeys(cryptoContext) {
            val decryptedPassphrase = decryptTextOrNull(memberPassphrase.passphrase)

            if (verifyText(decryptedPassphrase ?: "", memberPassphrase.signature)) decryptedPassphrase else null
        }

        if (plaintextPassphrase.isNullOrBlank()) {
            return UseCase.Result.InvalidParams("CacheCalendarPassphraseUseCase: could not decrypt passhprase")
        }

        // we cache decrypted CalendarPassphrase under CalendarPassphraseId, but actually this is
        //  Passphrase for CalendarKey, not Calendar
        valueStore.putStringInSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id, plaintextPassphrase)
        logger.v("success decrypting and storing passphrase for calendar $calendarId")
        return UseCase.Result.Success<Unit>()
    }

}
