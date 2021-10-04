package me.proton.android.calendar.presentation

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import biweekly.util.Frequency
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.UserSettingsRepository
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.mocks.*
import me.proton.android.calendar.mocks.CalendarMocks.getCalendarEntity
import me.proton.android.calendar.mocks.CalendarMocks.getCalendarSettingsEntity
import me.proton.android.calendar.mocks.CalendarMocks.getCalendarUserSettingsEntity
import me.proton.android.calendar.mocks.EventMocks.getEvent
import me.proton.android.calendar.mocks.EventMocks.getEventEntity
import me.proton.android.calendar.mocks.UserMocks.getUser
import me.proton.android.calendar.mocks.UserMocks.getUserSettingsEntity
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.user.domain.UserManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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

    /**
     *  EventViewModel.initialise Tests
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

            val eventViewModel = getInitialisedEventViewModel(
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
                initStartTime = null
            )
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
                initStartTime = null
            )

            // Test: Clone RRule from original event in DB if we are in edit mode
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value != null)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value?.frequency == Frequency.DAILY)

            assert(eventViewModel.eventLiveData.value?.id == singleEditEventId)

        }
    }

    /**
     *  EventViewModel.getSingleEditsInfo Tests
     */

    @Test
    fun getSingleEditsInfoTest() {
        runBlocking {

            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(hasAttendees = true)

            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf(
                getEvent(hasAttendees = true, isSingleEdit = true)
            )

            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            val singleEditsInfo = eventViewModel.getSingleEditsInfo(listOf(attendeeEmail))

            assert(singleEditsInfo?.singleEdits?.size == 1)
            assert(singleEditsInfo?.hasSingleEdit == true)
            assert(singleEditsInfo?.hasAnsweredSingleEdit == true)
            assert(singleEditsInfo?.hasFutureSingleEdit == true)
            assert(singleEditsInfo?.hasNonCancelledSingleEdit == true)
        }
    }
}
