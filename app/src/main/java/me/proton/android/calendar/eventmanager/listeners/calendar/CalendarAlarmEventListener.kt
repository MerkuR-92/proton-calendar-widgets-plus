package me.proton.android.calendar.eventmanager.listeners.calendar

import me.proton.android.calendar.data.api.ServerCalendarEventsApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.HandleEventsMetadataUseCase
import me.proton.android.calendar.domain.usecase.SyncAlarmsUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarAlarmEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val syncAlarmsUseCase: SyncAlarmsUseCase,
    private val logger: Logger,
): CalendarBaseEventListener<String, EventAlarmEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    private val invalidAlarmIds = mutableSetOf<String>()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, EventAlarmEntity>>? {
        return response.body.deserializeOrNull<ServerCalendarEventsApiResponse>()?.calendarAlarms?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, mapToAlarmEntity(it))
        }
    }

    private fun mapToAlarmEntity(response: ServerEvent.AlarmsApiResponse) = response.alarm?.let {
        EventAlarmEntity(it.id, it.occurrence, it.trigger, it.action, it.eventId, it.memberId, it.calendarId)
    }

    override suspend fun onPrepare(config: EventManagerConfig, entities: List<EventAlarmEntity>) {
        val alarmsWithMissingEvent = entities.filter { !calendarsRepository.hasEvent(it.eventId, it.calendarId) }
        val eventIdsToFetch = alarmsWithMissingEvent.mapNotNull {
            if (HandleEventsMetadataUseCase.shouldFetchEvent(it)) {
                logger.v("event ${it.eventId} for alarm doesn't exist in DB")
                it
            } else {
                logger.v("event ${it.eventId} for alarm doesn't exist in DB but is outside of sync window")
                invalidAlarmIds += it.id
                null
            }
        }
        if (eventIdsToFetch.isEmpty()) return

        val missingEvents = eventIdsToFetch.mapNotNull {
            val event = calendarsRepository.fetchEventById(config.userId, it.calendarId, it.eventId)
                .valueOrNullAndLogErrors(logger)
                ?.event

            // if we failed to fetch the missing Event for Alarm, make the Alarm invalid
            if (event == null) {
                invalidAlarmIds += it.id
                logger.i("CalendarAlarmEventListener could not fetch missing Event for Alarm")
            }

            event
        }
        calendarsRepository.persistEvents(*missingEvents.toTypedArray())
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<EventAlarmEntity>) {
        entities.filter { !invalidAlarmIds.contains(it.id) }.forEach { calendarsRepository.persistEventAlarm(logger, it) }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach { calendarsRepository.deleteEventAlarmById(it) }
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        calendarsRepository.deleteAllEventAlarms(config.asCalendar().calendarId)
    }

    override suspend fun onFailure(config: EventManagerConfig) {
        syncAlarmsUseCase.execute(config.userId, force = true)
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val actions = getActionMap(config)
        val alarmEvents = actions[Action.Create].orEmpty() + actions[Action.Update].orEmpty() + actions[Action.Delete].orEmpty()
        if (alarmEvents.isEmpty()) return
        handleAlarmsUseCase.execute(config.userId)
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        invalidAlarmIds.clear()
    }
}
