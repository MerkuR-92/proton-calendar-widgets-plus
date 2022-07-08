package me.proton.android.calendar

import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.model.Event

internal abstract class BaseTest {

    fun eventForICalString(iCalString: String, eventId: String? = null, type: Int = 0): Event {
        return Event.from(eventId ?: "event-id", me.proton.android.calendar.domain.model.Calendar(
            "calendar-id",
            "calendar",
            "email",
            "",
            1,
            true,
            type
        ), ICalUtilsImpl.parseICalString(iCalString)!!, 0, null, null)!!
    }

}
