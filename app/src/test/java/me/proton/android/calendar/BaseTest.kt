package me.proton.android.calendar

import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.koin.core.context.startKoin
import org.koin.test.KoinTest

internal abstract class BaseTest {

    fun eventForICalString(iCalString: String, eventId: String? = null): Event {
        return Event(eventId ?: "event-id", me.proton.android.calendar.domain.model.Calendar(
            "calendar-id",
            "calendar",
            "",
            1,
            true
        ), ICalUtils.parseICalString(iCalString)!!, null, null)
    }

}
