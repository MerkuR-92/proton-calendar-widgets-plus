package com.alamkanak.weekview

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal typealias EventChipsCacheProvider = () -> EventChipsCache?

internal class EventChipsCache {

    // written on the background events thread, read on the main (render) thread
    @Volatile
    var generation: Long = 0
        private set

    val allEventChips: List<EventChip>
        get() = normalEventChipsByDate.values.flatten() + allDayEventChipsByDate.values.flatten()

    private val normalEventChipsByDate = ConcurrentHashMap<Long, CopyOnWriteArrayList<EventChip>>()
    private val allDayEventChipsByDate = ConcurrentHashMap<Long, CopyOnWriteArrayList<EventChip>>()

    fun allEventChipsInDateRange(
        dateRange: List<Calendar>
    ): List<EventChip> {
        val results = mutableListOf<EventChip>()
        for (date in dateRange) {
            if (allDayEventChipsByDate.isNotEmpty()) results += allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
            if (normalEventChipsByDate.isNotEmpty()) results += normalEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
        }
        return results
    }

    fun normalEventChipsByDate(
        date: Calendar
    ): List<EventChip> = normalEventChipsByDate(date.atStartOfDay.timeInMillis)

    fun normalEventChipsByDate(
        dateMillis: Long
    ): List<EventChip> = if (normalEventChipsByDate.isNotEmpty()) normalEventChipsByDate[dateMillis].orEmpty() else emptyList()

    fun allDayEventChipsByDate(
        date: Calendar
    ): List<EventChip> = if (allDayEventChipsByDate.isNotEmpty()) allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty() else emptyList()

    fun allDayEventChipsInDateRange(
        dateRange: List<Calendar>
    ): List<EventChip> {
        val results = mutableListOf<EventChip>()
        for (date in dateRange) {
            if (allDayEventChipsByDate.isNotEmpty()) results += allDayEventChipsByDate[date.atStartOfDay.timeInMillis].orEmpty()
        }
        return results
    }

    fun replaceAll(eventChips: List<EventChip>) {
        clear()
        addAll(eventChips)
    }

    fun addAll(eventChips: List<EventChip>) {
        if (eventChips.isNotEmpty()) {
            removeByEventIds(eventChips.mapTo(HashSet(eventChips.size)) { it.eventId })
        }

        for (eventChip in eventChips) {
            val key = eventChip.startTime.atStartOfDay.timeInMillis

            if (eventChip.event.isAllDay || eventChip.event.isMultiDay) {
                allDayEventChipsByDate.addOrReplace(key, eventChip)
            } else {
                normalEventChipsByDate.addOrReplace(key, eventChip)
            }
        }
        generation++
    }

    private fun removeByEventIds(eventIds: Set<String>) {
        for (bucket in normalEventChipsByDate.values) {
            bucket.removeAll { it.event.id in eventIds }
        }
        for (bucket in allDayEventChipsByDate.values) {
            bucket.removeAll { it.event.id in eventIds }
        }
    }

    fun findHitEvent(x: Float, y: Float): EventChip? {
        val candidates = allEventChips.filter { it.isHit(x, y) }
        return when {
            candidates.isEmpty() -> null
            // Two events hit. This is most likely because an all-day event was clicked, but a
            // single event is rendered underneath it. We return the all-day event.
            candidates.size == 2 -> candidates.first { it.event.isAllDay || it.event.isMultiDay }
            else -> candidates.first()
        }
    }

    fun remove(eventId: String) {
        val eventChip = allEventChips.firstOrNull { it.eventId == eventId } ?: return
        remove(eventChip)
    }

    fun removeAll(events: List<ResolvedWeekViewEntity>) {
        if (events.isEmpty()) return
        removeByEventIds(events.mapTo(HashSet(events.size)) { it.id })
        generation++
    }

    private fun remove(eventChip: EventChip) {
        val key = eventChip.startTime.atStartOfDay.timeInMillis
        val eventId = eventChip.eventId

        if (allDayEventChipsByDate.isNotEmpty() && (eventChip.event.isAllDay || eventChip.event.isMultiDay)) {
            allDayEventChipsByDate[key]?.removeAll { it.event.id == eventId }
        } else if (normalEventChipsByDate.isNotEmpty()) {
            normalEventChipsByDate[key]?.removeAll { it.event.id == eventId }
        }
    }

    fun clear() {
        allDayEventChipsByDate.clear()
        normalEventChipsByDate.clear()
        generation++
    }

    private fun ConcurrentHashMap<Long, CopyOnWriteArrayList<EventChip>>.addOrReplace(
        key: Long,
        eventChip: EventChip
    ) {
        val results = getOrElse(key) { CopyOnWriteArrayList() }
        val indexOfExisting =
            if (results.isEmpty()) -1
            else results.indexOfFirst { it.event.id == eventChip.event.id }
        if (indexOfExisting != -1) {
            // If an event with the same ID already exists, replace it. The new event will likely be
            // more up-to-date.
            results.removeAt(indexOfExisting)
            results.add(indexOfExisting, eventChip)
        } else {
            results.add(eventChip)
        }
        this[key] = results
    }
}
