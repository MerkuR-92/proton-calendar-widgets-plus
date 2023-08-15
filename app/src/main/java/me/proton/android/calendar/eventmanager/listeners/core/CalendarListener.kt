package me.proton.android.calendar.eventmanager.listeners.core

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.data.api.CalendarsEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarListener @Inject constructor(
    database: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val workManager: WorkManager
): CalendarBaseEventListener<String, CalendarEntity>(database) {
    override val order: Int = 1
    override val type: Type = Type.Core

    private val calendarsToBootstrap: ArrayList<String> = arrayListOf()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarEntity>>? {
        return response.body.deserialize<CalendarsEvents>().calendars?.map {
            Event(requireNotNull(Action.Companion.map[it.action]), it.id, it.calendar)
        }
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onCreate(config, entities)

        entities.map {
            // We persist the calendar entity, bootstrap will be done in worker on success
            calendarsRepository.persistCalendar(config.userId.id, it)
            // Add id to the list of calendar to bootstrap in worker
            calendarsToBootstrap.add(it.id)
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onUpdate(config, entities)

        entities.map {
            if (calendarsRepository.selectCalendar(it.id) == null) {
                // We persist the calendar entity, bootstrap will be done in worker on success
                calendarsRepository.persistCalendar(config.userId.id, it)
                // Add id to the list of calendar to bootstrap in worker
                calendarsToBootstrap.add(it.id)
            } else {
                calendarsRepository.persistCalendar(config.userId.id, it)
            }
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        keys.map {
            val calendar = calendarsRepository.selectCalendar(it)
            // For holiday and shared calendars, we rely on member delete event
            if (calendar?.type != Calendar.CalendarType.HOLIDAY.value && calendar?.isSharedWithMe == false) {
                calendarsRepository.deleteCalendarById(it)
            }
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.BOOTSTRAP_CALENDARS,
                    UseCaseWorker.INPUT_USER_ID to config.userId.id,
                    UseCaseWorker.INPUT_CALENDAR_IDS to calendarsToBootstrap
                )
            )
            .build()

        workManager.enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.BOOTSTRAP_CALENDARS, ExistingWorkPolicy.APPEND, work).state
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        super.onComplete(config)

        calendarsToBootstrap.clear()
    }
}
