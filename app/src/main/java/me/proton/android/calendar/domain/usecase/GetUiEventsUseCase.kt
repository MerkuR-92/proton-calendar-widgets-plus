package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.distinct
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.filterVisibleCalendars
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GetUiEventsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val eventDecryptor: EventDecryptor,
    private val calendarsRepository: CalendarsRepository,
    private val getUserInfoUseCase: GetUserInfoUseCase
) : UseCase {

    fun execute(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        onlyVisibleCalendars: Boolean = true
    ): Flow<CalendarsRepository.GetEventsResult<UiEvent>> {

        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)

        val fromEpoch = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toEpoch = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return calendarsRepository.flowAllCalendars(userId.id)
            .map { if (onlyVisibleCalendars) it.filterVisibleCalendars() else it }
            .distinctUntilChanged()
            .debounceExceptFirst(1.seconds)
            .flatMapLatest { calendars ->

                combine(
                    getUserInfoUseCase(),

                    // trivial case, only 1 row for each event, start + end times are well defined
                    database.eventOccurrencesDao().selectNonRecurringBetweenInclusive(
                        userId.id,
                        calendars.map { it.id },
                        fromEpoch.toEpochSecond(),
                        toEpoch.toEpochSecond()
                    ),

                    // we know first and last occurrence time, it can be selected like non-recurring above
                    database.eventOccurrencesDao().selectFiniteRecurring(
                        userId.id,
                        calendars.map { it.id },
                        fromEpoch.toEpochSecond(),
                        toEpoch.toEpochSecond()
                    ),

                    // we don't know when the last occurrence happens, so we have to select all events
                    // except for the ones that start after our window
                    database.eventOccurrencesDao().selectInfiniteRecurring(
                        userId.id,
                        calendars.map { it.id },
                        toEpoch.toEpochSecond()
                    )

                ) { userInfo, nonRecurring, finiteRecurring, infiniteRecurring ->

                    // potential optimization: we have rrule, we can generate occurrences quickly without decrypting the event
                    // to filter out even more events here, before decryption takes place

                    // potential optimizations, but debatable if we're not dealing with huge amount of infinitely recurring events:
                    //  - select from by windowStart and windowEnd, only the rows that overlap with eventsWindow (problem: false negatives if the window was never generated for those events)
                    //  - groupBy events before performing discarding below and early-return for each event that for sure happens or doesn't happen in the window (problem: higher memory usage)

                    val notOccurring = infiniteRecurring.filter { occurrence ->

                        val isSelectedWindowFullyInOccurrenceWindow = isFullyBetween(fromEpoch.toEpochSecond() to toEpoch.toEpochSecond(), occurrence.windowStartTime to occurrence.windowEndTime)
                        val isEventNotHappeningInOccurrenceWindow = occurrence.startTime == null
                        // optimization: event is happening in the entire window selected from DB, but after the time we are searching for,
                        //  so we can only do this for events happening *after*, but not *outside* the searched window, because [occurrence.startTime]
                        //  is the first occurrence overlapping generated window, but we don't know if it's the only one
                        val isFirstEventOccurenceAfterSelectedWindow = (occurrence.startTime ?: 0) > toEpoch.toEpochSecond()

                        // check if this event for sure does not occur in <from, to>
                        isSelectedWindowFullyInOccurrenceWindow && (isEventNotHappeningInOccurrenceWindow || isFirstEventOccurenceAfterSelectedWindow)
                    }.distinct()

                    val filteredInfiniteRecurring = infiniteRecurring.filterNot { recurring -> notOccurring.find {
                        recurring.userId == it.userId && recurring.calendarId == it.calendarId && recurring.eventId == it.eventId } != null
                    }

                    // we are returning first out of potentially many EventOccurrence objects for infiniteRecurring events
                    //  it doesn't matter because in the next step we're not using metadata, we only need to know which events
                    //  to decrypt and expand
                    Triple(
                        userInfo,
                        nonRecurring.distinct(),
                        (finiteRecurring.distinct() + filteredInfiniteRecurring.distinct())
                    )
                }

            }.map { (userInfo, nonRecurring, recurring) ->

                val transformedNonRecurring = nonRecurring.mapNotNull { occurrenceEntity ->

                    val transformedEvent =
                        eventDecryptor.getFromCache(
                            occurrenceEntity.eventId,
                            occurrenceEntity.calendarId,
                            occurrenceEntity.modifyTime
                        ) ?: database.eventsDao().selectById(occurrenceEntity.eventId)
                            ?.let { eventDecryptor.decrypt(it) }

                    // hide events that we can't decrypt
                    transformedEvent?.takeIf { it.decryptionStatus != Event.DecryptionStatus.Failure.NoAddressKey }
                        ?.toUiEvent(
                            userEmails = userInfo.emails,
                            timeZoneId = timeZoneId,
                            isFreeUser = userInfo.hasSubscriptionForMail
                        )

                }

                val transformedRecurring = recurring.mapNotNull { occurrenceEntity ->

                    val transformedEvent =
                        eventDecryptor.getFromCache(
                            occurrenceEntity.eventId,
                            occurrenceEntity.calendarId,
                            occurrenceEntity.modifyTime
                        ) ?: database.eventsDao().selectById(occurrenceEntity.eventId)
                            ?.let { eventDecryptor.decrypt(it) }

                    // hide events that we can't decrypt
                    transformedEvent?.takeIf { it.decryptionStatus != Event.DecryptionStatus.Failure.NoAddressKey }
                        ?.let {

                            val eventsSharingUid = calendarsRepository.selectEventsByUid(occurrenceEntity.eventUid)

                            calendarsRepository.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                                transformedEvent,
                                eventsSharingUid,
                                eventsWindow.fromDate,
                                eventsWindow.toDate,
                                eventsWindow.timeZoneId,
                                userInfo.emails,
                                userInfo.hasSubscriptionForMail.not()
                            )
                        }

                }.flatten()

                CalendarsRepository.GetEventsResult.Success(transformedNonRecurring + transformedRecurring)
            }

    }

    /**
     * If [fromEpoch] -- [toEpoch] is fully between [windowStartTime] -- [windowEndTime]
     */
    private fun isFullyBetween(smallerWindow: Pair<Long, Long>, largerWindow: Pair<Long, Long>): Boolean {
        return smallerWindow.first >= largerWindow.first && smallerWindow.second <= largerWindow.second
    }

}
