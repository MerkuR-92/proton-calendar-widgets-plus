package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import me.proton.android.calendar.domain.model.UiEvent
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UiEventExpansionCacheTest {

    private val from = LocalDate.of(2025, 6, 1)
    private val to = LocalDate.of(2025, 6, 30)

    private fun key(siblings: List<UiEventExpansionCache.SiblingId>) = UiEventExpansionCache.Key(
        originalEventId = "e1",
        originalCalendarId = "calA",
        originalModifyTime = 0,
        calendarColor = "#FF0000",
        siblings = siblings,
        fromDate = from,
        toDate = to,
        timeZoneId = "UTC",
        userEmailsHash = 0,
        isFreeUser = false,
    )

    @Test
    fun `siblings differing only by calendar are different cache keys`() {
        // sibling ids can collide across calendars - the cache key must tell them apart
        val cache = UiEventExpansionCache()
        val expansion = listOf<UiEvent>(mockk())

        cache.put(key(listOf(UiEventExpansionCache.SiblingId("1", "calA", 100))), expansion)

        assertThat(cache.get(key(listOf(UiEventExpansionCache.SiblingId("1", "calB", 100))))).isNull()
        assertThat(cache.get(key(listOf(UiEventExpansionCache.SiblingId("1", "calA", 100))))).isEqualTo(expansion)
    }
}
