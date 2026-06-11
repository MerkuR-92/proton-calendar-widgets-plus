package me.proton.android.calendar.domain.indicators

import biweekly.property.Status
import biweekly.util.Frequency
import biweekly.util.Recurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.MAX_CALENDAR_INDICATORS
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.filterVisibleCalendars
import me.proton.android.calendar.domain.usecase.GetUserInfoUseCase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes mini-calendar indicator dots/skeleton from server-plaintext data
 * (no decryption for near-immediate UI feedback)
 */
@Singleton
class MetadataIndicatorsCalculator @Inject constructor(
    private val database: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val logger: Logger,
) {

    // one occurrence of an event resolved from metadata (no decryption)
    private data class SkeletonOccurrence(
        val eventId: String,
        val calendarId: String,
        val uid: String,
        val color: String,
        val fullDay: Boolean,
        val startInstant: Instant,
        val endInstant: Instant,
        val occurrenceNumber: Int,
    )

    // mirrors CalendarViewModel.calendarIndicators but skips decryption
    suspend fun compute(
        userId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): Map<LocalDate, List<String>> = withContext(Dispatchers.Default) {
        val occurrences = generateOccurrences(userId, fromDate, toDate, timeZoneId)
        val indicators = mutableMapOf<LocalDate, MutableList<String>>()
        for (occ in occurrences) {
            addSpannedDays(
                startInstant = occ.startInstant,
                endInstant = occ.endInstant,
                isAllDay = occ.fullDay,
                timeZoneId = timeZoneId,
                color = occ.color,
                indicators = indicators,
            )
        }
        indicators.mapValues { it.value.toList().sorted().take(MAX_CALENDAR_INDICATORS) }
    }

    // mirrors GetUiEventsUseCase.execute but skips decryption
    suspend fun computeSkeletonUiEvents(
        userId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): List<UiEvent> = withContext(Dispatchers.Default) {
        val zone = ZoneId.of(timeZoneId)
        generateOccurrences(userId, fromDate, toDate, timeZoneId).map { occ ->
            UiEvent(
                id = occ.eventId,
                calendarId = occ.calendarId,
                uid = occ.uid,
                summary = null,
                location = null,
                description = null,
                dateStart = occ.startInstant.atZone(zone),
                dateEnd = occ.endInstant.atZone(zone),
                isAllDay = occ.fullDay,
                occurrenceNumber = occ.occurrenceNumber,
                displayColor = occ.color,
                decryptionStatus = Event.DecryptionStatus.Success,
                participationStatus = null,
                status = Status.confirmed(),
                isSkeleton = true,
            )
        }.distinct() // mirror GetUiEventsUseCase's .distinct() so skeleton rows align with the real ones
    }

     // mirrors GetUiEventsUseCase for row fetch, filter and expansion
    private suspend fun generateOccurrences(
        userId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): List<SkeletonOccurrence> {
        val zone = ZoneId.of(timeZoneId)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toEndInstant = toDate.plusDays(1).atStartOfDay(zone).toInstant()
        val fromEpoch = fromInstant.epochSecond
        val toEpoch = toEndInstant.epochSecond

        val visibleCalendars = calendarsRepository.selectAllCalendars(userId).filterVisibleCalendars()
        val colorByCalendarId = visibleCalendars.associate { it.id to it.color }
        val visibleCalendarIds = colorByCalendarId.keys.toList()

        if (visibleCalendarIds.isEmpty()) return emptyList()

        val nonRecurring = database.eventOccurrencesDao()
            .selectNonRecurringBetweenInclusive(userId, visibleCalendarIds, fromEpoch, toEpoch)
            .firstOrNull().orEmpty()
        val finiteRecurring = database.eventOccurrencesDao()
            .selectFiniteRecurring(userId, visibleCalendarIds, fromEpoch, toEpoch)
            .firstOrNull().orEmpty()
        val infiniteRecurringRaw = database.eventOccurrencesDao()
            .selectInfiniteRecurring(userId, visibleCalendarIds, toEpoch)
            .firstOrNull().orEmpty()

        val notOccurring = infiniteRecurringRaw.filter { occ ->
            val isSelectedWindowFullyInOccurrenceWindow =
                fromEpoch >= occ.windowStartTime && toEpoch <= occ.windowEndTime
            val isEventNotHappeningInOccurrenceWindow = occ.startTime == null
            val isFirstEventOccurrenceAfterSelectedWindow = (occ.startTime ?: 0) > toEpoch
            isSelectedWindowFullyInOccurrenceWindow &&
                (isEventNotHappeningInOccurrenceWindow || isFirstEventOccurrenceAfterSelectedWindow)
        }
        val infiniteRecurring = infiniteRecurringRaw.filterNot { recurring ->
            notOccurring.any {
                recurring.userId == it.userId &&
                    recurring.calendarId == it.calendarId &&
                    recurring.eventId == it.eventId
            }
        }

        val allRows = nonRecurring + finiteRecurring + infiniteRecurring
        val byUid = allRows.groupBy { it.eventUid }

        // per-event color overrides
        val isFreeUser = !(getUserInfoUseCase().firstOrNull()?.hasSubscriptionForMail ?: false)
        val eventColorById: Map<String, String?> = if (isFreeUser) {
            emptyMap()
        } else {
            val eventIds = allRows.map { it.eventId }.distinct()
            if (eventIds.isEmpty()) emptyMap()
            else database.eventsDao().selectEventColorsById(eventIds)
                .associate { it.id to it.color }
        }

        fun resolveColor(eventId: String, calendarId: String): String? {
            val calColor = colorByCalendarId[calendarId] ?: return null
            if (isFreeUser) return calColor
            return eventColorById[eventId] ?: calColor
        }

        val result = mutableListOf<SkeletonOccurrence>()

        for ((_, group) in byUid) {
            val parents = group.filter { it.recurrenceID == null }
            val overrides = group.filter { it.recurrenceID != null }
            val overrideRecurrenceInstants = overrides
                .mapNotNull { it.recurrenceID }
                .map { Instant.ofEpochSecond(it) }
                .toSet()

            val parentByEvent = parents.groupBy { it.eventId to it.calendarId }
            for ((_, rows) in parentByEvent) {
                val rep = rows.firstOrNull() ?: continue
                val color = resolveColor(rep.eventId, rep.calendarId) ?: continue

                if (rep.rRule == null) {
                    // non-recurring parent: use the row's start/end directly
                    val startSec = rep.startTime ?: continue
                    val endSec = rep.endTime ?: continue
                    result.add(
                        SkeletonOccurrence(
                            eventId = rep.eventId,
                            calendarId = rep.calendarId,
                            uid = rep.eventUid,
                            color = color,
                            fullDay = rep.fullDay != 0,
                            startInstant = Instant.ofEpochSecond(startSec),
                            endInstant = Instant.ofEpochSecond(endSec),
                            occurrenceNumber = 0,
                        )
                    )
                } else {
                    // recurring parent: expand RRULE in the event's ORIGINAL timezone, filter by EXDATE and override RECURRENCE-IDs
                    val rrule = rep.rRule
                    val expansionTz = rep.startTimeZone.ifBlank { timeZoneId }

                    val sample = rows.firstOrNull { it.startTime != null && it.endTime != null }
                        ?: continue
                    val durationSec = sample.endTime!! - sample.startTime!!

                    val dtStart = Instant.ofEpochSecond(rep.firstOccurrenceStartTime)
                    val dtEnd = Instant.ofEpochSecond(rep.firstOccurrenceStartTime + durationSec)

                    val exDateInstants = rep.exDates
                        .map { Instant.ofEpochSecond(it) }
                        .toSet()

                    val occurrences = expandRecurring(
                        rrule = rrule,
                        dtStartInstant = dtStart,
                        dtEndInstant = dtEnd,
                        fullDay = rep.fullDay != 0,
                        expansionTimeZoneId = expansionTz,
                        // arithmetic generators / generateOccurrencesUntil localize to displayTz, which is what we want
                        formatTimeZoneId = timeZoneId,
                        fromDate = fromDate,
                        toDate = toDate,
                    ) ?: continue

                    for (occ in occurrences) {
                        val sInst = occ.start
                        if (sInst in exDateInstants) continue
                        if (sInst in overrideRecurrenceInstants) continue
                        val eInst = occ.end
                        if (!eInst.isAfter(fromInstant) || !sInst.isBefore(toEndInstant)) continue

                        result.add(
                            SkeletonOccurrence(
                                eventId = rep.eventId,
                                calendarId = rep.calendarId,
                                uid = rep.eventUid,
                                color = color,
                                fullDay = rep.fullDay != 0,
                                startInstant = sInst,
                                endInstant = eInst,
                                // absolute, DTSTART-relative number (matches the real expansion path)
                                occurrenceNumber = occ.number,
                            )
                        )
                    }
                }
            }

            // override rows (single edits)
            for (override in overrides) {
                val color = resolveColor(override.eventId, override.calendarId) ?: continue
                val startSec = override.startTime ?: continue
                val endSec = override.endTime ?: continue
                result.add(
                    SkeletonOccurrence(
                        eventId = override.eventId,
                        calendarId = override.calendarId,
                        uid = override.eventUid,
                        color = color,
                        fullDay = override.fullDay != 0,
                        startInstant = Instant.ofEpochSecond(startSec),
                        endInstant = Instant.ofEpochSecond(endSec),
                        occurrenceNumber = 0,
                    )
                )
            }
        }

        return result
    }

    // same rule as UiEvent.spansSingleDay
    private fun addSpannedDays(
        startInstant: Instant,
        endInstant: Instant,
        isAllDay: Boolean,
        timeZoneId: String,
        color: String,
        indicators: MutableMap<LocalDate, MutableList<String>>,
    ) {
        val zone = ZoneId.of(timeZoneId)
        val startLocal = startInstant.atZone(zone)
        val endLocal = endInstant.atZone(zone)
        val partTimeEndsOnMidnight = !isAllDay && endLocal.toLocalTime() == LocalTime.MIDNIGHT

        var day = startLocal.toLocalDate()
        val endDay = endLocal.toLocalDate()
        while (!day.isAfter(endDay)) {
            indicators.getOrPut(day) { mutableListOf() }.add(color)
            day = day.plusDays(1)
            if (day == endDay && (isAllDay || partTimeEndsOnMidnight)) break
        }
    }

    // mirrors expandOccurrencesWithSingleEditsAndExDatesToUiEvents with a fake VEVENT
    private fun expandRecurring(
        rrule: String,
        dtStartInstant: Instant,
        dtEndInstant: Instant,
        fullDay: Boolean,
        expansionTimeZoneId: String,
        formatTimeZoneId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<ExpandedOccurrence>? {
        val recurrence = ICalUtilsImpl.parseRecurrence(rrule) ?: return null
        val expansionZone = ZoneId.of(expansionTimeZoneId)
        val displayZone = ZoneId.of(formatTimeZoneId)
        val durationSec = dtEndInstant.epochSecond - dtStartInstant.epochSecond
        val timed = !fullDay && durationSec > 0

        // the common repeating events (plain daily and weekly-on-weekdays) take the fast date-math path
        val winStart = fromDate.atStartOfDay(displayZone).toInstant()
        val winEnd = toDate.plusDays(1).atStartOfDay(displayZone).toInstant()
        if (timed && isSimpleInfiniteDaily(recurrence)) {
            return FastOccurrenceGenerator.dailyOccurrencesInWindow(
                dtStartInstant, durationSec, recurrence.interval ?: 1, expansionZone, winStart, winEnd,
            )
        }
        if (timed && isSimpleInfiniteWeekly(recurrence)) {
            val byDays = recurrence.byDay.mapNotNull { bd -> bd.day?.let { runCatching { DayOfWeek.valueOf(it.name) }.getOrNull() } }.toSet()
            val wkst = recurrence.workweekStarts?.let { runCatching { DayOfWeek.valueOf(it.name) }.getOrNull() } ?: DayOfWeek.MONDAY
            if (byDays.size == recurrence.byDay.size) { // all weekdays converted cleanly
                FastOccurrenceGenerator.weeklyOccurrencesInWindow(
                    dtStartInstant, durationSec, recurrence.interval ?: 1, byDays, wkst, expansionZone, winStart, winEnd,
                )?.let { return it }
            }
        }

        // everything else (monthly, yearly, anything with COUNT/UNTIL or fancier BY* rules) is rarer and cheaper:
        // build a throwaway event and let biweekly expand it. the caller trims the result to the window.
        val startLocal = dtStartInstant.atZone(expansionZone).toLocalDateTime()
        val endLocalRaw = dtEndInstant.atZone(expansionZone).toLocalDateTime()
        val endLocal = if (!fullDay && !endLocalRaw.isAfter(startLocal)) {
            endLocalRaw.plusDays(1)
        } else endLocalRaw

        val dummy = generateDummyEvent(startLocal, endLocal, rrule, expansionZone, fullDay) ?: return null
        val occurrences = dummy.generateOccurrencesUntil(toDate, formatTimeZoneId) ?: return null
        return occurrences.map { ExpandedOccurrence(it.startDateTime.toInstant(), it.endDateTime.toInstant(), it.occurrenceNumber) }
    }

    // plain FREQ=DAILY with no BY* parts and no COUNT/UNTIL — i.e. infinite, fixed-interval daily
    private fun isSimpleInfiniteDaily(recurrence: Recurrence): Boolean {
        if (recurrence.frequency != Frequency.DAILY) return false
        if (recurrence.count != null || recurrence.until != null) return false
        return recurrence.byDay.isEmpty() && recurrence.byMonth.isEmpty() &&
            recurrence.byMonthDay.isEmpty() && recurrence.byYearDay.isEmpty() &&
            recurrence.byWeekNo.isEmpty() && recurrence.bySetPos.isEmpty() &&
            recurrence.byHour.isEmpty() && recurrence.byMinute.isEmpty() &&
            recurrence.bySecond.isEmpty()
    }

    // plain FREQ=WEEKLY;BYDAY=... (weekday list, no ordinals), no COUNT/UNTIL, no other BY* parts
    private fun isSimpleInfiniteWeekly(recurrence: Recurrence): Boolean {
        if (recurrence.frequency != Frequency.WEEKLY) return false
        if (recurrence.count != null || recurrence.until != null) return false
        if (recurrence.byDay.isEmpty() || recurrence.byDay.any { it.num != null }) return false
        return recurrence.byMonth.isEmpty() && recurrence.byMonthDay.isEmpty() &&
            recurrence.byYearDay.isEmpty() && recurrence.byWeekNo.isEmpty() &&
            recurrence.bySetPos.isEmpty() && recurrence.byHour.isEmpty() &&
            recurrence.byMinute.isEmpty() && recurrence.bySecond.isEmpty()
    }

    private fun generateDummyEvent(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        rrule: String,
        zoneId: ZoneId,
        fullDay: Boolean,
    ): Event? {
        val iCalString = if (fullDay) {
            val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            // VALUE=DATE -> isAllDay()=true
            """
                BEGIN:VCALENDAR
                PRODID:0
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART;VALUE=DATE:${startLocal.toLocalDate().format(dateFormatter)}
                DTEND;VALUE=DATE:${endLocal.toLocalDate().format(dateFormatter)}
                RRULE:$rrule
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()
        } else {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            """
                BEGIN:VCALENDAR
                PRODID:0
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART;TZID=${zoneId.id}:${startLocal.format(formatter)}
                DTEND;TZID=${zoneId.id}:${endLocal.format(formatter)}
                RRULE:$rrule
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()
        }
        return try {
            Event.dummyFrom(ICalUtilsImpl.parseICalString(iCalString)!!)
        } catch (t: Throwable) {
            logger.e("MetadataIndicatorsCalculator: failed to build dummy iCal for RRULE='$rrule'", t)
            null
        }
    }
}
