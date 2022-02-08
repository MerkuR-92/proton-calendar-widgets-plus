package me.proton.android.calendar.eventmanager.listeners.calendar

import me.proton.android.calendar.data.api.ServerCalendarEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarSettingsEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
): CalendarBaseEventListener<String, CalendarSettingsEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarSettingsEntity>>? {
        return response.body.deserializeOrNull<ServerCalendarEventsApiResponse>()?.calendarSettings?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.calendarSettings)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarSettingsEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarKey in deleted calendar")
            return
        }
        entities.forEach { calendarsRepository.persistCalendarSettings(it) }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarSettingsEntity>) {
        onCreate(config, entities)
    }
}
