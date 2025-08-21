package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.distinct
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.filterVisibleCalendars
import me.proton.core.domain.entity.UserId
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GetUiEventsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val eventDecryptor: EventDecryptor,
    private val calendarsRepository: CalendarsRepository,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val loadingStateUseCase: LoadingStateUseCase,
) : UseCase {

    @OptIn(FlowPreview::class)
    fun execute(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        onlyVisibleCalendars: Boolean = true
    ): Flow<CalendarsRepository.GetEventsResult<UiEvent>> {
        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)
        Timber.d("getUiEventsUseCase: $eventsWindow")

        val fromEpoch = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toEpoch = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return calendarsRepository.flowAllCalendars(userId.id)
            .map { if (onlyVisibleCalendars) it.filterVisibleCalendars() else it }
            .distinctUntilChanged()
            .debounceExceptFirst(1.seconds)
            .onEach { eventDecryptor.setCalendars(it) }
            .flatMapLatest { calendars ->
                val calendarIds = calendars.map { it.id }
                combine(
                    getUserInfoUseCase().debounceExceptFirst(1.seconds).distinctUntilChanged(),

                    // trivial case, only 1 row for each event, start + end times are well defined
                    database.eventOccurrencesDao().selectNonRecurringBetweenInclusive(
                        userId.id,
                        calendarIds,
                        fromEpoch.toEpochSecond(),
                        toEpoch.toEpochSecond()
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),

                    // we know first and last occurrence time, it can be selected like non-recurring above
                    database.eventOccurrencesDao().selectFiniteRecurring(
                        userId.id,
                        calendarIds,
                        fromEpoch.toEpochSecond(),
                        toEpoch.toEpochSecond()
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),

                    // we don't know when the last occurrence happens, so we have to select all events
                    // except for the ones that start after our window
                    database.eventOccurrencesDao().selectInfiniteRecurring(
                        userId.id,
                        calendarIds,
                        toEpoch.toEpochSecond()
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged()

                ) { userInfo, nonRecurring, finiteRecurring, infiniteRecurring ->
                    Timber.d("for $eventsWindow nonRecurring: ${nonRecurring.size}, finiteRecurring: ${finiteRecurring.size}, infiniteRecurring: ${infiniteRecurring.size}")
                    withContext(Dispatchers.Default) {
                        // potential optimization: we have rrule, we can generate occurrences quickly without decrypting the event
                        // to filter out even more events here, before decryption takes place

                        // potential optimizations, but debatable if we're not dealing with huge amount of infinitely recurring events:
                        //  - select from by windowStart and windowEnd, only the rows that overlap with eventsWindow (problem: false negatives if the window was never generated for those events)
                        //  - groupBy events before performing discarding below and early-return for each event that for sure happens or doesn't happen in the window (problem: higher memory usage)
                        val notOccurring = infiniteRecurring.filter { occurrence ->
                            val isSelectedWindowFullyInOccurrenceWindow = isFullyBetween(
                                fromEpoch.toEpochSecond() to toEpoch.toEpochSecond(),
                                occurrence.windowStartTime to occurrence.windowEndTime
                            )
                            val isEventNotHappeningInOccurrenceWindow = occurrence.startTime == null
                            // optimization: event is happening in the entire window selected from DB, but after the time we are searching for,
                            //  so we can only do this for events happening *after*, but not *outside* the searched window, because [occurrence.startTime]
                            //  is the first occurrence overlapping generated window, but we don't know if it's the only one
                            val isFirstEventOccurenceAfterSelectedWindow =
                                (occurrence.startTime ?: 0) > toEpoch.toEpochSecond()

                            // check if this event for sure does not occur in <from, to>
                            isSelectedWindowFullyInOccurrenceWindow && (isEventNotHappeningInOccurrenceWindow || isFirstEventOccurenceAfterSelectedWindow)
                        }

                        val filteredInfiniteRecurring = infiniteRecurring.filterNot { recurring ->
                            notOccurring.find {
                                recurring.userId == it.userId && recurring.calendarId == it.calendarId && recurring.eventId == it.eventId
                            } != null
                        }

                        // we are returning first out of potentially many EventOccurrence objects for infiniteRecurring events
                        //  it doesn't matter because in the next step we're not using metadata, we only need to know which events
                        //  to decrypt and expand
                        Pair(
                            userInfo,
                            Triple(
                                nonRecurring.distinct().asFlow(),
                                finiteRecurring.distinct().asFlow(),
                                filteredInfiniteRecurring.distinct().asFlow(),
                            )
                        )
                    }
                }
            }.transform { (userInfo, events) ->
                val (nonRecurring, finiteRecurring, infiniteRecurring) = events
                val result = withContext(Dispatchers.Default) {
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
                                isFreeUser = userInfo.hasSubscriptionForMail.not()
                            )
                    }

                    val transformedFiniteRecurring = finiteRecurring.mapNotNull { occurrenceEntity ->
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
                    }.flatMapConcat {
                        it.asFlow()
                    }

                    val transformedInfiniteRecurring = infiniteRecurring.mapNotNull { occurrenceEntity ->
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
                    }.flatMapConcat {
                        it.asFlow()
                    }
                    flowOf(transformedNonRecurring, transformedFiniteRecurring, transformedInfiniteRecurring).flattenConcat().toList()
                }
                Timber.d("getUiEventsUseCase emitting ${result.size} for $eventsWindow")
                emit(result.distinct())
            }.combine(loadingStateUseCase.invoke().map { it.inProgress() }
                .distinctUntilChanged()) { events, inProgress ->
                CalendarsRepository.GetEventsResult.Success(events, fullyLoaded = inProgress.not())
            }
    }

    /**
     * If [fromEpoch] -- [toEpoch] is fully between [windowStartTime] -- [windowEndTime]
     */
    private fun isFullyBetween(smallerWindow: Pair<Long, Long>, largerWindow: Pair<Long, Long>): Boolean {
        return smallerWindow.first >= largerWindow.first && smallerWindow.second <= largerWindow.second
    }
}
