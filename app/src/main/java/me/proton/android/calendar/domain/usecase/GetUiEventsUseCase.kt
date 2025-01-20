package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import me.proton.android.calendar.data.db.AppDatabase
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

        return calendarsRepository.flowUserCalendars(userId.id)
            .map { if (onlyVisibleCalendars) it.filterVisibleCalendars() else it }
            .distinctUntilChanged()
            .debounceExceptFirst(1.seconds)
            .flatMapLatest { calendars ->

                combine(
                    getUserInfoUseCase(),

                    database.eventOccurrencesDao().selectNonRecurringBetweenInclusive(
                        userId.id,
                        calendars.map { it.id },
                        fromEpoch.toEpochSecond(),
                        toEpoch.toEpochSecond()
                    ),

                    database.eventOccurrencesDao().selectFiniteRecurring(
                        userId.id,
                        calendars.map { it.id },
                        fromEpoch.toEpochSecond(),
                        toEpoch.toEpochSecond()
                    ),

                    database.eventOccurrencesDao().selectInfiniteRecurring(
                        userId.id,
                        calendars.map { it.id }
                    )


                ) { userInfo, nonRecurring, finiteRecurring, infiniteRecurring ->

                    // potential optimization: we have rrule, we can generate occurrences quickly without decrypting the event
                    // to filter out even more events here, before decryption takes place

                    /* TODO we should use windows for selection of these rows, right now we're selecting all of them */

                    Triple(
                        userInfo,
                        nonRecurring,
                        (finiteRecurring + infiniteRecurring)
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

}
