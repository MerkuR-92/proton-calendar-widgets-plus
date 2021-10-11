package me.proton.android.calendar.presentation

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.util.Frequency
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
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

            // Mock event with attendee (user as organizer)
            coEvery { transformEventUseCaseMock.execute(getEventEntity()) } returns getEvent(isOrganizer = true)

            // Mock single edit with attendee (user as organizer)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf(
                getEvent(isOrganizer = true, isSingleEdit = true)
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
    fun deleteEventTest() {
        runBlocking {

            // Get current user address
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())

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
    fun deleteDisabledCalendarRecurringEventTest() {
        runBlocking {

            // Mock recurring event with disabled calendar
            coEvery { transformEventUseCaseMock.execute(any()) } returns getEvent(isRecurring = true, hasDisabledCalendar = true)

            // Get current user address
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(getUserAddress())

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

    /**
     * EventViewModel save flow tests
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
