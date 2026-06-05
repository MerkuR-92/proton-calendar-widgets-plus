package me.proton.android.calendar.data

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.proton.android.calendar.data.entity.EventEntity
import org.junit.jupiter.api.Test

/**
 * Tests [attributeByUids] grouping
 */
internal class AttributeEventsByUidTest {

    private fun event(id: String, uidInSharedEvents: String): EventEntity = EventEntity(
        id = id,
        calendarId = "cal",
        sharedEventId = null,
        calendarKeyPacket = null,
        createTime = 0L,
        modifyTime = 0L,
        permissions = 0,
        addressKeyPacket = null,
        addressId = null,
        sharedKeyPacket = null,
        sharedEvents = listOf<JsonElement>(JsonPrimitive("BEGIN:VEVENT\r\nUID:$uidInSharedEvents\r\nEND:VEVENT")),
        calendarEvents = emptyList(),
        attendeesEvents = emptyList(),
        attendees = emptyList(),
        attendeesInfo = null,
        isProtonProtonInvite = null,
    )

    @Test
    fun `empty uids returns empty map`() {
        assertThat(listOf(event("e1", "a")).attributeByUids(emptyList())).isEqualTo(emptyMap())
    }

    @Test
    fun `groups a recurring master and its single edits under the shared uid`() {
        val master = event("master", "series-1")
        val edit1 = event("edit1", "series-1")
        val edit2 = event("edit2", "series-1")
        val unrelated = event("unrelated", "other-9")

        val result =
            listOf(
                master,
                edit1,
                edit2,
                unrelated
            ).attributeByUids(listOf("series-1", "other-9"))

        assertThat(result.getValue("series-1")).containsOnly(master, edit1, edit2)
        assertThat(result.getValue("other-9")).containsOnly(unrelated)
    }

    @Test
    fun `every requested uid is a key, with an empty list when nothing matches`() {
        val result = listOf(event("e1", "a")).attributeByUids(listOf("a", "missing"))

        assertThat(result.keys).isEqualTo(setOf("a", "missing"))
        assertThat(result.getValue("a")).containsOnly(event("e1", "a"))
        assertThat(result.getValue("missing")).isEmpty()
    }

    @Test
    fun `an event is attributed only to its own uid`() {
        val a = event("a", "uid-a")
        val b = event("b", "uid-b")

        val result = listOf(a, b).attributeByUids(listOf("uid-a", "uid-b"))

        assertThat(result.getValue("uid-a")).containsOnly(a)
        assertThat(result.getValue("uid-b")).containsOnly(b)
    }
}
