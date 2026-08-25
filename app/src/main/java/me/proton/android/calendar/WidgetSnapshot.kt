package me.proton.android.calendar

import biweekly.parameter.ParticipationStatus
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatFullDayCounter
import me.proton.android.calendar.common.utils.ICalUtilsImpl.explodeDayByDay
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.core.util.kotlin.takeIfNotBlank
import java.time.LocalDate
import java.time.ZoneId

// keeps the RemoteViews list within a single Binder transaction
internal const val MAX_WIDGET_EVENTS = 100

internal data class WidgetSnapshot(
    val events: List<WidgetEvent>,
    val statusText: String?,
    val showButtons: Boolean,
)

internal fun List<UiEvent>.toWidgetSnapshot(
    resourceProvider: ResourceProvider,
    fromDate: LocalDate,
    toDate: LocalDate,
    zoneId: ZoneId,
    is24Hour: Boolean,
): WidgetSnapshot {
    val widgetEvents = mutableListOf<WidgetEvent>()

    this.explodeDayByDay(fromDate, toDate, zoneId.id)
        .toSortedMap()
        .forEach { entry ->
            if (widgetEvents.size >= MAX_WIDGET_EVENTS) return@forEach
            val sorted = entry.value
                .filter { !it.isInThePast() }
                .distinctBy { Triple(it.id, it.calendarId, it.occurrenceNumber) }
                .sortedBy { "${!it.isAllDay}${it.dateStart.toEpochSecond()}${it.summary}" }

            sorted.forEachIndexed { index, e ->
                if (widgetEvents.size >= MAX_WIDGET_EVENTS) return@forEachIndexed
                widgetEvents.add(
                    e.toWidgetEvent(
                        resourceProvider = resourceProvider,
                        happensOn = entry.key,
                        timeZoneId = zoneId.id,
                        showDateColumn = index == 0,
                        showBottomSpacing = index == sorted.size - 1,
                        showNoEventsToday = false,
                        is24Hour = is24Hour,
                    )
                )
            }
        }

    if (widgetEvents.isNotEmpty() && widgetEvents.first().happensOn != fromDate) {
        widgetEvents.add(
            0,
            widgetEvents.first().copy(
                happensOn = LocalDate.now(),
                showDateColumn = true,
                showBottomSpacing = true,
                showNoEventsToday = true,
                color = resourceProvider.provideString(R.color.separator_norm)
            )
        )
    }

    if (widgetEvents.size > MAX_WIDGET_EVENTS) widgetEvents.subList(MAX_WIDGET_EVENTS, widgetEvents.size).clear()

    widgetEvents.removeLastOrNull()?.let { widgetEvents.add(it.copy(showBottomSpacing = false)) }

    return WidgetSnapshot(
        events = widgetEvents,
        statusText = when {
            widgetEvents.isEmpty() -> resourceProvider.provideString(R.string.calendar_widget_no_upcoming_events)
            else -> null
        },
        showButtons = true
    )
}

private fun UiEvent.toWidgetEvent(
    happensOn: LocalDate,
    timeZoneId: String,
    showDateColumn: Boolean,
    showBottomSpacing: Boolean,
    showNoEventsToday: Boolean,
    is24Hour: Boolean,
    resourceProvider: ResourceProvider,
): WidgetEvent {
    val fullDayCounter = this.calculateFullDayCounter(happensOn)

    val fullDayCounterString = if (fullDayCounter.second > 1) {
        this.formatFullDayCounter(happensOn)
    } else null

    val dateText = if (this.isAllDay) {
        resourceProvider.provideString(R.string.event_all_day)
    } else {
        if (fullDayCounter.second > 1) { // multi-day part-day
            when (fullDayCounter.first) {
                1 -> { // first day
                    resourceProvider.provideString(
                        R.string.calendar_widget_part_day_event_starts_at,
                        this.dateStart.formatTime(timeZoneId, is24Hour)
                    )
                }

                fullDayCounter.second -> { // last day
                    resourceProvider.provideString(
                        R.string.calendar_widget_part_day_event_ends_at,
                        this.dateEnd.formatTime(timeZoneId, is24Hour)
                    )
                }

                else -> { // day in the middle
                    resourceProvider.provideString(R.string.event_all_day)
                }
            }
        } else { // single-day part-day
            "${
                dateStart.formatTime(
                    timeZoneId,
                    is24Hour
                )
            } ‐ ${dateEnd.formatTime(timeZoneId, is24Hour)}"
        }
    }

    val locationText = this.location?.takeIfNotBlank()?.let { " • $it" }

    val subheaderContent = "${dateText}${locationText ?: ""}"

    return WidgetEvent(
        id = this.id,
        calendarId = this.calendarId,
        summary = this.summary?.replace("\n", " ")?.takeIfNotBlank()
            ?: resourceProvider.provideString(R.string.default_event_summary),
        subheaderContent = subheaderContent,
        happensOn = happensOn,
        showDateColumn = showDateColumn,
        showBottomSpacing = showBottomSpacing,
        showNoEventsToday = showNoEventsToday,
        fullDayCounter = fullDayCounterString,
        occurrenceNumber = this.occurrenceNumber,
        color = this.displayColor,
        isCancelledOrDeclined = this.decryptionStatus == Event.DecryptionStatus.Success && (this.isCancelled() || participationStatus == ParticipationStatus.DECLINED),
        needsAction = !this.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
        failedToDecrypt = this.decryptionStatus is Event.DecryptionStatus.Failure
    )
}
