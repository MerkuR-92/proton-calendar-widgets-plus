package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueWorkHelper
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.data.api.CalendarAlarmsEvents
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.SafePersistEventAlarmUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarAlarmEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase,
    private val workManager: WorkManager,
    private val logger: Logger
): CalendarBaseEventListener<String, EventAlarmEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    private val alarmsWithMissingEvent = mutableSetOf<String>()
    private val missingEvents = mutableSetOf<String>()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, EventAlarmEntity>>? {
        return response.body.deserialize<CalendarAlarmsEvents>().calendarAlarms?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, mapToAlarmEntity(it))
        }
    }

    private fun mapToAlarmEntity(response: ServerEvent.AlarmsApiResponse) = response.alarm?.let {
        EventAlarmEntity(it.id, it.occurrence, it.trigger, it.action, it.eventId, it.memberId, it.calendarId)
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<EventAlarmEntity>) {
        val alarmsWithEvents = arrayListOf<EventAlarmEntity>()
        entities.forEach {
            if (calendarsRepository.hasEvent(it.eventId, it.calendarId)) {
                alarmsWithEvents.add(it)
            } else {
                // We keep ids of events to fetch and ids of alarms with missing event
                missingEvents.add(it.eventId)
                alarmsWithMissingEvent.add(it.id)
            }
        }

        // Only persist alarms with event for now. We will handle the rest from onSuccess in a worker.
        safePersistEventAlarmUseCase.invoke(alarmsWithEvents)
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach {
            calendarsRepository.deleteEventAlarmById(it)
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val actions = getActionMap(config)
        val alarmEvents = actions[Action.Create].orEmpty() + actions[Action.Update].orEmpty() + actions[Action.Delete].orEmpty()
        if (alarmEvents.isEmpty()) return

        missingEvents.forEach { eventId ->
            // Launch worker to handle alarms with missing events
            workManager.enqueueWorkHelper(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.HANDLE_ALARMS_WITH_MISSING_EVENT,
                    UseCaseWorker.INPUT_USER_ID to config.userId.id,
                    UseCaseWorker.INPUT_CALENDAR_ID to config.asCalendar().calendarId,
                    UseCaseWorker.INPUT_EVENT_ID to eventId
                ),
                UseCaseWorker.UniqueWorkNames.HANDLE_ALARMS_WITH_MISSING_EVENT,
                ExistingWorkPolicy.APPEND,
                NetworkType.CONNECTED
            )
        }

        // Launch worker to handle alarms
        workManager.enqueueWorkHelper(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.HANDLE_ALARMS,
                UseCaseWorker.INPUT_USER_ID to config.userId.id
            ),
            UseCaseWorker.UniqueWorkNames.HANDLE_ALARMS,
            ExistingWorkPolicy.REPLACE,
            NetworkType.CONNECTED
        )
    }

    override suspend fun onFailure(config: EventManagerConfig) {
        // Launch worker to force refresh and sync alarms
        workManager.enqueueWorkHelper(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_ALARMS,
                UseCaseWorker.INPUT_USER_ID to config.userId.id,
                UseCaseWorker.INPUT_FORCE_SYNC_ALARMS to true // Force sync alarms
            ),
            UseCaseWorker.UniqueWorkNames.SYNC_ALARMS,
            ExistingWorkPolicy.REPLACE,
            NetworkType.CONNECTED
        )
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        alarmsWithMissingEvent.clear()
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)

        logger.e("CalendarAlarmEventListener onResetAll [${config.asCalendar().calendarId}]")

        val calendarId = config.asCalendar().calendarId

        // We wipe the alarms from the DB
        calendarsRepository.deleteAllEventAlarmsByCalendar(calendarId)

        // Launch worker to force refresh and sync alarms
        workManager.enqueueWorkHelper(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_ALARMS,
                UseCaseWorker.INPUT_USER_ID to config.userId.id,
                UseCaseWorker.INPUT_FORCE_SYNC_ALARMS to true // Force sync alarms
            ),
            UseCaseWorker.UniqueWorkNames.SYNC_ALARMS,
            ExistingWorkPolicy.REPLACE,
            NetworkType.CONNECTED
        )
    }
}
