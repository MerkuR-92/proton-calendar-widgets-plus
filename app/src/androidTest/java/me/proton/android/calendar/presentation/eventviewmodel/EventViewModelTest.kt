package me.proton.android.calendar.presentation.eventviewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.util.Frequency
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.mocks.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
open class EventViewModelTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * EventViewModel.initialise tests
     */

    /**
     * Initialise EventVM for creating an all day event
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

    /**
     * Initialise EventVM for creating a part day event
     */
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

            assert(eventViewModel.eventLiveData.value?.getStart(defaultTimezone)?.toLocalDate() == startDate)
            assert(eventViewModel.eventLiveData.value?.getStart(defaultTimezone)?.toLocalTime() == startTime)
            assert(eventViewModel.eventLiveData.value?.getEnd(defaultTimezone)?.toLocalTime() == endTime)

            assert(eventViewModel.eventLiveData.value?.isAllDay() == false)

        }
    }

    /**
     * Initialise EventVM for opening details of existing event
     */
    @Test
    fun initialiseViewEventDetailsTest() {
        runBlocking {

            // Mock event
            coEvery { transformEventUseCaseMock.execute(EventMocks.provideEventEntity()) } returns EventMocks.provideEvent()

            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )
        }
    }

    /**
     * Initialise EventVM for editing an event
     */
    @Test
    fun initialiseEditEventTest() {
        runBlocking {

            // Mock event
            coEvery { transformEventUseCaseMock.execute(EventMocks.provideEventEntity()) } returns EventMocks.provideEvent()

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )
        }
    }

    /**
     * Initialise EventVM for editing an event with no default calendar
     */
    @Test
    fun initialiseEditEventNoDefaultCalendarTest() {
        runBlocking {

            // Mock event
            coEvery { transformEventUseCaseMock.execute(EventMocks.provideEventEntity()) } returns EventMocks.provideEvent()

            // No default calendar
            coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns null

            // Fallback calendar
            val fallbackCalendarId = "fallbackCalendarId"
            coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendarEntity(id = fallbackCalendarId))

            // Fallback calendar settings
            coEvery { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) } returns CalendarMocks.provideCalendarSettingsEntity(id = fallbackCalendarId)

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectActiveUserCalendars(any()) }

            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) }

            assert(eventViewModel.calendarSettings.calendarId == fallbackCalendarId)
        }
    }

    /**
     * Initialise EventVM for editing an event with disabled default calendar
     */
    @Test
    fun initialiseEditEventDisabledDefaultCalendarTest() {
        runBlocking {

            // Mock event
            coEvery { transformEventUseCaseMock.execute(EventMocks.provideEventEntity()) } returns EventMocks.provideEvent()

            // No default calendar
            val calendarEntity = CalendarMocks.provideCalendarEntity(isDisabled = true)
            coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns calendarEntity

            // Fallback calendar
            val fallbackCalendarId = "fallbackCalendarId"
            coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendarEntity(id = fallbackCalendarId))

            // Fallback calendar settings
            coEvery { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) } returns CalendarMocks.provideCalendarSettingsEntity(id = fallbackCalendarId)

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectActiveUserCalendars(any()) }

            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) }

            assert(eventViewModel.calendarSettings.calendarId == fallbackCalendarId)
        }
    }

    /**
     * Initialise EventVM for editing a single edit
     */
    @Test
    fun initialiseEditSingleEditTest() {
        runBlocking {

            // Mock single edit event
            val singleEditEventEntity = EventMocks.provideEventEntity(isSingleEdit = true)
            val singleEditEvent = EventMocks.provideEvent(isSingleEdit = true)
            coEvery { calendarsRepositoryMock.selectEventEntity(singleEditEventId) } returns singleEditEventEntity
            coEvery { transformEventUseCaseMock.execute(singleEditEventEntity) } returns singleEditEvent

            // Mock root event
            val rootEventEntity = EventMocks.provideEventEntity()
            val rootEvent = EventMocks.provideEvent(isRecurring = true)
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
     * EventViewModel.getSingleEditsInfo tests
     */

    /**
     * Get single edits info for event with one declined future non cancelled single edit
     */
    @Test
    fun getSingleEditsInfoTest() {
        runBlocking {

            // Mock event with attendee (user as organizer)
            coEvery { transformEventUseCaseMock.execute(EventMocks.provideEventEntity()) } returns EventMocks.provideEvent(isRecurring = true, isOrganizer = true)

            // Mock single edit with attendee (user as organizer)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf(
                EventMocks.provideEvent(isOrganizer = true, isSingleEdit = true)
            )

            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = 1,
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
