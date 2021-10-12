package me.proton.android.calendar.presentation

import android.app.Application
import android.text.TextUtils
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.util.Frequency
import biweekly.util.Recurrence
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.formatSendPreferencesError
import me.proton.android.calendar.common.AndroidUtils.toInt
import me.proton.android.calendar.common.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.UserSettingsRepository
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.mocks.*
import me.proton.android.calendar.mocks.CalendarMocks.getCalendarEntity
import me.proton.android.calendar.mocks.CalendarMocks.getCalendarSettingsEntity
import me.proton.android.calendar.mocks.CalendarMocks.getCalendarUserSettingsEntity
import me.proton.android.calendar.mocks.EventMocks.getEvent
import me.proton.android.calendar.mocks.EventMocks.getEventEntity
import me.proton.android.calendar.mocks.UserMocks.getSendPreferences
import me.proton.android.calendar.mocks.UserMocks.getUser
import me.proton.android.calendar.mocks.UserMocks.getUserAddress
import me.proton.android.calendar.mocks.UserMocks.getUserSettingsEntity
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.toBoolean
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
    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()

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
        coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())

        coEvery { calendarsRepositoryMock.selectEventEntity(eventId) } returns getEventEntity()
    }

    /**
     * Utils private methods
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
            resourceProvider = resourceProviderMock,
            handleAlarmsUseCase = handleAlarmsUseCaseMock
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

    private fun provideDisplayDialog(selectedItem: Int = 0, selectPositive: Boolean = true): BaseDialogFragment.DisplayDialog {
        return object: BaseDialogFragment.DisplayDialog {
            override fun alertDialog(
                title: String,
                message: String,
                positiveButton: String,
                negativeButton: String,
                alertDialogListener: BaseDialogFragment.AlertDialogListener?
            ) {
                if (selectPositive) alertDialogListener?.onPositive()
            }

            override fun pickerDialog(
                title: String,
                items: Array<String>,
                defaultSelectedItem: Int,
                positiveButton: String,
                negativeButton: String,
                alertDialogListener: BaseDialogFragment.AlertDialogListener?
            ) {
                if (selectPositive) alertDialogListener?.onPositive(selectedItem)
            }
        }
    }

    /**
     * EventViewModel.initialise tests:
     *  initialiseCreateAllDayEventTest -> Initialise EventVM for creating an all day event
     *  initialiseCreatePartDayEventTest -> Initialise EventVM for creating a part day event
     *  initialiseViewEventDetailsTest -> Initialise EventVM for opening details of existing event
     *  initialiseEditEventTest -> Initialise EventVM for editing an event
     *  initialiseEditSingleEditTest -> Initialise EventVM for editing a single edit
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

            // Mock event
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent()

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

            // Mock event
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent()

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
     * EventViewModel.getSingleEditsInfo tests:
     *  getSingleEditsInfoTest -> Get single edits info for event with one declined future non cancelled single edit
     */

    @Test
    fun getSingleEditsInfoTest() {
        runBlocking {

            // Mock event with attendee (user as organizer)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isRecurring = true, isOrganizer = true)

            // Mock single edit with attendee (user as organizer)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf(
                getEvent(isOrganizer = true, isSingleEdit = true)
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

    /**
     * EventViewModel delete flow tests:
     *  deleteEventTest -> Single event
     *  deleteSingleOccurrenceRecurringEventTest -> Single occurrence recurring
     *  deleteRecurringEventOptionThisTest -> Recurring from first occurrence with option "This"
     *  deleteRecurringEventOptionThisAndFutureTest -> Recurring from second occurrence with option "This & future"
     *  deleteRecurringEventOptionAllTest -> Recurring from second occurrence with option "All"
     *  deleteDisabledCalendarRecurringEventTest -> Recurring event in disabled calendar
     *  deleteEventChangingAnswerTest -> While changing answer
     *  deleteEventAsAnOrganizerTest -> Event with attendees (with user as organizer)
     *  deleteEventDisabledCalendarAsAnOrganizerTest -> Event with attendees (with user as organizer) in a disabled calendar
     *  deleteRecurringEventAsAnOrganizerTest -> Recurring event with attendees (with user as organizer)
     *  deleteRecurringEventDisabledCalendarAsAnOrganizerTest -> Recurring event with attendees (with user as organizer) in a disabled calendar
     *  deleteEventAsAnOrganizerNetworkErrorTest -> Event with attendees (with user as organizer) with network error
     *  deleteEventAsAnOrganizerAllSendPreferencesErrorTest -> Event with attendees (with user as organizer) with all send preferences error
     *  deleteEventAsAnOrganizerSomeSendPreferencesErrorTest -> Event with attendees (with user as organizer) with some send preferences error
     */

    @Test
    fun deleteEventTest() {
        runBlocking {

            // Mock event
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent()

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.THIS_EVENT, 0) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteSingleOccurrenceRecurringEventTest() {
        runBlocking {

            // Mock event
            val event = getEvent(isRecurring = true)
            // Set recurrence count to 1
            event.iCalEvent.setRecurrenceRule(Recurrence.Builder(event.iCalEvent.recurrenceRule.value).count(1).build())
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.ALL_EVENTS, null) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteRecurringEventOptionThisTest() {
        runBlocking {

            // Mock event
            val event = getEvent(isRecurring = true)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Select option this
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 0)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.THIS_EVENT, occurrenceNumber) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // This and future is hidden when first occurrence is selected
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteRecurringEventOptionThisAndFutureTest() {
        runBlocking {

            // Mock event
            val event = getEvent(isRecurring = true)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 2
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Select option this and future
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 1)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.THIS_EVENT_AND_FUTURE, occurrenceNumber) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // This and future is hidden when first occurrence is selected
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteRecurringEventOptionAllTest() {
        runBlocking {

            // Mock event
            val event = getEvent(isRecurring = true)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 2
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Select option all
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 2)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.ALL_EVENTS, null) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // This and future is hidden when first occurrence is selected
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteDisabledCalendarRecurringEventTest() {
        runBlocking {

            // Mock recurring event with disabled calendar
            coEvery { transformEventUseCaseMock.execute(any()) } returns getEvent(isRecurring = true, hasDisabledCalendar = true)

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, EventEditDeleteOption.ALL_EVENTS, null) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteEventChangingAnswerTest() {
        runBlocking {

            // Mock event
            coEvery { transformEventUseCaseMock.execute(any()) } returns getEvent()

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Simulate changing answer state
            eventViewModel.attendeeAnswerState.value = Pair(ParticipationStatus.ACCEPTED, true)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Success snack
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            // Handle delete use case
            coVerify(exactly = 0) { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any()) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
        }
    }

    @Test
    fun deleteEventAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true)

            // Handle delete use case with emailSent to true
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(true)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                    sendPreferences = getSendPreferences()
                ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) } returns protonCalendarApplication.getString(R.string.snack_event_deleted_as_organizer)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(Pair(attendeeEmail, getSendPreferences())),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer)
            ))
        }
    }

    @Test
    fun deleteEventDisabledCalendarAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer) and disabled calendar
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true, hasDisabledCalendar = true)

            // Handle delete use case with emailSent to false (calendar disabled)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(false)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                    sendPreferences = getSendPreferences()
                ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer_disabled) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event_as_organizer_disabled)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = true
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteRecurringEventAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true, isRecurring = true)

            // Handle delete use case with emailSent to true
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(true)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                    sendPreferences = getSendPreferences()
                ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) } returns protonCalendarApplication.getString(R.string.snack_event_deleted_as_organizer)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(Pair(attendeeEmail, getSendPreferences())),
                timeFormat.toBoolean(),
                isPartOfChain = true,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer)
            ))
        }
    }

    @Test
    fun deleteRecurringEventDisabledCalendarAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer) and disabled calendar
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true, isRecurring = true, hasDisabledCalendar = true)

            // Handle delete use case with emailSent to false (disabled calendar)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(false)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                    sendPreferences = getSendPreferences()
                ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer_disabled) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_organizer_disabled)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(),
                timeFormat.toBoolean(),
                isPartOfChain = true,
                isCalendarDisabled = true
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteEventAsAnOrganizerNetworkErrorTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.NetworkError)
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_network_error) } returns protonCalendarApplication.getString(R.string.snack_network_error)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 0) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_network_error) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnack(
                resourceProviderMock.provideString(R.string.snack_network_error)
            ))
        }
    }

    @Test
    fun deleteEventAsAnOrganizerAllSendPreferencesErrorTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true)

            // Handle delete use case with emailSent to false (empty send preferences)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(false)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled)
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_template, attendeeEmail, resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled)) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_template, attendeeEmail, resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled))
            val emailWithError = resourceProvider.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled)
            )

            // Send preferences dialog
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_none_message, emailWithError) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_none_message, emailWithError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(),
                mapOf(),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 2) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_none_message, emailWithError) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_template, attendeeEmail, resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled)) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun deleteEventAsAnOrganizerSomeSendPreferencesErrorTest() {
        runBlocking {

            // Mock event with two attendees (user as the organizer)
            val event = getEvent(isOrganizer = true)
            val secondAttendee = Attendee(secondAttendeeName, secondAttendeeEmail)
            secondAttendee.participationStatus = ParticipationStatus.DECLINED
            event.iCalEvent.addAttendee(secondAttendee)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns event

            // Handle delete use case with emailSent to true
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(true)

            // Get canonical and send preferences for attendees
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail, secondAttendeeEmail)) } returns mapOf(
                Pair(attendeeEmail, attendeeEmail), Pair(secondAttendeeEmail, secondAttendeeEmail)
            )
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(
                Pair(attendeeEmail, attendeeEmail),
                Pair(secondAttendeeEmail, secondAttendeeEmail)
            )) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled),
                Pair(secondAttendeeEmail, ObtainSendPreferencesUseCase.Result.Success(sendPreferences = getSendPreferences()))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_template, attendeeEmail, resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled)) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_template, attendeeEmail, resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled))
            val emailWithError = resourceProvider.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled)
            )

            // Send preferences dialog
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_some_message, emailWithError) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_some_message, emailWithError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) } returns protonCalendarApplication.getString(R.string.snack_event_deleted_as_organizer)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(secondAttendee),
                mapOf(Pair(secondAttendeeEmail, getSendPreferences())),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 2) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_some_message, emailWithError) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_template, attendeeEmail, resourceProvider.provideString(R.string.event_send_prefs_error_address_disabled)) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer)
            ))
        }
    }

    /**
     * EventViewModel save flow tests:
     *  createAllDayEventTest -> Create an all day event
     */

    @Test
    fun createAllDayEventTest() {
        runBlocking {

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, 1) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdate(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_created) } returns protonCalendarApplication.getString(R.string.snack_event_created)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, null, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = getUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    recurrenceManuallyEdited = false,
                    isCreate = true
                )
            }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_created) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonthOnSpecificDay(
                resourceProvider.provideString(
                    R.string.snack_event_created
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * EventViewModel change answer flow tests
     *  changeAnswerExternalEventTest -> For external invite
     */

    @Test
    fun changeAnswerExternalEventTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isAttendee = true)

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(organizerEmail, organizerEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                    sendPreferences = getSendPreferences()
                ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, 1) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdate(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = getEventEntity()
                )
            )

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.ACCEPTED,
                sendPreferences = mapOf(Pair(organizerEmail, getSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = null,
                isProtonProtonInvite = false,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = eventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.ACCEPTED.toInt(),
                personalPartICalString = null,
                updateTime = any() // updateTime = Instant.now()
            ) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.ACCEPTED, false))
        }
    }
}
