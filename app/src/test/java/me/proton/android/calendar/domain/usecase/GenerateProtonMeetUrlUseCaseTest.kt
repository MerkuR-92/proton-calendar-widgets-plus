package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateMeetingApiRequest
import me.proton.android.calendar.data.api.CreateMeetingApiResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ProtonMeetApi
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.UserAddress
import okhttp3.HttpUrl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class GenerateProtonMeetUrlUseCaseTest {

    private val logger: Logger = TestsLogger
    private val userId = UserId("test-user-id")

    private val meetBaseUrl: HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("meet.test")
        .build()

    private val meetApi: ProtonMeetApi = mockk()
    private val userAddressManager: UserAddressManager = mockk()
    private val prepareMeetPayloadUseCase: PrepareMeetPayloadUseCase = mockk()

    private lateinit var sut: GenerateProtonMeetUrlUseCase

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        sut = GenerateProtonMeetUrlUseCase(
            meetBaseUrl = meetBaseUrl,
            meetApi = meetApi,
            userAddressManager = userAddressManager,
            prepareMeetPayloadUseCase = prepareMeetPayloadUseCase,
            logger = logger,
        )
    }

    @Test
    fun `returns Error when no address found`() = runTest {
        // given
        coEvery { userAddressManager.getAddresses(userId) } returns emptyList()

        // when
        val result = sut.execute(meetingName = "Whatever", userId = userId)

        // then
        assertEquals(result, GenerateProtonMeetUrlUseCase.GenerateMeetUrlResult.GenericError)
    }

    @Test
    fun `returns Error when payload preparation fails with NoAccountError`() = runTest {
        // given
        coEvery { userAddressManager.getAddresses(userId) } returns listOf(address())
        coEvery {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = any()
            )
        } returns PreparedMeetPayloadResult.NoAccountError

        // when
        val result = sut.execute(meetingName = "Meeting name", userId = userId)

        // then
        assertEquals(result, GenerateProtonMeetUrlUseCase.GenerateMeetUrlResult.GenericError)
    }

    @Test
    fun `uses placeholder name when meetingName is null`() = runTest {
        // given
        coEvery { userAddressManager.getAddresses(userId) } returns listOf(address())
        coEvery {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = any()
            )
        } returns PreparedMeetPayloadResult.NoAccountError

        // when
        sut.execute(meetingName = null, userId = userId)

        // then
        coVerify {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = "New Proton Meet conference",
            )
        }
    }

    @Test
    fun `returns Error when api returns ApiResponse_Error`() = runTest {
        // given
        coEvery { userAddressManager.getAddresses(userId) } returns listOf(address())
        coEvery {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = any()
            )
        } returns PreparedMeetPayloadResult.Success(preparedPayload())

        coEvery {
            meetApi.getProtonMeetUrl(
                userId = userId,
                body = any<CreateMeetingApiRequest>()
            )
        } returns ApiResponse.Error(errorCode = 2500, httpCode = 500, error = "api error", shouldLog = false)

        // when
        val result = sut.execute(meetingName = "Name", userId = userId)

        // then
        assertEquals(result, GenerateProtonMeetUrlUseCase.GenerateMeetUrlResult.ApiError("api error"))
    }

    @Test
    fun `returns Error when api returns ApiResponse_Exception`() = runTest {
        // given
        coEvery { userAddressManager.getAddresses(userId) } returns listOf(address())
        coEvery {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = any()
            )
        } returns PreparedMeetPayloadResult.Success(preparedPayload())

        coEvery {
            meetApi.getProtonMeetUrl(
                userId = userId,
                body = any<CreateMeetingApiRequest>()
            )
        } returns ApiResponse.Exception(exception = RuntimeException("boom"))

        // when
        val result = sut.execute(meetingName = "Name", userId = userId)

        // then
        assertEquals(result, GenerateProtonMeetUrlUseCase.GenerateMeetUrlResult.GenericError)
    }

    @Test
    fun `returns Error when api returns success but meeting is null`() = runTest {
        // given
        coEvery { userAddressManager.getAddresses(userId) } returns listOf(address())
        coEvery {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = any()
            )
        } returns PreparedMeetPayloadResult.Success(preparedPayload())

        val apiResponse = CreateMeetingApiResponse(
            meeting = null,
            code = 500,
        )

        coEvery {
            meetApi.getProtonMeetUrl(
                userId = userId,
                body = any<CreateMeetingApiRequest>()
            )
        } returns ApiResponse.Success(apiResponse)

        // when
        val result = sut.execute(meetingName = "Name", userId = userId)

        // then
        assertEquals(result, GenerateProtonMeetUrlUseCase.GenerateMeetUrlResult.GenericError)
    }

    @Test
    fun `success builds correct url and returns expected data`() = runTest {
        // given
        val sessionKey: SessionKey = mockk()
        val payload = preparedPayload(sessionKey)
        val meetingLinkName = "meeting-123"

        coEvery { userAddressManager.getAddresses(userId) } returns listOf(address())

        coEvery {
            prepareMeetPayloadUseCase.execute(
                userId = userId,
                meetingName = "My meeting"
            )
        } returns PreparedMeetPayloadResult.Success(payload)

        val apiResponse = CreateMeetingApiResponse(
            code = 200,
            meeting = CreateMeetingApiResponse.Meeting(
                id = "meedId",
                meetingLinkName = meetingLinkName,
            ),
        )

        coEvery {
            meetApi.getProtonMeetUrl(
                userId = userId,
                body = any<CreateMeetingApiRequest>()
            )
        } returns ApiResponse.Success(apiResponse)

        // when
        val result = sut.execute(meetingName = "My meeting", userId = userId)

        // then
        result as GenerateProtonMeetUrlUseCase.GenerateMeetUrlResult.Success

        val expectedUrl =
            meetBaseUrl.toString() + "join/id-$meetingLinkName#pwd-${payload.urlPasswordBase}"

        assertThat(result.url).isEqualTo(expectedUrl)
        assertThat(result.meetingLinkNameConfId).isEqualTo(meetingLinkName)
        assertThat(result.encryptedTitle).isEqualTo(payload.encryptedMeetingNameB64)
        assertThat(result.sessionKey).isEqualTo(sessionKey)
    }

    private fun address(): UserAddress =
        mockk {
            every { addressId } returns AddressId("address-id")
            every { email } returns "test@email.com"
        }

    private fun preparedPayload(sessionKey: SessionKey = mockk()): PreparedMeeting =
        mockk {
            every { encryptedMeetingNameB64 } returns "encrypted-title-b64"
            every { saltB64 } returns "salt-b64"
            every { sessionKeyB64 } returns "session-key-b64"
            every { srpModulusId } returns "mod-id"
            every { srpSaltB64 } returns "srp-salt-b64"
            every { srpVerifierB64 } returns "srp-verifier-b64"
            every { armoredPw } returns "armored-pw"
            every { urlPasswordBase } returns "url-pwd"
            every { this@mockk.sessionKey } returns sessionKey
        }
}
