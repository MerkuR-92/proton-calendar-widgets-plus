package me.proton.android.calendar.domain.usecase

import android.util.Log
import biweekly.property.Attendee
import biweekly.util.Frequency
import biweekly.util.Recurrence
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.ApiResponseCode
import me.proton.android.calendar.common.ICalUtilsImpl
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


internal class HandleDeleteUseCaseTest {

    private lateinit var appDatabaseMock: AppDatabase

    private val calendarsApiMock: CalendarsApi = mockk()

    private val calendarsRepositoryMock: CalendarsRepository = mockk()

    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val sendEmailUseCaseMock: SendEmailUseCase = mockk()
    private val updateParticipationStatusUseCaseMock: UpdateParticipationStatusUseCase = mockk()

    private val testsLogger = TestsLogger

    private val userId = UserId("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true

        appDatabaseMock = mockk()

        coEvery { editCreateEventUseCaseMock.execute(userId, any(), any()) } returns UseCase.Result.Success(listOf(userId.id))

        coEvery { sendEmailUseCaseMock.sendInviteToAttendees(userId, any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

        coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

        coEvery { calendarsApiMock.syncEvents(userId, any(), any()) } returns ApiResponse.Success(
            getSyncEventsApiResponse()
        )
        coEvery { calendarsApiMock.getEventsByUid(userId, any(), any(), any()) } returns ApiResponse.Success(
            EventsByUidApiResponse(
                listOf(
                    getEventEntity()
                )
            )
        )

        coEvery { appDatabaseMock.membersDao().select(any()) } returns listOf(
            MemberEntity(
                id = "id",
                permissions = 64,
                email = "email@mail.com",
                calendarId = "calendarId"
            )
        )

        coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns getEventEntity() // We do not care about this EventEntity since it is just used as a parameter for transformEventUseCase and that is mocked above to return event
        coEvery { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) } returns getCalendarUserSettingsEntity()
        coEvery { calendarsRepositoryMock.selectRootEventEntity(any()) } returns getEventEntity()
        coEvery { calendarsRepositoryMock.deleteEventsById(any()) } just Runs
        coEvery { calendarsRepositoryMock.fetchEventById(userId, any(), any()) } returns ApiResponse.Success(
            EventApiResponse(
                event = getEventEntity()
            )
        )

        coEvery { sendEmailUseCaseMock.sendCancellationToAttendees(userId, any(), any(), any(), any())
        } returns UseCase.Result.Success<Unit>()
        coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(userId, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns UseCase.Result.Success<Unit>()

        coEvery { updateParticipationStatusUseCaseMock.execute(userId, any(), any(), any(), any(), any(), any())
        } returns UseCase.Result.Success<Unit>()
    }

    private fun getHandleDeleteUseCase(): HandleDeleteUseCase {
        return HandleDeleteUseCase(
            testsLogger,
            handleAlarmsUseCaseMock,
            calendarsApiMock,
            appDatabaseMock,
            editCreateEventUseCaseMock,
            transformEventUseCaseMock,
            calendarsRepositoryMock,
            sendEmailUseCaseMock,
            updateParticipationStatusUseCaseMock
        )
    }

    private fun getCalendarUserSettingsEntity(): CalendarUserSettingsEntity {
        return CalendarUserSettingsEntity(
            fkUserId = userId.id,
            weekLength = 7,
            displayWeekNumber = 1,
            autoDetectPrimaryTimezone = 1,
            primaryTimezone = "Europe/Paris",
            displaySecondaryTimezone = 0,
            secondaryTimezone = null,
            viewPreference = 0,
            defaultCalendarId = "calendarId"
        )
    }

    enum class SyncEventErrorType {
        ERROR_EVENT_DOES_NOT_EXIST,
        ERROR_DELETING_ON_SERVER,
        NO_ERROR
    }

    private fun getSyncEventsApiResponse(syncEventErrorType: SyncEventErrorType = SyncEventErrorType.NO_ERROR): SyncEventsApiResponse {
        val syncResponseWrapperList = when (syncEventErrorType) {
            SyncEventErrorType.ERROR_EVENT_DOES_NOT_EXIST -> listOf(
                SyncResponseWrapper(
                    index = 0,
                    response = SyncResponse(
                        code = ApiResponseCode.EVENT_DOES_NOT_EXIST,
                        event = getEventEntity()
                    )
                )
            )
            SyncEventErrorType.ERROR_DELETING_ON_SERVER -> listOf(
                SyncResponseWrapper(
                    index = 0,
                    response = SyncResponse(
                        code = 0,
                        event = getEventEntity()
                    )
                )
            )
            SyncEventErrorType.NO_ERROR -> listOf()
        }

        return SyncEventsApiResponse(
            responses = syncResponseWrapperList
        )
    }

    private fun prepareEvent(isRecurring: Boolean = false, hasAttendees: Boolean = false): Event {

        // TODO Handle Single edits by preparing second set of EventEntity & Event and mocking transformEventUseCaseMock parameter to match either

        val event = getEvent(isRecurring, hasAttendees)

        coEvery { transformEventUseCaseMock.execute(any()) } returns event

        return event
    }

    private fun getEvent(isRecurring: Boolean = false, hasAttendees: Boolean = false): Event {
        val baseICalIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
            BEGIN:VTIMEZONE
            TZID:Europe/Paris
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTAMP:20210914T132502Z
            UID:m-AMDWMT5erCPBet63epq0Sx4USc@proton.me
            STATUS:CONFIRMED
            SEQUENCE:0
            DTSTART;TZID=Europe/Paris:20210914T153000
            DTEND;TZID=Europe/Paris:20210914T160000
            SUMMARY:Single event
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER;RELATED=START:-PT15M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val baseICal = ICalUtilsImpl.parseICalString(baseICalIcs)!!

        // Recurring event
        if (isRecurring) baseICal.events.first().setRecurrenceRule(Recurrence.Builder(Frequency.DAILY).interval(1).count(10).build())

        // Event with attendees
        if (hasAttendees) baseICal.events.first().addAttendee(Attendee("adamtst", "adamtst@pm.me"))

        baseICal.setDefaultTimeZone("Europe/Paris")

        return Event.from(
            "id",
            Calendar(
                "calendarId",
                "calendar",
                "",
                1,
                true,
                0
            ),
            baseICal,
            null
        )!!
    }

    private fun getEventEntity(): EventEntity {
        return EventEntity(
            "id",
            "calendarId",
            "sharedEventId",
            "calendarKeyPacket",
            0L,
            0L,
            1,
            "sharedKeyPacket",
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            null
        )
    }

    @Test
    fun `handleDelete delete single event test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false, hasAttendees = false)

            /* Delete single event */
            assert(
                getHandleDeleteUseCase().handleDelete(
                    userId,
                    event.id,
                    EventEditDeleteOption.THIS_EVENT,
                    0
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { transformEventUseCaseMock.execute(any()) }
            coVerify(exactly = 1) { appDatabaseMock.membersDao().select(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) }
            coVerify(exactly = 0) { editCreateEventUseCaseMock.execute(userId, any(), any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.deleteEventsById(listOf(event.id)) }
            coVerify(exactly = 1) { handleAlarmsUseCaseMock.execute(userId) }
        }
    }

    @Test
    fun `handleDelete single event that doesn't exist on server anymore test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false, hasAttendees = false)

            /* Delete single event that doesn't exist on server anymore */
            coEvery { calendarsApiMock.syncEvents(userId, any(), any()) } returns ApiResponse.Success(
                getSyncEventsApiResponse(SyncEventErrorType.ERROR_EVENT_DOES_NOT_EXIST)
            )

            assert(
                getHandleDeleteUseCase().handleDelete(
                    userId,
                    event.id,
                    EventEditDeleteOption.THIS_EVENT,
                    0
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { transformEventUseCaseMock.execute(any()) }
            coVerify(exactly = 1) { appDatabaseMock.membersDao().select(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) }
            coVerify(exactly = 0) { editCreateEventUseCaseMock.execute(userId, any(), any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.deleteEventsById(listOf(event.id)) }
            coVerify(exactly = 1) { handleAlarmsUseCaseMock.execute(userId) }
        }
    }

    @Test
    fun `handleDelete ERROR delete single event test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false, hasAttendees = false)

            coEvery { calendarsApiMock.syncEvents(userId, any(), any()) } returns ApiResponse.Success(
                getSyncEventsApiResponse(SyncEventErrorType.ERROR_DELETING_ON_SERVER)
            )

            /* Error delete single event */
            assert(
                getHandleDeleteUseCase().handleDelete(
                    userId,
                    event.id,
                    EventEditDeleteOption.THIS_EVENT,
                    0
                ) is UseCase.Result.Error
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { transformEventUseCaseMock.execute(any()) }
            coVerify(exactly = 1) { appDatabaseMock.membersDao().select(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) }
            coVerify(exactly = 0) { editCreateEventUseCaseMock.execute(userId, any(), any()) }
            coVerify(exactly = 0) { calendarsRepositoryMock.deleteEventsById(listOf(event.id)) }
            coVerify(exactly = 0) { handleAlarmsUseCaseMock.execute(userId) }
        }
    }
}

