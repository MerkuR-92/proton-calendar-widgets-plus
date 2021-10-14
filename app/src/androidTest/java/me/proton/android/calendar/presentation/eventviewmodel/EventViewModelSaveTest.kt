package me.proton.android.calendar.presentation.eventviewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.mocks.*
import me.proton.android.calendar.presentation.calendar.EventViewModel
import me.proton.core.util.kotlin.toBoolean
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent

/**
 * EventViewModel save flow tests
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EventViewModelSaveTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * Create an all day event
     */
    @Test
    fun createAllDayEventTest() {
        runBlocking {

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.getUserAddress())

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
            coEvery { resourceProviderMock.provideString(R.string.snack_event_created) } returns protonCalendarApplication.getString(
                R.string.snack_event_created)

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
                    userSettings = UserMocks.getUserSettingsEntity(),
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
                resourceProviderMock.provideString(
                    R.string.snack_event_created
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

}
