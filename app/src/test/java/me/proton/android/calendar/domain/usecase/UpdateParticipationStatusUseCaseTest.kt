package me.proton.android.calendar.domain.usecase

import android.util.Log
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.AttendeeApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEvent
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEventResponse
import me.proton.android.calendar.test.shared.mocks.userId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class UpdateParticipationStatusUseCaseTest {

    private val calendarsApiMock: CalendarsApi = mockk()
    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val updatePersonalPartUseCaseMock: UpdatePersonalPartUseCase = mockk()
    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    private val updateEventOccurrencesUseCaseMock: UpdateEventOccurrencesUseCase = mockk()

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true

        coEvery { calendarsApiMock.updateParticipationStatus(userId, any(), any(), any(), any()) } returns
            ApiResponse.Success(AttendeeApiResponse(provideEventResponse()))
        coEvery { updatePersonalPartUseCaseMock.execute(userId, any(), any(), any()) } returns
            UseCase.Result.Success<Unit>()
        coEvery { calendarsApiMock.getEventsByUid(userId, any(), any(), any()) } returns
            ApiResponse.Success(EventsByUidApiResponse(emptyList()))
        coEvery { calendarsRepositoryMock.persistEvents(*anyVararg()) } returns Unit
        coEvery { handleAlarmsUseCaseMock.execute(userId, any()) } returns UseCase.Result.Success<Unit>()
        coEvery { updateEventOccurrencesUseCaseMock.execute(any(), any()) } returns Unit
    }

    private fun useCase() = UpdateParticipationStatusUseCase(
        TestsLogger,
        calendarsApiMock,
        calendarsRepositoryMock,
        updatePersonalPartUseCaseMock,
        handleAlarmsUseCaseMock,
        updateEventOccurrencesUseCaseMock
    )

    @Test
    fun `executeClearSingleEdits writes each single edit to its own calendar`() {
        runBlocking {
            // a single edit can come from a different calendar - the api call must target that one
            val here = provideEvent(isSingleEdit = true, isAttendee = true)
            val elsewhere = Event.from(
                provideEvent(isSingleEdit = true, isAttendee = true),
                id = "edit-elsewhere",
                calendar = here.calendar.copy(id = "other-calendar")
            )

            coEvery { calendarsRepositoryMock.getSingleEdits(userId, any(), any(), any()) } returns
                listOf(here, elsewhere)

            useCase().executeClearSingleEdits(
                userId = userId,
                calendarId = here.calendar.id,
                eventUid = here.uid,
                userEmails = listOf("nobody@proton.me"),
                mainChainStatus = 0
            )

            coVerify(exactly = 1) {
                calendarsApiMock.updateParticipationStatus(userId, here.calendar.id, here.id, any(), any())
            }
            coVerify(exactly = 1) {
                calendarsApiMock.updateParticipationStatus(userId, "other-calendar", "edit-elsewhere", any(), any())
            }
            coVerify(exactly = 0) {
                calendarsApiMock.updateParticipationStatus(userId, here.calendar.id, "edit-elsewhere", any(), any())
            }
        }
    }
}
