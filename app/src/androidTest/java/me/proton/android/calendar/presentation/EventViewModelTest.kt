package me.proton.android.calendar.presentation

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import biweekly.property.Attendee
import biweekly.property.RecurrenceId
import biweekly.util.Frequency
import biweekly.util.Recurrence
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.ICalUtilsImpl
import me.proton.android.calendar.common.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.UserSettingsRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.Delinquent
import me.proton.core.user.domain.entity.Role
import me.proton.core.user.domain.entity.User
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EventViewModelTest {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var appDatabaseMock: AppDatabase
    private lateinit var protonCalendarApplicationMock: Application
    private lateinit var resourceProviderMock: ResourceProvider

    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val userSettingsRepositoryMock: UserSettingsRepository = mockk()

    private val userManagerMock: UserManager = mockk()

    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val updateParticipationStatusUseCaseMock: UpdateParticipationStatusUseCase = mockk()
    private val sendEmailUseCaseMock: SendEmailUseCase = mockk()
    private val getCanonicalEmailsUseCaseMock: GetCanonicalEmailsUseCase = mockk()
    private val obtainSendPreferencesUseCaseMock: ObtainSendPreferencesUseCase = mockk()
    private val handleSaveUseCaseMock: HandleSaveUseCase = mockk()
    private val handleDeleteUseCaseMock: HandleDeleteUseCase = mockk()
    private val updateCalendarUseCaseMock: UpdateCalendarUseCase = mockk()

    private val testsLogger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }

    private val userId = UserId("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")
    private val calendarId = "calendarId"
    private val calendarSettingsId = "calendarSettingsId"
    private val eventId = "eventId"
    private val singleEditEventId = "singleEditEventId"
    private val eventUid = "m-AMDWMT5erCPBet63epq0Sx4USc@proton.me"

    private val defaultTimezone = "Europe/Paris"
    private val defaultEventDuration = 30

    @Before
    fun beforeEach() {
        clearAllMocks()
        appDatabaseMock = mockk()
        protonCalendarApplicationMock = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        resourceProviderMock = mockk()

        coEvery { calendarsRepositoryMock.getDefaultCalendarId(userId.id) } returns calendarId
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns getCalendarEntity()
        // TODO Test fallback to first active user calendar when default calendar is null
        coEvery { calendarsRepositoryMock.getActiveUserCalendars(userId.id) } returns listOf(getCalendarEntity())
        coEvery { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) } returns getCalendarUserSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns getCalendarSettingsEntity()
        coEvery { userSettingsRepositoryMock.selectUserSettings(userId.id) } returns getUserSettingsEntity()
        coEvery { userManagerMock.getUser(userId) } returns getUser()

        coEvery { calendarsRepositoryMock.selectEventEntity(eventId) } returns getEventEntity()
        coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent()

    }

    /**
     *   Utils private methods
     */

    private fun getEventViewModel(): EventViewModel {
        return EventViewModel(
            application = protonCalendarApplicationMock,
            userManager = userManagerMock,
            calendarsRepository = calendarsRepositoryMock,
            userSettingsRepository = userSettingsRepositoryMock,
            transformEventUseCase = transformEventUseCaseMock,
            updateParticipationStatusUseCase = updateParticipationStatusUseCaseMock,
            sendEmailUseCase = sendEmailUseCaseMock,
            logger = testsLogger,
            json = json,
            getCanonicalEmailsUseCase = getCanonicalEmailsUseCaseMock,
            obtainSendPreferencesUseCase = obtainSendPreferencesUseCaseMock,
            handleSaveUseCase = handleSaveUseCaseMock,
            handleDeleteUseCase = handleDeleteUseCaseMock,
            updateCalendarUseCase = updateCalendarUseCaseMock,
            resourceProvider = resourceProviderMock
        )
    }

    private suspend fun getInitialisedEventViewModel(
        editMode: Boolean,
        eventId: String?,
        occurrenceNumber: Int?,
        initStartDate: String?,
        initStartTime: String?
    ): EventViewModel {
        val eventViewModel = getEventViewModel()
        eventViewModel.initialise(
            userId,
            editMode,
            eventId,
            occurrenceNumber,
            initStartDate,
            initStartTime
        )

        if (editMode) {
            coVerify(exactly = 1) { calendarsRepositoryMock.getDefaultCalendarId(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendar(any()) }
            // TODO Uncomment this when testing invalid default calendar
            //coVerify(exactly = 1) { calendarsRepositoryMock.getActiveUserCalendars(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(any()) }
        }

        coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(any()) }
        coVerify(exactly = 1) { userSettingsRepositoryMock.selectUserSettings(any()) }
        coVerify(exactly = 1) { userManagerMock.getUser(any()) }

        if (eventId != null && eventId == singleEditEventId) {
            // If we edit existing single edit event
            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectRootEventEntity(any()) }
            coVerify(exactly = 2) { transformEventUseCaseMock.execute(any()) }
        } else if (eventId != null) {
            // If we edit existing event
            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { transformEventUseCaseMock.execute(any()) }
        }

        return eventViewModel
    }

    private fun getCalendarEntity(): CalendarEntity {
        return CalendarEntity(
            id = calendarId,
            name = "CalendarName",
            description = "CalendarDescription",
            color = "#000000",
            display = 1,
            flags = 1,
            type = 0,
            fkUserId = userId.id
        )
    }

    private fun getCalendarSettingsEntity(): CalendarSettingsEntity {
        return CalendarSettingsEntity(
            id = calendarSettingsId,
            calendarId = calendarId,
            defaultEventDuration = defaultEventDuration,
            defaultPartDayNotifications = emptyList(), // TODO Test default part day notifications
            defaultFullDayNotifications = emptyList() // TODO Test default full day notifications
        )
    }

    private fun getCalendarUserSettingsEntity(): CalendarUserSettingsEntity {
        return CalendarUserSettingsEntity(
            fkUserId = userId.id,
            weekLength = 7,
            displayWeekNumber = 1,
            autoDetectPrimaryTimezone = 1,
            primaryTimezone = defaultTimezone,
            displaySecondaryTimezone = 0,
            secondaryTimezone = null,
            viewPreference = 0,
            defaultCalendarId = calendarId
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

    private fun getUser(): User {
        return User(
            userId = userId,
            email = "user@email.com",
            name = "UserName",
            displayName = "DisplayName",
            currency = "EUR",
            credit = 50,
            usedSpace = 0,
            maxSpace = 3096,
            maxUpload = 3096,
            role = Role.NoOrganization,
            private = true,
            services = 1,
            subscribed = 1,
            delinquent = Delinquent.None,
            keys = emptyList()
        )
    }

    private fun getEvent(isRecurring: Boolean = false, hasAttendees: Boolean = false, isSingleEdit: Boolean = false): Event {
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
        // All day single edit on second occurrence
        else if (isSingleEdit) baseICal.events.first().recurrenceId = RecurrenceId(
            LocalDate.of(2021, 9, 15).toDate(TimeZone.getDefault().id),
            false
        )

        // Event with attendees
        if (hasAttendees) baseICal.events.first().addAttendee(Attendee("adamtst", "adamtst@pm.me"))

        baseICal.setDefaultTimeZone(defaultTimezone)

        return Event.from(
            if (isSingleEdit) singleEditEventId else eventId,
            Calendar(
                calendarId,
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

    private fun getEventEntity(isSingleEdit: Boolean = false): EventEntity {
        return EventEntity(
            if (isSingleEdit) singleEditEventId else eventId,
            calendarId,
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

    /**
     *   EventViewModel.initialise Tests
     */

    @Test
    fun initialiseCreateAllDayEventTest() {
        runBlocking {

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            val startDate = ZonedDateTime.of(
                LocalDate.now(),
                LocalTime.of(0, 0),
                ZoneId.of(defaultTimezone)
            )

            assert(eventViewModel.eventLiveData.value?.getStart(TimeZone.getDefault().id) == startDate)
            assert(eventViewModel.eventLiveData.value?.getStart(TimeZone.getDefault().id) == startDate)
            assert(eventViewModel.eventLiveData.value?.getEnd(TimeZone.getDefault().id) == startDate)

            assert(eventViewModel.eventLiveData.value?.isAllDay() == true)

        }
    }

    @Test
    fun initialiseCreatePartDayEventTest() {
        runBlocking {

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = 0,
                initStartDate = "2021-10-01",
                initStartTime = "12:15:28.054"
            )

            val startDate = LocalDate.of(2021, 10, 1)
            val startTime = LocalTime.of(12, 15)
            val endTime = LocalTime.of(12, 45)

            assert(eventViewModel.eventLiveData.value?.getStart(TimeZone.getDefault().id)?.toLocalDate() == startDate)
            assert(eventViewModel.eventLiveData.value?.getStart(TimeZone.getDefault().id)?.toLocalTime() == startTime)
            assert(eventViewModel.eventLiveData.value?.getEnd(TimeZone.getDefault().id)?.toLocalTime() == endTime)

            assert(eventViewModel.eventLiveData.value?.isAllDay() == false)

        }
    }

    @Test
    fun initialiseViewEventDetailsTest() {
        runBlocking {

            val eventViewModel = getEventViewModel()
            eventViewModel.initialise(
                userId,
                editMode = false,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )
        }
    }

    @Test
    fun initialiseEditEventTest() {
        runBlocking {

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null)
        }
    }

    @Test
    fun initialiseEditSingleEditTest() {
        runBlocking {

            // Mock single edit event
            val singleEditEventEntity = getEventEntity(isSingleEdit = true)
            val singleEditEvent =  getEvent(isSingleEdit = true)
            coEvery { calendarsRepositoryMock.selectEventEntity(singleEditEventId) } returns singleEditEventEntity
            coEvery { transformEventUseCaseMock.execute(singleEditEventEntity) } returns singleEditEvent

            // Mock root event
            val rootEventEntity = getEventEntity()
            val rootEvent = getEvent(isRecurring = true)
            coEvery { calendarsRepositoryMock.selectRootEventEntity(eventUid) } returns rootEventEntity
            coEvery { transformEventUseCaseMock.execute(rootEventEntity) } returns rootEvent

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = singleEditEventId,
                occurrenceNumber = 2,
                initStartDate = null,
                initStartTime = null)

            // Test: Clone RRule from original event in DB if we are in edit mode
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value != null)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value?.frequency == Frequency.DAILY)

            assert(eventViewModel.eventLiveData.value?.id == singleEditEventId)

        }
    }
}
