package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Random
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PassphraseApiRequest
import me.proton.android.calendar.data.api.ResetCalendarApiRequest
import me.proton.android.calendar.data.api.SetupKeyApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.AddressKey
import me.proton.core.domain.entity.UserId

class ResetPasswordUseCase(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val json: Json,
    private val database: AppDatabase
): UseCase {

    suspend fun execute(userId: UserId) : UseCase.Result {

        // Get all the calendars to be reset
        val resetInfoResponse = calendarsApi.getResetInfo(userId)
        if (resetInfoResponse !is ApiResponse.Success) {
            logger.e("error getting calendars to reset from API in ResetCalendarKeysUseCase")
            return UseCase.Result.Error("error getting calendars to reset from API: $resetInfoResponse")
        }

        val setupKeyApiRequestMap = hashMapOf<String, SetupKeyApiRequest>()

        // Reset key for each calendar
        resetInfoResponse.data.calendars.forEach {
            val calendarId = it.id

            // Get member for address
            when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)) {
                is ApiResponse.Success -> {

                    // Use admin member address
                    val adminMember = memberListApiResponse.data.members.firstOrNull { memberEntity ->
                        // TODO Take admin member that has decryptable key
                        memberEntity.hasPermission(MemberEntity.Permission.ADMIN)
                    } ?: return UseCase.Result.Error("no admin member in ResetCalendarKeysUseCase")

                    val address =
                        database.addressesDao().select(userId.id, adminMember.email).firstOrNull()?.toAddress(json)
                            ?: return UseCase.Result.Error("No address id found in ResetCalendarKeysUseCase")
                    // TODO check key validity

                    setupKeyApiRequestMap[calendarId] = getSetupKeyApiRequest(
                        userId,
                        address.id,
                        address.primaryKey ?: address.keys[0],
                        it.members
                    ) ?: return UseCase.Result.Error("getSetupKeyApiRequest was null in ResetCalendarKeysUseCase")
                }
                is ApiResponse.Error -> UseCase.Result.Error(memberListApiResponse.error)
                is ApiResponse.Exception -> UseCase.Result.Error(
                    memberListApiResponse.exception.message ?: "(no exception message)"
                )
            }
        }

        // TODO: update ResetCalendarApiRequest and remove map once web has updated the route
        return when (val resetCalendarApiResponse = calendarsApi.resetCalendar(userId, ResetCalendarApiRequest(setupKeyApiRequestMap))) {
            is ApiResponse.Success -> {
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error(resetCalendarApiResponse.error)
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error(
                    resetCalendarApiResponse.exception.message ?: "(no exception message)"
                )
            }
        }
    }

    private fun getSetupKeyApiRequest(
        userId: UserId,
        addressId: String,
        adminMemberAddressKey: AddressKey,
        members: Map<String, String>) : SetupKeyApiRequest? {

        val valueStore = valueStoreProvider.provideValueStore(userId.id)
        val userPassphrase = valueStore.getString(ValueKey.USER_PASSPHRASE)
        if (userPassphrase == null) {
            logger.e("user passphrase is empty in ResetCalendarKeysUseCase")
            return null
        }

        // Generate a random 32 bytes passphrase
        val calendarPassphrase = Base64.encode(Random.randBytes(32))

        // Create a new X25519 key that will be used as a Calendar key (please note that the UserID should be set to 'Calendar key')
        // and encrypt key with new 32 bytes string token
        val calendarPrivateKey =
            crypto.generateEccKey("not-a-name", "not-an-email@example.tld", calendarPassphrase.toByteArray())
        if (calendarPrivateKey == null) {
            logger.e("generateEncryptedKey was null in ResetCalendarKeysUseCase")
            return null
        }

        // Sign the token using the admin member AddressKey
        val tokenSignature = crypto.signTextDetached(
            calendarPassphrase,
            adminMemberAddressKey.privateKey,
            userPassphrase.toByteArray()
        )
        if (tokenSignature == null) {
            logger.e("signature was null in ResetCalendarKeysUseCase")
            return null
        }

        // Encrypt the token with all the public keys in Members array
        val packets =
            crypto.encryptTextWithSessionKey(
                calendarPassphrase,
                ArrayList<String>(members.values)
            )

        val keyPackets = hashMapOf<String, String>()
        members.keys.forEachIndexed { index, memberId ->
            packets.second[index]?.let { keyPacket ->
                keyPackets[memberId] = keyPacket
            }
        }

        if (keyPackets.isNullOrEmpty()) {
            logger.e("keyPackets was null or empty in ResetCalendarKeysUseCase")
            return null
        }

        val passphraseApiRequest = PassphraseApiRequest(
            packets.first,
            keyPackets
        )

        return SetupKeyApiRequest(
            privateKey = calendarPrivateKey,
            signature = tokenSignature,
            addressId = addressId,
            passphrase = passphraseApiRequest
        )
    }
}
