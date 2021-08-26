package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PassphraseApiRequest
import me.proton.android.calendar.data.api.SetupKeyApiRequest
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.encryptText
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserManager

class KeySetupUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val crypto: Crypto,
    private val cryptoContext: CryptoContext,
    private val userManager: UserManager
): UseCase {

    suspend fun execute(userId: UserId, addressId: String, calendarId: String, memberId: String) : UseCase.Result {

        val memberAddressKey = userManager.getAddresses(userId, refresh = true).find { it.addressId.id == addressId }?.keys?.primary() ?: return UseCase.Result.Error("KeySetupUseCase: No valid Primary Address Key found for Member")

        // Generate a random 32 bytes passphrase
        val calendarPassphrase = Base64.encode(Random.randBytes(32))

        // Create a new X25519 key that will be used as a Calendar key (please note that the UserID should be set to 'Calendar key')
        // and encrypt key with new 32 bytes string token
        val calendarPrivateKey =
            crypto.generateEccKey("not-a-name", "not-an-email@example.tld", calendarPassphrase.toByteArray())
                ?: return UseCase.Result.Error("KeySetupUseCase: generateEncryptedKey was null")

        // Encrypt and sign the calendar passphrase using the member’s AddressKey

        val encryptedToken = kotlin.runCatching { memberAddressKey.privateKey.encryptText(cryptoContext, calendarPassphrase) }.getOrNull() ?: return UseCase.Result.Error("KeySetupUseCase: could not encrypt token")

        val tokenSignature = kotlin.runCatching { memberAddressKey.privateKey.signText(cryptoContext, calendarPassphrase) }.getOrNull() ?: return UseCase.Result.Error("KeySetupUseCase: could not sign token")

        val keyPackets = mapOf(memberId to (Ciphertext.from(encryptedToken).encodedKeyPacket ?: return UseCase.Result.Error("KeySetupUseCase: KeyPackets was null")))

        val passphraseApiRequest = PassphraseApiRequest(
            Ciphertext.from(encryptedToken).encodedDataPacket,
            keyPackets
        )
        val setupKeyApiRequest = SetupKeyApiRequest(
            privateKey = calendarPrivateKey,
            signature = tokenSignature,
            addressId = addressId,
            passphrase = passphraseApiRequest
        )
        return when (val setupKeyApiResponse = calendarsApi.setupKey(userId, calendarId, setupKeyApiRequest)) {
            is ApiResponse.Success -> {
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error("KeySetupUseCase: error in setup key: ${setupKeyApiResponse.error}")
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error(
                    "KeySetupUseCase: error in setup key: ${setupKeyApiResponse.exception.message ?: "(no exception message)"}"
                )
            }
        }
    }

    suspend fun execute(userId: UserId, calendarId: String) : UseCase.Result {

        val address = userManager.getAddresses(userId, refresh = true).firstOrNull {
            it.canSend && it.canReceive
        } ?: return UseCase.Result.Error("KeySetupUseCase: No Address found")

        // Get member for address
        return when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)) {
            is ApiResponse.Success -> {

                val memberId = memberListApiResponse.data.members.firstOrNull()?.id
                    ?: return UseCase.Result.Error("KeySetupUseCase: memberId was null")

                val keySetupResult = execute(
                    userId,
                    address.addressId.id,
                    calendarId,
                    memberId)

                when (keySetupResult) {
                    is UseCase.Result.InvalidParams -> { logger.e("KeySetupUseCase: InvalidParams: ${keySetupResult.message}") }
                    is UseCase.Result.Error -> { logger.e("KeySetupUseCase: Error: ${keySetupResult.message}") }
                }

                return keySetupResult
            }
            is ApiResponse.Error -> UseCase.Result.Error("KeySetupUseCase: error in fetch members: ${memberListApiResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("KeySetupUseCase: error in fetch members: ${memberListApiResponse.exception.message ?: "(no exception message)"}")
        }
    }
}
