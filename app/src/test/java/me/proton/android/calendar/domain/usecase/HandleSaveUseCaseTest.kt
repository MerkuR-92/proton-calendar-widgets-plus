package me.proton.android.calendar.domain.usecase

import android.util.Log
import biweekly.property.Attendee
import biweekly.util.Frequency
import biweekly.util.Recurrence
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.ICalUtilsImpl
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.ICalUtilsImpl.setStartTimeZone
import me.proton.android.calendar.common.ICalUtilsImpl.wrapInICalendar
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

internal class HandleSaveUseCaseTest {

    private val calendarsRepositoryMock: CalendarsRepository = mockk()

    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val handleDeleteUseCaseMock: HandleDeleteUseCase = mockk()
    private val sendEmailUseCaseMock: SendEmailUseCase = mockk()

    private val testsLogger = TestsLogger

    private val userId = UserId("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")

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

    private fun getUserSettingsEntity(): UserSettingsEntity {
        return UserSettingsEntity(
            fkUserId = userId.id,
            weekStart = 1, // 0: Locale default, 1: Monday, 6: Saturday 7: Sunday
            dateFormat = 1, // 0: Locale default, 1: DD_MM_YYYY, 2: MM_DD_YYYY, 3: YYYY_MM_DD
            timeFormat = 1 // 0: Locale default, 1: 24H, 2: 12H
        )
    }

    private fun prepareEvent(isRecurring: Boolean, hasAttendees: Boolean): Event {

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

        val event = Event.from(
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

        coEvery { transformEventUseCaseMock.execute(any()) } returns event

        val eventEntity = EventEntity(
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

        // We do not care about this EventEntity since it is just used as a parameter for transformEventUseCase and that is mocked above to return event
        coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns eventEntity

        return event
    }

    @Test
    fun `handleSave create single event test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false, hasAttendees = false)

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
                    eventTimeZoneId = "Europe/Paris",
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

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = true, hasAttendees = false)

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
                    eventTimeZoneId = "Europe/Paris",
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

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false, hasAttendees = true)

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
                    eventTimeZoneId = "Europe/Paris",
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

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = true, hasAttendees = true)

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
                    eventTimeZoneId = "Europe/Paris",
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
