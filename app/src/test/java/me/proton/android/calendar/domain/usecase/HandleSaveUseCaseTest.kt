package me.proton.android.calendar.domain.usecase

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.mocks.*
import me.proton.android.calendar.mocks.EventMocks.getEvent
import me.proton.android.calendar.mocks.EventMocks.getEventEntity
import me.proton.android.calendar.mocks.UserMocks.getUserSettingsEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HandleSaveUseCaseTest {

    private val calendarsRepositoryMock: CalendarsRepository = mockk()

    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val handleDeleteUseCaseMock: HandleDeleteUseCase = mockk()
    private val sendEmailUseCaseMock: SendEmailUseCase = mockk()

    private val testsLogger = TestsLogger

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true

        coEvery { handleDeleteUseCaseMock.handleDeleteSingleEdits(userId, any(), any()) } returns UseCase.Result.Success<Unit>()
        coEvery { editCreateEventUseCaseMock.execute(userId, any(), any()) } returns UseCase.Result.Success(listOf(userId.id))
        coEvery { sendEmailUseCaseMock.sendInviteToAttendees(userId, any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()
    }

    private fun getHandleSaveUseCase(): HandleSaveUseCase {
        return HandleSaveUseCase(
            testsLogger,
            calendarsRepositoryMock,
            transformEventUseCaseMock,
            editCreateEventUseCaseMock,
            handleDeleteUseCaseMock,
            sendEmailUseCaseMock
        )
    }

    @Test
    fun `handleSave create single event test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns getEventEntity()
            val event = getEvent(isRecurring = false)
            coEvery { transformEventUseCaseMock.execute(any()) } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = getUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    recurrenceManuallyEdited = false,
                    isCreate = true
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `handleSave create recurring event test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns getEventEntity()
            val event = getEvent(isRecurring = true)
            coEvery { transformEventUseCaseMock.execute(any()) } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = getUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    recurrenceManuallyEdited = false,
                    isCreate = true
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `handleSave create single event with attendees test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns getEventEntity()
            val event = getEvent(isRecurring = false, isOrganizer = true)
            coEvery { transformEventUseCaseMock.execute(any()) } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = getUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    recurrenceManuallyEdited = false,
                    isCreate = true
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }

            coVerify(exactly = 1) {
                sendEmailUseCaseMock.sendInviteToAttendees(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `handleSave create recurring event with attendees test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns getEventEntity()
            val event = getEvent(isRecurring = true, isOrganizer = true)
            coEvery { transformEventUseCaseMock.execute(any()) } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = getUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    recurrenceManuallyEdited = false,
                    isCreate = true
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }

            coVerify(exactly = 1) {
                sendEmailUseCaseMock.sendInviteToAttendees(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }
}
