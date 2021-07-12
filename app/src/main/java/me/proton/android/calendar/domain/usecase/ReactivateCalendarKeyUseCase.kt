package me.proton.android.calendar.domain.usecase

import com.proton.gopenpgp.crypto.Crypto.newKeyFromArmored
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ReenableKeyApiRequest
import me.proton.android.calendar.data.api.ReenableKeyApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.EncryptedMessage
import me.proton.core.crypto.common.pgp.decryptAndVerifyTextOrNull
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.decryptText
import me.proton.core.key.domain.decryptTextOrNull
import me.proton.core.key.domain.useKeys
import me.proton.core.key.domain.verifyText
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.equalsNoCase

class ReactivateCalendarKeyUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val json: Json,
    private val userManager: UserManager,
    private val cryptoContext: CryptoContext
): UseCase {

    suspend fun execute(userId: UserId, calendarId: String) : UseCase.Result {

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

                    // Load Address linked to member
                    val memberAddress = userManager.getAddresses(userId, refresh = true).find {
                        it.email.equalsNoCase(member.email)
                    } ?: return@members

                    // Try to decrypt passphrase
                    val decryptedPassphrase = memberAddress.useKeys(cryptoContext) {
                        decryptTextOrNull(memberPassphrase.passphrase)?.let {
                            if (verifyText(it, memberPassphrase.signature)) it else null
                        }
                    } ?: return@members

                    // Decrypt Calendar key
                    val decryptedCalendarKey = newKeyFromArmored(calendarKey.privateKey).unlock(decryptedPassphrase.toByteArray()) ?: return@members

                    // Get primary passphrase
                    val primaryPassphrase = passphrasesResponse.data.passphrases.firstOrNull {
                        it.flags == 1
                    }?.toPassphrase(json) ?: return@members

                    // Get member primary passphrase
                    val memberPrimaryPassphrase = primaryPassphrase.memberPassphrases.firstOrNull {
                        it.memberId == member.id
                    } ?: return@members

                    // Decrypt member primary passphrase
                    val decryptedMemberPrimaryPassphrase = memberAddress.useKeys(cryptoContext) {
                        decryptTextOrNull(memberPrimaryPassphrase.passphrase)?.let {
                            if (verifyText(it, memberPrimaryPassphrase.signature)) it else null
                        }
                    } ?: return@members

                    // Encrypt Calendar key using primary passphrase
                    val newlyEncryptedCalendarKey = decryptedCalendarKey.lock(decryptedMemberPrimaryPassphrase.toByteArray()) ?: return@members

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

        if (reenableKeyResponses.any { it !is ApiResponse.Success }) {
            return UseCase.Result.Error("ReactivateCalendarKeyUseCase: error reenabling one or more key from API")
        }

        return UseCase.Result.Success<Unit>()
    }

}
