package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueWorkHelper
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.data.api.CalendarSettingsEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.getDefaultAlarms
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

class CalendarSettingsEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
    private val workManager: WorkManager,
    private val database: AppDatabase
): CalendarBaseEventListener<String, CalendarSettingsEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    // Map of calendarIds and a list of eventIds
    private val updateEventsAlarms: HashMap<String, List<String>> = hashMapOf()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarSettingsEntity>>? {
        return response.body.deserialize<CalendarSettingsEvents>().calendarSettings?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.calendarSettings)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarSettingsEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarKey in deleted calendar")
            return
        }
        entities.forEach { newCalendarSettings ->
            val currentPartDayAlarms = calendarsRepository.selectCalendarSettings(newCalendarSettings.calendarId)?.getDefaultAlarms(
                Json.Default, false)
            val newPartDayAlarms = newCalendarSettings.getDefaultAlarms(Json.Default, false)

            val currentFullDayAlarms = calendarsRepository.selectCalendarSettings(newCalendarSettings.calendarId)?.getDefaultAlarms(
                Json.Default, true)
            val newFullDayAlarms = newCalendarSettings.getDefaultAlarms(Json.Default, true)

            // we persist new Calendar Settings because they are needed in calculations in next steps,
            //  but we have the previous Settings cached above
            calendarsRepository.persistCalendarSettings(newCalendarSettings)

            if (currentPartDayAlarms?.isTheSameAs(newPartDayAlarms) == false) {
                updateEventsAlarms[newCalendarSettings.calendarId] = database.eventsDao().selectPartDayOnly(newCalendarSettings.calendarId).map { it.id }
            }

            if (currentFullDayAlarms?.isTheSameAs(newFullDayAlarms) == false) {
                updateEventsAlarms[newCalendarSettings.calendarId] = database.eventsDao().selectAllDayOnly(newCalendarSettings.calendarId).map { it.id }
            }
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)

        updateEventsAlarms.forEach {
            // Launch worker to update alarms of given events
            workManager.enqueueWorkHelper(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_ALARMS,
                    UseCaseWorker.INPUT_USER_ID to config.userId.id,
                    UseCaseWorker.INPUT_EVENT_IDS to it.value.toTypedArray()
                ),
                UseCaseWorker.UniqueWorkNames.UPDATE_ALARMS,
                ExistingWorkPolicy.APPEND,
                NetworkType.CONNECTED
            )
        }
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        super.onComplete(config)

        updateEventsAlarms.clear()
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        logger.i("CalendarSettingsEventListener onResetAll")

        val calendarId = config.asCalendar().calendarId

        // Wipe calendar settings from DB
        calendarsRepository.deleteCalendarSettingsByCalendarId(calendarId)

        // Launch worker to refresh calendar settings
        workManager.enqueueWorkHelper(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.REFRESH_CALENDAR_SETTINGS,
                UseCaseWorker.INPUT_USER_ID to config.userId.id,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId
            ),
            UseCaseWorker.UniqueWorkNames.REFRESH_CALENDAR_SETTINGS,
            ExistingWorkPolicy.REPLACE,
            NetworkType.CONNECTED
        )
    }
}
