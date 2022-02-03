package me.proton.android.calendar.eventmanager.listeners.core

import androidx.annotation.VisibleForTesting
import me.proton.android.calendar.data.api.ServerCoreEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarFlags
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
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
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase,
    private val keySetupUseCase: KeySetupUseCase,
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
        }?.let { handleIncompleteKeys(config, it) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun handleIncompleteKeys(
        config: EventManagerConfig,
        events: List<Event<String, CalendarEntity>>
    ): List<Event<String, CalendarEntity>> {
        // Try to complete key setup for calendar:
        // - we use newly updated calendar fetched from API if it succeeds
        // - we use calendar from server event if it fails
        return events.map { event ->
            val entity = event.entity ?: return@map event
            val updatedCalendar = if (entity.hasIncompleteKeySetup) {
                when (val result = keySetupUseCase.execute(config.userId, entity.id)) {
                    is UseCase.Result.Success<*> -> {
                        val fetchedCalendar = calendarsRepository.fetchCalendar(config.userId, entity.id)
                        if (fetchedCalendar == null) {
                            logger.e("error getting calendar from API in HandleServerEventsUseCase")

                            var calendarFlags = entity.flags
                            calendarFlags -= CalendarFlags.INCOMPLETE_SETUP.value
                            // if calendar is inactive and no other error flags are set, make it active
                            if (calendarFlags == 0) calendarFlags = CalendarFlags.ACTIVE.value

                            entity.copy(flags = calendarFlags)
                        } else {
                            fetchedCalendar
                        }
                    }
                    is UseCase.Result.InvalidParams -> {
                        logger.e("keySetupResult invalid params: ${result.message}")
                        entity
                    }
                    is UseCase.Result.Error -> {
                        logger.e("keySetupResult error: ${result.message}")
                        entity
                    }
                }
            } else {
                entity
            }
            event.copy(entity = updatedCalendar)
        }
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onCreate(config, entities)

        val timezone = calendarsRepository.selectCalendarUserSettings(config.userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id

        entities.map {
            val executeBootstrapResult = bootstrapCalendarsUseCase.executeBootstrap(it, config.userId, timezone)
            executeBootstrapResult.ifSuccessAndLogErrors(logger) { }

            // If bootstraping failed, persist calendar manually
            if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                calendarsRepository.persistCalendar(config.userId.id, it)
            }
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onUpdate(config, entities)

        entities.map { calendarsRepository.persistCalendar(config.userId.id, it) }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        keys.map { calendarsRepository.deleteCalendarById(it) }
    }
}
