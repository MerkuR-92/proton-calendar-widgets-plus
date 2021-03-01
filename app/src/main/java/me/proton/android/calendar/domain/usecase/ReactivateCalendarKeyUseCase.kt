package me.proton.android.calendar.domain.usecase

import com.proton.gopenpgp.crypto.Crypto.newKeyFromArmored
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ReenableKeyApiRequest
import me.proton.android.calendar.data.api.ReenableKeyApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId

class ReactivateCalendarKeyUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val json: Json,
    private val database: AppDatabase
): UseCase {

    suspend fun execute(userId: UserId, calendarId: String) : UseCase.Result {
        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        val userPassphrase = valueStore.getString(ValueKey.USER_PASSPHRASE)
        if (userPassphrase == null) {
            return UseCase.Result.Error("ReactivateCalendarKeyUseCase: error empty user passphrase")
        }

        // Get all keys
        val keysResponse = calendarsApi.getKeys(userId, calendarId)
        if (keysResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("ReactivateCalendarKeyUseCase: error getting keys from API: $keysResponse")
        }

        // Get all the passphrase
        val passphrasesResponse = calendarsApi.getPassphrases(userId, calendarId)
        if (passphrasesResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("ReactivateCalendarKeyUseCase: error getting passphrases from API: $passphrasesResponse")
        }

        // Get all members
        val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)
        if (memberListApiResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("ReactivateCalendarKeyUseCase: error getting members from API: $memberListApiResponse")
        }

        val reenableKeyResponses = arrayListOf<ApiResponse<ReenableKeyApiResponse>>()

        // For each key with flags == 0
        keysResponse.data.keys.forEach keys@{ calendarKey ->
            if (calendarKey.flags == 0) {

                // Get passphrase for key
                val passphraseForKey = passphrasesResponse.data.passphrases.firstOrNull { passphrase ->
                    passphrase.id == calendarKey.passphraseId
                }?.toPassphrase(json) ?: return@keys

                // For each calendar member linked to current user
                memberListApiResponse.data.members.forEach members@{ member ->

                    // Extract member passphrase
                    val memberPassphrase = passphraseForKey.memberPassphrases.firstOrNull { memberPassphrase ->
                        memberPassphrase.memberId == member.id
                    } ?: return@members

                    // Load all AddressKeys of address linked to member
                    val address =
                        database.addressesDao().select(userId.id, member.email).firstOrNull()?.toAddress(json)
                            ?: return@members

                    // Try to decrypt passphrase, if fail continue for each
                    address.keys.forEach addressKeys@{ addressKey ->

                        val decryptedPassphrase = crypto.decryptText(
                            memberPassphrase.passphrase,
                            addressKey.privateKey,
                            userPassphrase.toByteArray()
                        ) ?: return@addressKeys

                        if (!crypto.verifyTextDetached(
                                decryptedPassphrase,
                                memberPassphrase.signature,
                                listOf(crypto.getArmoredPublicKey(addressKey.publicKey) ?: "")
                            )
                        ) return@addressKeys

                        // Decrypt Calendar key
                        val decryptedCalendarKey = newKeyFromArmored(calendarKey.privateKey).unlock(decryptedPassphrase.toByteArray()) ?: return@addressKeys

                        // Get primary passphrase
                        val primaryPassphrase = passphrasesResponse.data.passphrases.firstOrNull {
                            it.flags == 1
                        }?.toPassphrase(json) ?: return@addressKeys

                        // Get member primary passphrase
                        val memberPrimaryPassphrase = primaryPassphrase.memberPassphrases.firstOrNull {
                            it.memberId == member.id
                        } ?: return@addressKeys

                        // Get all private keys for address
                        val privateAddressKeys = address.keys.map {
                            it.privateKey
                        }

                        // Get all public keys for address
                        val publicAddressKeys = address.keys.map {
                            it.publicKey
                        }

                        // Decrypt member primary passphrase
                        val decryptedMemberPrimaryPassphrase = crypto.decryptText(
                            memberPrimaryPassphrase.passphrase,
                            privateAddressKeys,
                            userPassphrase.toByteArray()
                        ) ?: return@addressKeys

                        if (!crypto.verifyTextDetached(
                                decryptedMemberPrimaryPassphrase,
                                memberPrimaryPassphrase.signature,
                                publicAddressKeys
                            )
                        ) return@addressKeys

                        // Encrypt Calendar key using primary passphrase
                        val newlyEncryptedCalendarKey = decryptedCalendarKey.lock(decryptedMemberPrimaryPassphrase.toByteArray()) ?: return@addressKeys

                        // Post newly encrypted Calendar key
                        reenableKeyResponses.add(calendarsApi.reenableKey(
                            userId,
                            calendarId,
                            calendarKey.id,
                            ReenableKeyApiRequest(newlyEncryptedCalendarKey.armor())
                        ))

                        // Break calendar member for each
                        return@keys
                    }
                }
            }
        }

        if (reenableKeyResponses.any { it !is ApiResponse.Success }) {
            return UseCase.Result.Error("ReactivateCalendarKeyUseCase: error reenabling one or more key from API")
        }

        return UseCase.Result.Success<Unit>()
    }

}
