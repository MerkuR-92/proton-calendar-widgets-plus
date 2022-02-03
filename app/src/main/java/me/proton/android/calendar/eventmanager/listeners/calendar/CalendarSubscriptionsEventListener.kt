package me.proton.android.calendar.eventmanager.listeners.calendar

import me.proton.android.calendar.data.api.ServerCalendarEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
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

class CalendarSubscriptionsEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
): CalendarBaseEventListener<String, CalendarSubscriptionEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarSubscriptionEntity>>? {
        return response.body.deserializeOrNull<ServerCalendarEventsApiResponse>()?.calendarSubscriptions?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.calendarSubscriptionEntity)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarSubscriptionEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarSubscriptions in deleted calendar")
            return
        }
        entities.forEach { calendarsRepository.persistCalendarSubscription(it) }
    }
}
