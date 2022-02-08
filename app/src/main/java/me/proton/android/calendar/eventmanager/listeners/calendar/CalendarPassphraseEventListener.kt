package me.proton.android.calendar.eventmanager.listeners.calendar

import me.proton.android.calendar.data.api.ServerCalendarEventsApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.CacheCalendarPassphraseUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserializeOrNull
import javax.inject.Inject

class CalendarPassphraseEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val logger: Logger,
): CalendarBaseEventListener<String, PassphraseEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Calendar

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, PassphraseEntity>>? {
        return response.body.deserializeOrNull<ServerCalendarEventsApiResponse>()?.calendarPassphrases?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.passphrase)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<PassphraseEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarPassphrase in deleted calendar")
            return
        }
        entities.forEach { calendarsRepository.persistPassphrase(it) }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach { calendarsRepository.deletePassphraseById(it) }
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        val events = (getActionMap(config)[Action.Create].orEmpty() + getActionMap(config)[Action.Update].orEmpty())
        if (events.isEmpty()) return

        // TODO make sure we delete passphrase from cache if it becomes inactive
        when (val result = cacheCalendarPassphraseUseCase.execute(config.userId, config.asCalendar().calendarId)) {
            is UseCase.Result.InvalidParams -> logger.e("event loop calendar passphrase caching InvalidParams in CalendarPassphraseEventListener: ${result.message}")
            is UseCase.Result.Error -> logger.e("event loop calendar passphrase caching Error in CalendarPassphraseEventListener: ${result.message}")
            else -> {}
        }
    }
}
