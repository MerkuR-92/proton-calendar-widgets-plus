package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class GetMinimalCalendarEventsUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
) {
    suspend fun execute(userId: UserId, fetch: Boolean = false): Flow<List<Event>?> {
        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id
        val zoneId = if (timezone.isBlank()) ZoneId.systemDefault() else ZoneId.of(timezone)
        val now = Instant.now()
        val fromDate = LocalDateTime.ofInstant(now.minus(60, ChronoUnit.DAYS), zoneId).toLocalDate()
        val toDate = LocalDateTime.ofInstant(now.plus(120, ChronoUnit.DAYS), zoneId).toLocalDate()
        if (fetch) {
            calendarsRepository.fetchEvents(userId, fromDate, toDate, timezone)
        }
        return calendarsRepository.eventsFlow(fromDate, toDate, timezone)
    }
}
