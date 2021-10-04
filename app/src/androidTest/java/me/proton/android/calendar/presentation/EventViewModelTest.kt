package me.proton.android.calendar.presentation

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import biweekly.util.Frequency
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
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
import me.proton.android.calendar.mocks.UserMocks.getUserAddress
import me.proton.android.calendar.mocks.UserMocks.getUserSettingsEntity
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.user.domain.UserManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EventViewModelTest: KoinComponent {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var appDatabaseMock: AppDatabase
    private lateinit var protonCalendarApplication: Application

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

    private val resourceProvider: ResourceProvider by inject()
    private val resourceProviderMock: ResourceProvider = mockk()

    @Before
    fun beforeEach() {
        clearAllMocks()
        appDatabaseMock = mockk()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        protonCalendarApplication = application

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
            application = protonCalendarApplication,
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

    /**
     * EventViewModel delete flow tests
     */

    @Test
    fun deleteSingleEventTest() {
//        runBlocking {
//
//            coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())
//            coEvery { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.THIS_EVENT, 0) } returns UseCase.Result.Success<Unit>()
//
//            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
//            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event)
//            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
//            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)
//            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)
//
//            val occurrenceNumber = 0
//            val eventViewModel = getInitialisedEventViewModel(
//                editMode = false,
//                eventId = eventId,
//                occurrenceNumber = occurrenceNumber,
//                initStartDate = null,
//                initStartTime = null
//            )
//
//            val provideDisplayDialog = object : BaseDialogFragment.DisplayDialog {
//                override fun alertDialog(
//                    title: String,
//                    message: String,
//                    positiveButton: String,
//                    negativeButton: String,
//                    alertDialogListener: BaseDialogFragment.AlertDialogListener
//                ) {
//                    alertDialogListener.onPositive(this@runBlocking)
//                }
//            }
//
//            withContext(Dispatchers.Default) {
//                eventViewModel.handleDelete(provideDisplayDialog, occurrenceNumber)
//            }
//
//            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
//            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event) }
//            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
//            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }
//            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }
//
//            assert(eventViewModel.eventState.value == EventViewModel.EventState.Idle)
//            assert(eventViewModel.eventSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
//                resourceProviderMock.provideString(R.string.snack_event_deleted)
//            ))
//        }
    }

    @Test
    fun deleteDisabledCalendarRecurringEventTest() {
//        runBlocking {
//
//            coEvery { transformEventUseCaseMock.execute(any()) } returns getEvent(isRecurring = true, hasDisabledCalendar = true)
//
//            coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())
//            coEvery { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.ALL_EVENTS, null) } returns UseCase.Result.Success<Unit>()
//
//            val occurrenceNumber = 1
//            val eventViewModel = getInitialisedEventViewModel(
//                editMode = false,
//                eventId = eventId,
//                occurrenceNumber = occurrenceNumber,
//                initStartDate = null,
//                initStartTime = null
//            )
//
//            // Called on delete click
//            eventViewModel.handleDelete(occurrenceNumber)
//
//            assert(eventViewModel.eventState.value == EventViewModel.EventState.Processing.Deleting)
//            assert(eventViewModel.eventDialogState.value == EventViewModel.EventDialogState.Delete.DisabledCalendarRecurring)
//
//            // Called on confirmation dialog click
//            eventViewModel.handleDeleteDisabledCalendarRecurring()
//
//            assert(eventViewModel.eventState.value == EventViewModel.EventState.Idle)
//            assert(eventViewModel.eventSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
//                resourceProviderMock.provideString(R.string.snack_event_deleted)
//            ))
//        }
    }
}
