package me.proton.android.calendar.eventmanager.listeners.core

import androidx.annotation.VisibleForTesting
import me.proton.android.calendar.data.api.ServerCoreEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.BootstrapCalendarUseCase
import me.proton.android.calendar.domain.usecase.KeySetupUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.util.kotlin.deserializeOrNull
import java.time.ZoneId
import javax.inject.Inject

class CalendarListener @Inject constructor(
    database: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val bootstrapCalendarUseCase: BootstrapCalendarUseCase,
    private val logger: Logger,
): CalendarBaseEventListener<String, CalendarEntity>(database) {
    override val order: Int = 1
    override val type: Type = Type.Core

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarEntity>>? {
        return response.body.deserializeOrNull<ServerCoreEventsApiResponse>()?.calendars?.map {
            Event(requireNotNull(Action.Companion.map[it.action]), it.id, it.calendar)
        }
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onCreate(config, entities)

        val timezone = calendarsRepository.selectCalendarUserSettings(config.userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id

        entities.map {
            val executeBootstrapResult = bootstrapCalendarUseCase.executeBootstrap(it, config.userId, timezone)
            executeBootstrapResult.ifSuccessAndLogErrors(logger) { }

            // If bootstraping failed, persist calendar manually
            if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                calendarsRepository.persistCalendar(config.userId.id, it)
            }
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onUpdate(config, entities)

        val timezone = calendarsRepository.selectCalendarUserSettings(config.userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id

        entities.map {
            if (calendarsRepository.selectCalendar(it.id) == null) {

                val executeBootstrapResult = bootstrapCalendarUseCase.executeBootstrap(it, config.userId, timezone)
                executeBootstrapResult.ifSuccessAndLogErrors(logger) { }

                // If bootstraping failed, persist calendar manually
                if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                    calendarsRepository.persistCalendar(config.userId.id, it)
                }
            } else {
                calendarsRepository.persistCalendar(config.userId.id, it)
            }
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        keys.map { calendarsRepository.deleteCalendarById(it) }
    }
}
