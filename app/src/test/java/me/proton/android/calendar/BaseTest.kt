package me.proton.android.calendar

import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.model.Event

internal abstract class BaseTest {

    fun eventForICalString(iCalString: String, eventId: String? = null): Event {
        return Event.from(eventId ?: "event-id", me.proton.android.calendar.domain.model.Calendar(
            "calendar-id",
            "calendar",
            "",
            1,
            true,
            0
        ), ICalUtilsImpl.parseICalString(iCalString)!!, null, null)!!
    }

}
