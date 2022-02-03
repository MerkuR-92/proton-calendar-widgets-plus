package me.proton.android.calendar.eventmanager.listeners.core

import me.proton.android.calendar.data.api.ServerCoreEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
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

class CalendarMemberEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
): CalendarBaseEventListener<String, MemberEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Core

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, MemberEntity>>? {
        return response.body.deserializeOrNull<ServerCoreEventsApiResponse>()?.calendarMembers?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.member)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<MemberEntity>) {
        entities.forEach {
            if (calendarsRepository.hasCalendar(it.calendarId)) {
                calendarsRepository.persistMember(it)
            } else {
                logger.i("action CREATE/UPDATE for calendarMember ${it.id} in deleted calendar ${it.calendarId}")
            }
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        keys.forEach { calendarsRepository.deleteMemberById(it) }
    }
}
