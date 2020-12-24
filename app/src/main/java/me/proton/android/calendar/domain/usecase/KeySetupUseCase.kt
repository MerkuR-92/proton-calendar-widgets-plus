package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Random
import kotlinx.serialization.json.Json
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
    private val valueStoreProvider: ValueStoreProvider,
    private val json: Json,
    private val database: AppDatabase,
    private val usersRepository: UsersRepository
    ): UseCase {

    suspend fun execute(userId: UserId, addressId: String, calendarId: String, memberAddressKey: AddressKey, memberId: String) : UseCase.Result {
        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        val userPassphrase = valueStore.getString(ValueKey.USER_PASSPHRASE) ?: return UseCase.Result.InvalidParams("user passphrase is empty in KeySetupUseCase")

        // Generate a random 32 bytes passphrase
        val calendarPassphrase = Base64.encode(Random.randBytes(32))

        // Create a new X25519 key that will be used as a Calendar key (please note that the UserID should be set to 'Calendar key')
        val calendarPrivateKey =
            crypto.generateEccKey("not-a-name", "not-an-email@example.tld", calendarPassphrase.toByteArray())
                ?: return UseCase.Result.Error("generateEncryptedKey was null in KeySetupUseCase")

        // Encrypt and sign the calendar passphrase using the member’s AddressKey
        val encryptedToken = crypto.encryptText(
            calendarPassphrase,
            memberAddressKey.privateKey
        ) ?: return UseCase.Result.Error("encryptedSignedToken was null in KeySetupUseCase")

        val tokenSignature = crypto.signTextDetached(
            calendarPassphrase,
            memberAddressKey.privateKey,
            userPassphrase.toByteArray()
        ) ?: return UseCase.Result.Error("signature was null in KeySetupUseCase")

        val keyPackets = mapOf(memberId to (Ciphertext.from(encryptedToken).encodedKeyPacket ?: return UseCase.Result.Error("KeyPackets was null in KeySetupUseCase")))
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

    suspend fun execute(userId: UserId, calendarId: String) : UseCase.Result {
        val user = usersRepository.selectUserById(userId.id)
        val email = user?.email ?: return UseCase.Result.Error("Email for user was null in CreateCalendarUseCase")
        val address = database.addressesDao().select(userId.id, email).firstOrNull()?.toAddress(json) ?: return UseCase.Result.Error("No address id found in CreateCalendarUseCase")

        // Get member for address
        return when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)) {
            is ApiResponse.Success -> {

                val memberId = memberListApiResponse.data.members.firstOrNull()?.id ?: return UseCase.Result.Error("memberId was null in CreateCalendarUseCase")

                val keySetupResult = execute(
                    userId,
                    address.id,
                    calendarId,
                    address.primaryKey ?: address.keys[0],
                    memberId)

                when (keySetupResult) {
                    is UseCase.Result.InvalidParams -> { logger.e("InvalidParams in CreateCalendarUseCase: ${keySetupResult.message}") }
                    is UseCase.Result.Error -> { logger.e("Error in CreateCalendarUseCase: ${keySetupResult.message}") }
                }

                return keySetupResult
            }
            is ApiResponse.Error -> UseCase.Result.Error(memberListApiResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(memberListApiResponse.exception.message ?: "(no exception message)")
        }
    }
}
