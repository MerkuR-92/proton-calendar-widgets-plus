package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Refreshes the calendars and bootstraps any calendar missing its local setup
 */
class EnsureCalendarsCompleteUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val bootstrapCalendarUseCase: BootstrapCalendarUseCase,
    private val logger: Logger,
) : UseCase {

    /** @return whether the calendar list refresh from the API succeeded. */
    suspend fun execute(userId: UserId): Boolean {
        val refreshed = calendarsRepository.refreshCalendars(userId)

        val completeIds = calendarsRepository.selectAllCalendars(userId.id).map { it.id }.toSet()
        val incomplete = calendarsRepository.selectCalendarEntities(userId.id).filterNot { it.id in completeIds }

        incomplete.forEach { entity ->
            logger.i("EnsureCalendarsComplete: bootstrapping incomplete calendar ${entity.id}")
            bootstrapCalendarUseCase.executeBootstrap(entity, userId)
        }
        return refreshed
    }
}
