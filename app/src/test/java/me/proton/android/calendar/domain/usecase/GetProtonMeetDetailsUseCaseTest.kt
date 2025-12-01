package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.MeetingDetailsResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ProtonMeetApi
import me.proton.android.calendar.domain.usecase.GetProtonMeetDetailsUseCase.MeetingDetailsResult
import me.proton.android.calendar.domain.utils.ProtonMeetCrypto
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.crypto.common.pgp.DecryptedText
import me.proton.core.crypto.common.pgp.SessionKey
import me.proton.core.crypto.common.pgp.VerificationStatus
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetProtonMeetDetailsUseCaseTest {

    private val logger: Logger = TestsLogger
    private val testUserId: UserId = userId

    private val meetApi: ProtonMeetApi = mockk()
    private val protonMeetCrypto: ProtonMeetCrypto = mockk()
    private val userManager: UserManager = mockk()

    private lateinit var sut: GetProtonMeetDetailsUseCase
    private lateinit var user: User

    @BeforeEach
    fun `before each`() {
        clearAllMocks()

        user = mockk(relaxed = true)
        coEvery { userManager.getUser(testUserId) } returns user

        sut = GetProtonMeetDetailsUseCase(
            meetApi = meetApi,
            protonMeetCrypto = protonMeetCrypto,
            userManager = userManager,
            logger = logger,
        )
    }

    @Test
    fun `returns Error when api returns ApiResponse_Error`() = runTest {
        // given
        coEvery {
            meetApi.getProtonMeetDetails(
                userId = testUserId,
                meetingLinkName = "MEETING_LINK"
            )
        } returns ApiResponse.Error(
            errorCode = 2500,
            httpCode = 500,
            error = "api error",
            shouldLog = false,
        )

        // when
        val result = sut.execute(meetingLinkName = "MEETING_LINK", userId = testUserId)

        // then
        assertThat(result).isInstanceOf(MeetingDetailsResult.Error::class)
    }

    @Test
    fun `returns Error when api returns success but meeting is null`() = runTest {
        // given
        val apiResponse = MeetingDetailsResponse(
            meeting = null,
            code = 500,
        )

        coEvery {
            meetApi.getProtonMeetDetails(
                userId = testUserId,
                meetingLinkName = "MEETING_LINK"
            )
        } returns ApiResponse.Success(apiResponse)

        // when
        val result = sut.execute(meetingLinkName = "MEETING_LINK", userId = testUserId)

        // then
        assertThat(result).isInstanceOf(MeetingDetailsResult.Error::class)
    }

    @Test
    fun `returns Error when meeting password is missing`() = runTest {
        // given
        val apiResponse = MeetingDetailsResponse(
            meeting = MeetingDetailsResponse.Meeting(
                passwordArmored = null,
                sessionKey = "encrypted-session-key",
                salt = "salt-b64",
            ),
            code = 200,
        )

        coEvery {
            meetApi.getProtonMeetDetails(
                userId = testUserId,
                meetingLinkName = "MEETING_LINK"
            )
        } returns ApiResponse.Success(apiResponse)

        // when
        val result = sut.execute(meetingLinkName = "MEETING_LINK", userId = testUserId)

        // then
        assertThat(result).isInstanceOf(MeetingDetailsResult.Error::class)
    }

    @Test
    fun `returns Error when decrypting password fails`() = runTest {
        // given
        val apiResponse = MeetingDetailsResponse(
            meeting = MeetingDetailsResponse.Meeting(
                passwordArmored = "encrypted-password",
                sessionKey = "encrypted-session-key",
                salt = "salt-b64",
            ),
            code = 200,
        )

        coEvery {
            meetApi.getProtonMeetDetails(
                userId = testUserId,
                meetingLinkName = "MEETING_LINK"
            )
        } returns ApiResponse.Success(apiResponse)

        every {
            protonMeetCrypto.decryptPassword(
                user = user,
                encryptedPassword = "encrypted-password"
            )
        } returns DecryptedText(
            text = "",
            status = VerificationStatus.Failure
        )

        // when
        val result = sut.execute(meetingLinkName = "MEETING_LINK", userId = testUserId)

        // then
        assertThat(result).isInstanceOf(MeetingDetailsResult.Error::class)
    }

    @Test
    fun `returns Error when decrypting session key fails`() = runTest {
        // given
        val apiResponse = MeetingDetailsResponse(
            meeting = MeetingDetailsResponse.Meeting(
                passwordArmored = "encrypted-password",
                sessionKey = "encrypted-session-key",
                salt = "salt-b64",
            ),
            code = 200,
        )

        coEvery {
            meetApi.getProtonMeetDetails(
                userId = testUserId,
                meetingLinkName = "MEETING_LINK"
            )
        } returns ApiResponse.Success(apiResponse)

        every {
            protonMeetCrypto.decryptPassword(
                user = user,
                encryptedPassword = "encrypted-password"
            )
        } returns DecryptedText(
            text = "plain-password",
            status = VerificationStatus.Success
        )

        every {
            protonMeetCrypto.decryptSessionKey(
                encryptedSessionKeyB64 = "encrypted-session-key",
                password = "plain-password",
                saltB64 = "salt-b64",
            )
        } returns null

        // when
        val result = sut.execute(meetingLinkName = "MEETING_LINK", userId = testUserId)

        // then
        assertThat(result).isInstanceOf(MeetingDetailsResult.Error::class)
    }

    @Test
    fun `success returns expected session key`() = runTest {
        // given
        val expectedSessionKey: SessionKey = mockk()

        val apiResponse = MeetingDetailsResponse(
            meeting = MeetingDetailsResponse.Meeting(
                passwordArmored = "encrypted-password",
                sessionKey = "encrypted-session-key",
                salt = "salt-b64",
            ),
            code = 200,
        )

        coEvery {
            meetApi.getProtonMeetDetails(
                userId = testUserId,
                meetingLinkName = "MEETING_LINK"
            )
        } returns ApiResponse.Success(apiResponse)

        every {
            protonMeetCrypto.decryptPassword(
                user = user,
                encryptedPassword = "encrypted-password"
            )
        } returns DecryptedText(
            text = "plain-password",
            status = VerificationStatus.Success
        )

        every {
            protonMeetCrypto.decryptSessionKey(
                encryptedSessionKeyB64 = "encrypted-session-key",
                password = "plain-password",
                saltB64 = "salt-b64",
            )
        } returns expectedSessionKey

        // when
        val result = sut.execute(meetingLinkName = "MEETING_LINK", userId = testUserId)

        // then
        result as MeetingDetailsResult.Success
        assertThat(result.sessionKey).isEqualTo(expectedSessionKey)
    }
}
