package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Random
import com.google.gson.Gson
import me.proton.android.calendar.common.GsonCommon.gson
import me.proton.android.calendar.common.ICalUtils.generateProtonUid
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PassphraseApiRequest
import me.proton.android.calendar.data.api.SetupKeyApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.AddressKey
import me.proton.core.domain.entity.UserId

class KeySetupUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider
): UseCase {

    companion object {
        const val WORKER_ID = "KEY_SETUP"
    }

    suspend fun execute(userId: UserId, addressId: String, calendarId: String, memberAddressKey: AddressKey, memberId: String) : UseCase.Result {
        val valueStore = valueStoreProvider.provideValueStore(userId.id)

        // Generate a random 32 bytes passphrase
        val passphrase = Base64.encode(Random.randBytes(32))

        // Create a new X25519 key that will be used as a Calendar key (please note that the UserID should be set to 'Calendar key')
        val calendarPrivateKey = crypto.generateEncryptedKey("not-a-name", "not-an-email@example.tld", passphrase.toByteArray())

        // Encrypt and sign the passphrase using the member’s AddressKey
        val encryptedToken = crypto.encryptText(
            passphrase,
            memberAddressKey.privateKey
        ) ?: return UseCase.Result.InvalidParams("encryptedSignedToken was null in KeySetupUseCase")

        val tokenSignature = crypto.signTextDetached(
            passphrase,
            memberAddressKey.privateKey,
            (valueStore.getString(ValueKey.USER_PASSPHRASE) ?: "").toByteArray()
        ) ?: return UseCase.Result.InvalidParams("signature was null in KeySetupUseCase")

        val keyPackets = mapOf(memberId to (Ciphertext.from(encryptedToken).encodedKeyPacket ?: return UseCase.Result.InvalidParams("KeyPackets was null in KeySetupUseCase")))
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
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error(setupKeyApiResponse.error)
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error(
                    setupKeyApiResponse.exception.message ?: "(no exception message)"
                )
            }
        }
    }
}
