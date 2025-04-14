package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueWorkHelper
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.data.api.CalendarKeysEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarKeyEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val workManager: WorkManager,
    private val logger: Logger,
): CalendarBaseEventListener<String, CalendarKeyEntity>(db) {
    override val order: Int = 3
    override val type: Type = Type.Calendar

    private var refreshMembersFlags: Boolean = false

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarKeyEntity>>? {
        return response.body.deserialize<CalendarKeysEvents>().calendarKeys?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.key)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarKeyEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarKey in deleted calendar")
            return
        }
        if (entities.isNotEmpty()) refreshMembersFlags = true
        entities.forEach {
            calendarsRepository.persistCalendarKey(it)
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach {
            calendarsRepository.deleteCalendarKeyById(it)
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val updatedItems = getActionMap(config)[Action.Create].orEmpty() + getActionMap(config)[Action.Update].orEmpty()
        if (updatedItems.isEmpty()) return

        if (refreshMembersFlags) {
            // Launch worker to refresh members flags
            workManager.enqueueWorkHelper(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.REFRESH_MEMBERS_FLAGS,
                    UseCaseWorker.INPUT_USER_ID to config.userId.id
                ),
                UseCaseWorker.UniqueWorkNames.REFRESH_MEMBERS_FLAGS,
                ExistingWorkPolicy.REPLACE,
                NetworkType.CONNECTED
            )

            refreshMembersFlags = false
        }
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)

        logger.e("CalendarKeyEventListener onResetAll [${config.asCalendar().calendarId}]")

        val calendarId = config.asCalendar().calendarId

        // Wipe calendar keys from DB
        calendarsRepository.deleteCalendarKeyByCalendarId(calendarId)

        // Launch worker to refresh calendar keys
        workManager.enqueueWorkHelper(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.REFRESH_CALENDAR_KEYS,
                UseCaseWorker.INPUT_USER_ID to config.userId.id,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId
            ),
            UseCaseWorker.UniqueWorkNames.REFRESH_CALENDAR_KEYS,
            ExistingWorkPolicy.APPEND,
            NetworkType.CONNECTED
        )
    }
}
