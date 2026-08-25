package me.proton.android.calendar.domain.usecase

import androidx.collection.LruCache
import me.proton.android.calendar.domain.model.UiEvent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the result of recurrence expansion (expandOccurrencesWithSingleEditsAndExDatesToUiEvents) so
 * re-viewing a window (e.g. flipping back to a previously opened agenda day) doesn't re-run the RRULE
 * expansion — which the decrypt cache does not cover and is the dominant cost on warm re-views.
 *
 * The [Key] captures everything the expansion output depends on (event version + its single-edit/exdate
 * siblings' versions + window + timezone + user emails + free/paid + calendar color), so any change to
 * the event, its edits, or the rendering inputs naturally produces a fresh key — i.e. it self-invalidates.
 */
@Singleton
class UiEventExpansionCache @Inject constructor() {

    data class SiblingId(val eventId: String, val calendarId: String, val modifyTime: Long)

    data class Key(
        val originalEventId: String,
        val originalCalendarId: String,
        val originalModifyTime: Long,
        val calendarColor: String,
        val siblings: List<SiblingId>,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val timeZoneId: String,
        val userEmailsHash: Int,
        val isFreeUser: Boolean,
    )

    /** Identifies a (user, window) that has already been fully loaded once this session. */
    data class WindowKey(
        val userId: String,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val timeZoneId: String,
    )

    // bound by total cached UiEvents (not entry count) so a few large windows can't blow up memory
    private val cache = object : LruCache<Key, List<UiEvent>>(MAX_CACHED_UI_EVENTS) {
        override fun sizeOf(key: Key, value: List<UiEvent>): Int = value.size.coerceAtLeast(1)
    }
    private val seenWindows = LruCache<WindowKey, Boolean>(MAX_SEEN_WINDOWS)

    fun get(key: Key): List<UiEvent>? = cache.get(key)

    fun put(key: Key, value: List<UiEvent>) {
        cache.put(key, value)
    }

    /** True once [markWindowSeen] has run for this window — used to skip the (re)computed skeleton. */
    fun hasSeenWindow(key: WindowKey): Boolean = seenWindows.get(key) != null

    fun markWindowSeen(key: WindowKey) {
        seenWindows.put(key, true)
    }

    fun clear() {
        cache.evictAll()
        seenWindows.evictAll()
    }

    private companion object {
        const val MAX_CACHED_UI_EVENTS = 5_000
        const val MAX_SEEN_WINDOWS = 200
    }
}
