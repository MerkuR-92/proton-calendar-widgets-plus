package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.overlaps
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueWorkHelper
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import net.sqlcipher.database.SQLiteException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * This needs to be called when EventEntity is already saved in DB, otherwise foreign key will fail!
 */
class UpdateEventOccurrencesUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase
) : UseCase {

    private val generatedWindowsCount = 24L

    suspend fun execute(
        userId: String,
        eventEntityMetadata: EventEntityMetadata
    ) {

        // early return if there is no need to update the Occurrences
        if (database.inTransaction {
            database.eventOccurrencesDao().hasOccurrenceWithEqualOrHigherModifyTime(
                userId,
                eventEntityMetadata.calendarId,
                eventEntityMetadata.id,
                eventEntityMetadata.modifyTime
            )
        }) return

        val eventOccurrenceEntities = if (eventEntityMetadata.rRule == null) { // normal event or single edit

            listOf(
                EventOccurrenceEntity(
                    userId = userId,
                    calendarId = eventEntityMetadata.calendarId,
                    eventId = eventEntityMetadata.id,
                    eventUid = eventEntityMetadata.uid,
                    fullDay = eventEntityMetadata.fullDay,
                    startTime = eventEntityMetadata.startTime,
                    endTime = eventEntityMetadata.endTime,
                    windowStartTime = eventEntityMetadata.startTime,
                    windowEndTime = eventEntityMetadata.endTime,
                    modifyTime = eventEntityMetadata.modifyTime,
                    firstOccurrenceStartTime = eventEntityMetadata.startTime
                )
            )

        } else { // recurring event

            val startUtc = LocalDateTime.ofEpochSecond(eventEntityMetadata.startTime, 0, ZoneOffset.UTC)
            val endUtc = LocalDateTime.ofEpochSecond(eventEntityMetadata.endTime, 0, ZoneOffset.UTC)

            val dummyEventForOccurrences = generateDummyEventForOccurrences(startUtc, endUtc, eventEntityMetadata)

            val startOfEventsFirstMonth = startUtc.withDayOfMonth(1).toLocalDate()

            val occurrences = dummyEventForOccurrences.generateOccurrencesUntil(
                startOfEventsFirstMonth.plusMonths(generatedWindowsCount + 1).minusDays(1),
                "UTC"
            )

            val firstOccurrenceAfterWindows = dummyEventForOccurrences.generateFirstOccurrenceSince(
                startOfEventsFirstMonth.plusMonths(generatedWindowsCount + 1).atStartOfDay(ZoneId.of("UTC"))
            )

            val firstOccurrenceStartTime = occurrences?.firstOrNull()?.startDateTime?.toEpochSecond() ?: run {
                logger.e("UpdateEventOccurrencesUseCase.execute() failed to get first occurrence, RRULE: ${eventEntityMetadata.rRule}")
                return
            }

            val lastOccurrenceEndTime = if (firstOccurrenceAfterWindows == null) {
                occurrences?.lastOrNull()?.endDateTime?.toEpochSecond()
            } else null

            val eventOccurrenceEntities = mutableListOf<EventOccurrenceEntity>()

            for (i in 0..generatedWindowsCount) {

                val windowStart = startOfEventsFirstMonth.plusMonths(i).atStartOfDay(ZoneId.of("UTC"))
                val windowEnd = windowStart.toLocalDate().plusMonths(1).atStartOfDay(ZoneId.of("UTC")).minusSeconds(1)

                // first generated occurrence should be happening in the first window for sure

                // this can be one of many occurrences in a given window but we only care about hit inside window, not particular start/end-times
                val occurrenceInThisWindow = occurrences.find {
                    Pair(it.startDateTime, it.endDateTime).overlaps(Pair(windowStart, windowEnd))
                }

                // don't save unnecessary occurrences if their window starts after last occurrence
                val isWindowAfterLastOccurrence =
                    lastOccurrenceEndTime != null && windowStart.toEpochSecond() > lastOccurrenceEndTime

                if (occurrenceInThisWindow != null || isWindowAfterLastOccurrence.not()) {

                    eventOccurrenceEntities.add(

                        // if there is no occurrence in this window, startTime and endTime will be null -- this is a useful information for lookup
                        EventOccurrenceEntity(
                            userId = userId,
                            calendarId = eventEntityMetadata.calendarId,
                            eventId = eventEntityMetadata.id,
                            eventUid = eventEntityMetadata.uid,
                            fullDay = eventEntityMetadata.fullDay,
                            startTime = occurrenceInThisWindow?.startDateTime?.toEpochSecond(),
                            endTime = occurrenceInThisWindow?.endDateTime?.toEpochSecond(),
                            rRule = eventEntityMetadata.rRule,
                            windowStartTime = windowStart.toEpochSecond(),
                            windowEndTime = windowEnd.toEpochSecond(),
                            firstOccurrenceStartTime = firstOccurrenceStartTime,
                            lastOccurrenceEndTime = lastOccurrenceEndTime,
                            modifyTime = eventEntityMetadata.modifyTime
                        )
                    )
                }

            }

            eventOccurrenceEntities
        }

        kotlin.runCatching {
            database.inTransaction {
                database.eventOccurrencesDao()
                    .deleteAllForEvent(userId, eventEntityMetadata.calendarId, eventEntityMetadata.id)
                database.eventOccurrencesDao().insert(*eventOccurrenceEntities.toTypedArray())
            }
        }.onFailure {
            logger.e("UpdateEventOccurrencesUseCase failed for cal=${eventEntityMetadata.calendarId}", it)
        }

    }

    private fun generateDummyEventForOccurrences(
        startUtc: LocalDateTime,
        endUtc: LocalDateTime,
        eventEntityMetadata: EventEntityMetadata
    ): Event {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        val iCalString = """
                    BEGIN:VCALENDAR
                    PRODID:0
                    VERSION:2.0
                    BEGIN:VEVENT
                    DTSTART;TZID=UTC:${startUtc.format(formatter)}
                    DTEND;TZID=UTC:${endUtc.format(formatter)}
                    RRULE:${eventEntityMetadata.rRule}
                    END:VEVENT
                    END:VCALENDAR
                    """.trimIndent()

        return Event.dummyFrom(ICalUtilsImpl.parseICalString(iCalString)!!)!!
    }

}
