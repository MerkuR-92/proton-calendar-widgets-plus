package me.proton.android.calendar.eventmanager.listeners.calendar

import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.ServerCalendarEventsApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.FetchPublicKeysUseCase
import me.proton.android.calendar.domain.usecase.GetMinimalCalendarEventsUseCase
import me.proton.android.calendar.domain.usecase.HandleEventsMetadataUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val delegate: CalendarEventListenerDelegate,
    private val getMinimalCalendarEventsUseCase: GetMinimalCalendarEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val logger: Logger,
): CalendarBaseEventListener<String, ServerEvent.EventEntityMetadata>(db) {
    override val order: Int = 3
    override val type: Type = Type.Calendar
    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, ServerEvent.EventEntityMetadata>>? {
        return response.body.deserializeOrNull<ServerCalendarEventsApiResponse>()?.calendarEvents?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.event)
        }
    }

    override suspend fun onPrepare(config: EventManagerConfig, entities: List<ServerEvent.EventEntityMetadata>) {
        delegate.onPrepare(config, entities)
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<ServerEvent.EventEntityMetadata>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE for calendarEvent in deleted calendar")
            return
        }
        val entityIds = entities.map { it.id }
        delegate.onCreate(entityIds)
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<ServerEvent.EventEntityMetadata>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action UPDATE for calendarEvent in deleted calendar")
            return
        }
        val entityIds = entities.map { it.id }
        delegate.onUpdate(entityIds)
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        delegate.onDelete(keys)
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        calendarsRepository.deleteAllEvents(config.asCalendar().calendarId)
        val eventIds = getMinimalCalendarEventsUseCase.execute(config.userId, fetch = true).firstOrNull()?.map { it.id }
        eventIds?.let { updateAlarmsUseCase.execute(config.userId.id, it) }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val entityIds = getActionMap(config)[Action.Create]?.mapNotNull { it.entity?.id }.orEmpty() +
                getActionMap(config)[Action.Update]?.mapNotNull { it.entity?.id }.orEmpty()

        delegate.onSuccess(config, entityIds)
    }
}

class CalendarEventListenerDelegate @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
    private val widgetRefresher: WidgetRefresher,
    private val handleEventsMetadataUseCase: HandleEventsMetadataUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
) {

    private var entities = emptyMap<String, EventEntity>()

    suspend fun onPrepare(config: EventManagerConfig, eventsMetadata: List<ServerEvent.EventEntityMetadata>) {
        // Fetch any event not cached as the provided metadata is not enough to create entities
        entities = eventsMetadata.filter { calendarsRepository.shouldFetchEvent(it) }
            .mapNotNull { metadata ->
                fetchEventEntity(config.userId, metadata)
            }.associateBy { event -> event.id }
    }

    suspend fun onCreate(entityIds: List<String>) {
        if (entityIds.isEmpty()) return
        val entitiesToCreate = entityIds.mapNotNull { entities[it] }
        onUpsert(entitiesToCreate)
    }

    suspend fun onUpdate(entityIds: List<String>) {
        if (entityIds.isEmpty()) return
        val entitiesToUpdate = entityIds.mapNotNull { entities[it] }
        onUpsert(entitiesToUpdate)
    }

    private suspend fun onUpsert(entities: List<EventEntity>) {
        calendarsRepository.persistEvents(*entities.toTypedArray())
    }

    suspend fun onDelete(ids: List<String>) {
        if (ids.isEmpty()) return

        calendarsRepository.deleteEventsById(ids)
    }

    suspend fun onSuccess(config: EventManagerConfig, entityIds: List<String>) {
        val entitiesToPostProcess = entityIds.mapNotNull { entities[it] }
        if (entitiesToPostProcess.isEmpty()) return
        // Post process received events
        // TODO We are not using the result of that use case for now
        // fetchPublicKeysUseCase.execute(config.userId, entitiesToPostProcess)
        updateAlarmsUseCase.execute(config.userId.id, entitiesToPostProcess.map { it.id })
        widgetRefresher.refreshEventList()
        // Clean cached entities
        entities = emptyMap()
    }

    private suspend fun fetchEventEntity(userId: UserId, response: ServerEvent.EventEntityMetadata): EventEntity? {
        return when (val result = calendarsRepository.fetchEventById(userId, response.calendarId, response.id)) {
            is ApiResponse.Success<EventApiResponse> -> result.data.event
            is ApiResponse.Error -> {
                // If event was not found just omit it, otherwise we'll retry this indefinitely
                if (result.isNotFound()) return null
                else throw IllegalStateException(result.error)
            }
            is ApiResponse.Exception -> throw result.exception
        }
    }

}
