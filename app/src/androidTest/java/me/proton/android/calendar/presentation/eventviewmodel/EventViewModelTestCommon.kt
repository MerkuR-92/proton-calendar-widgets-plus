package me.proton.android.calendar.presentation.eventviewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import kotlinx.serialization.json.Json
import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.UserSettingsRepository
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.mocks.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.user.domain.UserManager
import org.junit.Before
import org.junit.Rule
import org.koin.core.KoinComponent

open class EventViewModelTestCommon: KoinComponent {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    lateinit var appDatabaseMock: AppDatabase
    lateinit var protonCalendarApplication: Application

    val calendarsRepositoryMock: CalendarsRepository = mockk()
    val userSettingsRepositoryMock: UserSettingsRepository = mockk()

    val userManagerMock: UserManager = mockk()

    val transformEventUseCaseMock: TransformEventUseCase = mockk()
    val updateParticipationStatusUseCaseMock: UpdateParticipationStatusUseCase = mockk()
    val sendEmailUseCaseMock: SendEmailUseCase = mockk()
    val getCanonicalEmailsUseCaseMock: GetCanonicalEmailsUseCase = mockk()
    val obtainSendPreferencesUseCaseMock: ObtainSendPreferencesUseCase = mockk()
    val handleSaveUseCaseMock: HandleSaveUseCase = mockk()
    val handleDeleteUseCaseMock: HandleDeleteUseCase = mockk()
    val updateCalendarUseCaseMock: UpdateCalendarUseCase = mockk()
    val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    val calendarWidgetRefresherMock: CalendarWidgetRefresher = mockk()

    private val testsLogger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }

    val resourceProviderMock: ResourceProvider = mockk()

    @Before
    fun beforeEach() {
        clearAllMocks()
        appDatabaseMock = mockk()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        protonCalendarApplication = application

        coEvery { calendarsRepositoryMock.getDefaultCalendarId(userId.id) } returns calendarId
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.getCalendarEntity()
        // TODO Test fallback to first active user calendar when default calendar is null
        coEvery { calendarsRepositoryMock.getActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.getCalendarEntity())
        coEvery { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) } returns CalendarMocks.getCalendarUserSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.getCalendarSettingsEntity()
        coEvery { userSettingsRepositoryMock.selectUserSettings(userId.id) } returns UserMocks.getUserSettingsEntity()
        coEvery { userManagerMock.getUser(userId) } returns UserMocks.getUser()
        coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.getUserAddress())

        coEvery { calendarsRepositoryMock.selectEventEntity(eventId) } returns EventMocks.getEventEntity()

        coEvery { calendarWidgetRefresherMock.refresh() } just Runs
    }

    /**
     * Utils private methods
     */

    fun getEventViewModel(): EventViewModel {
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
            widgetRefresher = calendarWidgetRefresherMock,
            handleAlarmsUseCase = handleAlarmsUseCaseMock
        )
    }

    suspend fun getInitialisedEventViewModel(
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
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(any()) }
        }

        coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(any()) }
        coVerify(exactly = 1) { userSettingsRepositoryMock.selectUserSettings(any()) }
        coVerify(exactly = 1) { userManagerMock.getUser(any()) }

        if (eventId != null && eventId == singleEditEventId) {
            // If we edit existing single edit event
            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            if (editMode) {
                coVerify(exactly = 1) { calendarsRepositoryMock.selectRootEventEntity(any()) }
                coVerify(exactly = 2) { transformEventUseCaseMock.execute(any()) }
            } else {
                coVerify(exactly = 1) { transformEventUseCaseMock.execute(any()) }
            }
        } else if (eventId != null) {
            // If we edit existing event
            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { transformEventUseCaseMock.execute(any()) }
        }

        return eventViewModel
    }

    fun provideDisplayDialog(selectedItem: Int = 0, selectPositive: Boolean = true): BaseDialogFragment.DisplayDialog {
        return object: BaseDialogFragment.DisplayDialog {
            override fun alertDialog(
                title: String,
                message: String,
                positiveButton: String,
                negativeButton: String,
                dialogListener: BaseDialogFragment.DialogListener?
            ) {
                if (selectPositive) dialogListener?.onPositive()
            }

            override fun pickerDialog(
                title: String,
                items: Array<String>,
                defaultSelectedItem: Int,
                positiveButton: String,
                negativeButton: String,
                dialogListener: BaseDialogFragment.DialogListener?
            ) {
                if (selectPositive) dialogListener?.onPositive(selectedItem)
            }
        }
    }

}
