package me.proton.android.calendar.eventmanager.listeners.calendar

import me.proton.android.calendar.data.api.ServerCalendarEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarKeyEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
): CalendarBaseEventListener<String, CalendarKeyEntity>(db) {
    override val order: Int = 3
    override val type: Type = Type.Calendar

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarKeyEntity>>? {
        return response.body.deserializeOrNull<ServerCalendarEventsApiResponse>()?.calendarKeys?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.key)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarKeyEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarKey in deleted calendar")
            return
        }
        entities.forEach { calendarsRepository.persistCalendarKey(it) }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach { calendarsRepository.deleteCalendarKeyById(it) }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val updatedItems = getActionMap(config)[Action.Create].orEmpty() + getActionMap(config)[Action.Update].orEmpty()
        if (updatedItems.isEmpty()) return

        // TODO If new key, fetch all calendars to get flags (flags are not yet returned in fetch by id)
        //  Replace this with select calendar by id once BE implements updated flags there
        calendarsRepository.refreshCalendarsFlags(config.userId)
    }
}
