package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.KeysApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ManualRefreshUseCaseTest {

    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val ensureCalendarsCompleteUseCase: EnsureCalendarsCompleteUseCase = mockk(relaxed = true)
    private val refreshCalendarPassphraseUseCase: RefreshCalendarPassphraseUseCase = mockk()
    private val calendarsApi: CalendarsApi = mockk()
    private val accountManager: AccountManager = mockk()
    private val userSettingsRepository: UserSettingsRepository = mockk()
    private val database: AppDatabase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    private val userId = UserId("user-id")
    private val calendarId = "cal-1"
    private val timeZone = "UTC"

    private val useCase = ManualRefreshUseCase(
        calendarsRepository = calendarsRepository,
        ensureCalendarsCompleteUseCase = ensureCalendarsCompleteUseCase,
        refreshCalendarPassphraseUseCase = refreshCalendarPassphraseUseCase,
        calendarsApi = calendarsApi,
        accountManager = accountManager,
        userSettingsRepository = userSettingsRepository,
        database = database,
        logger = logger,
    )

    private fun calendar(id: String, owner: Boolean) = mockk<Calendar> {
        every { this@mockk.id } returns id
        every { isOwner } returns owner
    }

    private fun keysSuccess(): ApiResponse<KeysApiResponse> =
        ApiResponse.Success(mockk { every { keys } returns listOf(mockk(relaxed = true)) })

    @BeforeEach
    fun setUp() {
        mockkStatic("me.proton.android.calendar.common.CoreExtensionsKt")
        every { accountManager.getPrimaryUserId() } returns flowOf(userId)
        coEvery { userSettingsRepository.getWeekStart(any(), any()) } returns 1
        coEvery { calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id) } returns timeZone
        coEvery { ensureCalendarsCompleteUseCase.execute(userId) } returns true
        coEvery { calendarsRepository.selectAllCalendars(userId.id) } returns listOf(calendar(calendarId, owner = true))
        coEvery { calendarsApi.getKeys(userId, calendarId) } returns keysSuccess()
        coEvery { refreshCalendarPassphraseUseCase(userId, calendarId) } returns UseCase.Result.Success<Unit>()
        every { calendarsRepository.flowVisibleCalendarIds(userId.id) } returns flowOf(listOf(calendarId))
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `ensures calendars, resyncs owned crypto, then fetches events`() = runBlocking {
        val result = useCase.execute()

        assertThat(result).isInstanceOf(UseCase.Result.Success::class)
        coVerify(exactly = 1) { ensureCalendarsCompleteUseCase.execute(userId) }
        coVerify(exactly = 1) { calendarsApi.getKeys(userId, calendarId) }
        coVerify(exactly = 1) { calendarsRepository.persistCalendarKey(any()) }
        coVerify(exactly = 1) { refreshCalendarPassphraseUseCase(userId, calendarId) }
        coVerify(exactly = 1) { calendarsRepository.fetchEvents(userId, any(), any(), timeZone, force = true) }
    }

    @Test
    fun `only owned calendars have their crypto resynced`() = runBlocking {
        coEvery { calendarsRepository.selectAllCalendars(userId.id) } returns
            listOf(calendar(calendarId, owner = true), calendar("shared", owner = false))

        useCase.execute()

        coVerify(exactly = 1) { calendarsApi.getKeys(userId, calendarId) }
        coVerify(exactly = 0) { calendarsApi.getKeys(userId, "shared") }
    }

    @Test
    fun `key refresh failure is best-effort and still fetches events`() = runBlocking {
        coEvery { calendarsApi.getKeys(userId, calendarId) } returns ApiResponse.Error(500, 0, "boom", false)

        val result = useCase.execute()

        assertThat(result).isInstanceOf(UseCase.Result.Success::class)
        coVerify(exactly = 0) { calendarsRepository.persistCalendarKey(any()) }
        coVerify(exactly = 0) { refreshCalendarPassphraseUseCase(any(), any()) }
        coVerify(exactly = 1) { calendarsRepository.fetchEvents(userId, any(), any(), timeZone, force = true) }
    }

    @Test
    fun `skips event fetch when no calendars are visible`() = runBlocking {
        every { calendarsRepository.flowVisibleCalendarIds(userId.id) } returns flowOf(emptyList())

        val result = useCase.execute()

        assertThat(result).isInstanceOf(UseCase.Result.Success::class)
        coVerify(exactly = 1) { ensureCalendarsCompleteUseCase.execute(userId) }
        coVerify(exactly = 0) { calendarsRepository.fetchEvents(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `returns error and does nothing when no user is logged in`() = runBlocking {
        every { accountManager.getPrimaryUserId() } returns flowOf(null)

        val result = useCase.execute()

        assertThat(result).isInstanceOf(UseCase.Result.Error::class)
        coVerify(exactly = 0) { ensureCalendarsCompleteUseCase.execute(any()) }
        coVerify(exactly = 0) { calendarsRepository.fetchEvents(any(), any(), any(), any(), any()) }
    }
}
