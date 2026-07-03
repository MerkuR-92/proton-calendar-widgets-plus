@file:OptIn(ExperimentalCoroutinesApi::class)

package me.proton.android.calendar.domain.usecase

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetMinimalCalendarEventsUseCaseTest {

    private val userId = UserId("u1")
    private val calendarId = "cal1"

    private val loadingState = LoadingStateUseCase()

    private val calendarsRepository = mockk<CalendarsRepository>(relaxed = true)
    private val userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val fetchEventsUseCase = mockk<FetchEventsUseCase>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val updateAlarmsUseCase = mockk<UpdateAlarmsUseCase>(relaxed = true)
    private val updateEventOccurrencesUseCase = mockk<UpdateEventOccurrencesUseCase>(relaxed = true)

    private val useCase = GetMinimalCalendarEventsUseCase(
        calendarsRepository,
        userSettingsRepository,
        database,
        fetchEventsUseCase,
        logger,
        updateAlarmsUseCase,
        updateEventOccurrencesUseCase,
        loadingState,
    )

    @BeforeEach
    fun setUp() {
        coEvery { calendarsRepository.selectCalendarUserSettings(any()) } returns null
        mockkStatic("me.proton.android.calendar.common.CoreExtensionsKt")
        coEvery { userSettingsRepository.getWeekStart(any(), any()) } returns 1
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private suspend fun inProgress() = loadingState.invoke().first().inProgress()

    @Test
    fun `clears fetching marker when cancelled mid-fetch`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        // hang mid-fetch so we can cancel while marked fetching
        coEvery { fetchEventsUseCase.splitFetchEvents(any(), any(), any(), any(), any()) } coAnswers {
            fetchStarted.complete(Unit)
            awaitCancellation()
        }

        val job = launch { useCase.execute(userId, calendarId) }

        fetchStarted.await()
        assertTrue(inProgress(), "calendar should be marked fetching while the fetch is in-flight")

        job.cancelAndJoin()

        assertFalse(inProgress(), "cancellation mid-fetch must still clear the fetching marker")
    }

    @Test
    fun `clears fetching marker on fetch error`() = runTest {
        coEvery {
            fetchEventsUseCase.splitFetchEvents(any(), any(), any(), any(), any())
        } returns FetchEventsUseCase.ResultData(UseCase.Result.Error("boom"), null)

        useCase.execute(userId, calendarId)

        assertFalse(inProgress())
    }
}
