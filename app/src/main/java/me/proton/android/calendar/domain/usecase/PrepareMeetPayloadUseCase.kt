package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.utils.ProtonMeetCrypto
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.PGPCrypto
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.crypto.common.pgp.SignatureContext
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.encryptAndSignText
import me.proton.core.key.domain.entity.keyholder.KeyHolder
import me.proton.core.key.domain.useKeys
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import java.security.SecureRandom
import javax.inject.Inject

data class PreparedMeeting(
    val encryptedMeetingNameB64: String,
    val saltB64: String,
    val sessionKeyB64: String,
    val srpModulusId: String,
    val srpSaltB64: String,
    val srpVerifierB64: String,
    val armoredPw: String?,
    val urlPasswordBase: String,
    val sessionKey: SessionKey, // used for re-encrypting event title later
)

private const val BasePassLength = 12
private const val EmptyUsername = ""

sealed interface PreparedMeetPayloadResult {
    data class Success(val payload: PreparedMeeting) : PreparedMeetPayloadResult
    data object NoAccountError : PreparedMeetPayloadResult
}

class PrepareMeetPayloadUseCase @Inject constructor(
    private val crypto: ProtonMeetCrypto,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val userManager: UserManager,
) {

    suspend fun execute(
        userId: UserId,
        meetingName: String,
    ): PreparedMeetPayloadResult {
        val sessionId = accountRepository.getAccountOrNull(userId)?.sessionId ?: return PreparedMeetPayloadResult.NoAccountError

        val password = crypto.randomPassword(BasePassLength)

        val (salt, passwordHash) = hashPasswordWithSalt(password)

        val sessionKey = crypto.pgpCrypto.generateNewSessionKey()
        val encryptedSessionKey = crypto.encryptSessionKey(sessionKey, passwordHash)

        val user = userManager.getUser(userId)
        val encryptedPassword = crypto.encryptAndSignText(user, password)

        val modulus = authRepository.randomModulus(sessionId)
        val srp = crypto.srpCrypto.calculatePasswordVerifier(
            username = EmptyUsername,
            password = password.encodeToByteArray(),
            modulusId = modulus.modulusId,
            modulus = modulus.modulus,
        )

        val encryptedMeetingName = crypto.encryptEventName(eventName = meetingName, sessionKey)

        return PreparedMeetPayloadResult.Success(
            PreparedMeeting(
                encryptedMeetingNameB64 = encryptedMeetingName,
                saltB64 = salt,
                sessionKeyB64 = encryptedSessionKey,
                srpModulusId = modulus.modulusId,
                srpSaltB64 = srp.salt,
                srpVerifierB64 = srp.verifier,
                armoredPw = encryptedPassword,
                urlPasswordBase = password,
                sessionKey = sessionKey,
            )
        )
    }

    private fun hashPasswordWithSalt(
        password: String,
    ): Pair<String, ByteArray> {
        val salt = crypto.pgpCrypto.generateNewKeySalt()
        val hashBytes = crypto.pgpCrypto.getPassphrase(
            password = password.encodeToByteArray(),
            encodedSalt = salt,
        )
        return salt to hashBytes
    }
}
