package me.proton.android.calendar.test.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.domain.usecase.PrepareMeetPayloadUseCase
import me.proton.android.calendar.domain.usecase.PreparedMeetPayloadResult
import me.proton.android.calendar.domain.utils.ProtonMeetCrypto
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.auth.domain.entity.Modulus
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.crypto.android.context.AndroidCryptoContext
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.DecryptedText
import me.proton.core.crypto.common.pgp.PGPCrypto
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.crypto.common.pgp.VerificationStatus
import me.proton.core.crypto.common.srp.Auth
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PrepareMeetPayloadUseCaseTest {

    private lateinit var cryptoContext: CryptoContext
    private lateinit var protonMeetCrypto: ProtonMeetCrypto
    private lateinit var srpCrypto: SrpCrypto

    private val accountRepository = mockk<AccountRepository>()
    private val authRepository = mockk<AuthRepository>()
    private val userManager = mockk<UserManager>()

    private val testUserId: UserId = userId

    @Before
    fun setUp() {
        clearAllMocks()

        cryptoContext = AndroidCryptoContext()

        srpCrypto = mockk()
        coEvery {
            srpCrypto.calculatePasswordVerifier(
                username = any(),
                password = any(),
                modulusId = any(),
                modulus = any()
            )
        } returns Auth(
            salt = "srpSalt",
            verifier = "srpVerifier",
            version = 1,
            modulusId = "modId",
        )

        protonMeetCrypto = object : ProtonMeetCrypto {

            override fun encryptAndSignText(
                user: User,
                text: String,
            ): String = "encryptedPassword"

            override fun randomPassword(len: Int): String = "random123456"

            override fun encryptEventName(
                eventName: String,
                sessionKey: SessionKey
            ): String = "encryptedEventName"

            override fun decryptSessionKey(
                encryptedSessionKeyB64: String,
                password: String,
                saltB64: String
            ): SessionKey? = null

            override fun encryptSessionKey(
                sessionKey: SessionKey,
                passwordHash: ByteArray
            ): String = "not_needed"

            override fun decryptPassword(
                user: User,
                encryptedPassword: String
            ): DecryptedText = DecryptedText("not_needed", VerificationStatus.Failure)

            override val pgpCrypto: PGPCrypto = cryptoContext.pgpCrypto
            override val srpCrypto: SrpCrypto = this@PrepareMeetPayloadUseCaseTest.srpCrypto
        }

        val user = mockk<User>(relaxed = true)
        every { user.keys } returns emptyList()
        coEvery { userManager.getUser(testUserId) } returns user

        val account = mockk<Account> {
            every { sessionId } returns SessionId("sessionId")
        }
        coEvery { accountRepository.getAccountOrNull(testUserId) } returns account

        val modulus = mockk<Modulus> {
            every { modulusId } returns "mod-id"
            every { modulus } returns "modulus-string"
        }
        coEvery { authRepository.randomModulus(SessionId("sessionId")) } returns modulus
    }

    @Test
    fun argError() = runBlocking {
        coEvery { accountRepository.getAccountOrNull(testUserId) } returns null

        val result = sut().execute(testUserId, "My meeting")

        assertTrue(result is PreparedMeetPayloadResult.NoAccountError)
    }

    @Test
    fun argErrorWhenSessionIdNull() = runBlocking {
        val account = mockk<Account> {
            every { sessionId } returns null
        }
        coEvery { accountRepository.getAccountOrNull(testUserId) } returns account

        val result = sut().execute(testUserId, "My meeting")

        assertTrue(result is PreparedMeetPayloadResult.NoAccountError)
    }

    @Test
    fun successGeneratesValidPayload() = runBlocking {
        val result = sut().execute(testUserId, "My meeting")

        assertTrue(result is PreparedMeetPayloadResult.Success)
        val payload = (result as PreparedMeetPayloadResult.Success).payload

        assertEquals("mod-id", payload.srpModulusId)
        assertEquals("srpSalt", payload.srpSaltB64)
        assertEquals("srpVerifier", payload.srpVerifierB64)

        assertEquals("encryptedPassword", payload.armoredPw)
        assertEquals("encryptedEventName", payload.encryptedMeetingNameB64)

        assertTrue(payload.urlPasswordBase.isNotBlank())
        assertEquals(12, payload.urlPasswordBase.length)

        assertTrue(payload.sessionKeyB64.isNotBlank())
        assertTrue(payload.saltB64.isNotBlank())
        assertTrue(payload.encryptedMeetingNameB64.isNotBlank())
    }

    private fun sut(): PrepareMeetPayloadUseCase =
        PrepareMeetPayloadUseCase(
            crypto = protonMeetCrypto,
            accountRepository = accountRepository,
            authRepository = authRepository,
            userManager = userManager,
        )
}