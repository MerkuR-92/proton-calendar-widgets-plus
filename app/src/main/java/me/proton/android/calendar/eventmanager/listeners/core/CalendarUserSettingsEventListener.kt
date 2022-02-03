package me.proton.android.calendar.eventmanager.listeners.core

import me.proton.android.calendar.data.api.ServerCoreEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.CalendarUserSettingsChangedUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarUserSettingsEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase,
): CalendarBaseEventListener<String, CalendarUserSettingsEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Core

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarUserSettingsEntity>>? {
        return response.body.deserializeOrNull<ServerCoreEventsApiResponse>()?.let {
            // CalendarUserSettings is a special case, it can only be updated
            if (it.calendarUserSettings != null) {
                listOf(Event(Action.Update, it.eventId, it.calendarUserSettings))
            } else {
                null
            }
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarUserSettingsEntity>) {
        entities.forEach { calendarUserSettingsChangedUseCase.execute(config.userId.id, it) }
    }
}
