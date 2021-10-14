package me.proton.android.calendar.presentation.eventviewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.AndroidUtils.toInt
import me.proton.android.calendar.common.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.mocks.*
import me.proton.core.util.kotlin.toBoolean
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent

/**
 * EventViewModel change answer flow tests
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EventViewModelChangeAnswerTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * For external invite
     */
    @Test
    fun changeAnswerExternalEventTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { transformEventUseCaseMock.execute(EventMocks.getEventEntity()) } returns EventMocks.getEvent(isAttendee = true)

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.getUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                    sendPreferences = UserMocks.getSendPreferences()
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
                    event = EventMocks.getEventEntity()
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
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.getSendPreferences())),
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
