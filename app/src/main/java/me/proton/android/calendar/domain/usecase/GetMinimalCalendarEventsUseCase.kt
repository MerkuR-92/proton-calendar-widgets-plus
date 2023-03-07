package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.getCachedMonthViewsTimeWindow
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetMinimalCalendarEventsUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase
) {
    suspend fun execute(userId: UserId, fetch: Boolean = false): Flow<List<Event>?> {
        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id
        val zoneId = if (timezone.isBlank()) ZoneId.systemDefault() else ZoneId.of(timezone)
        val now = LocalDate.now(zoneId)
        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val timeWindow = getCachedMonthViewsTimeWindow(now, weekStart)
        val fromDate = timeWindow.first
        val toDate = timeWindow.second
        if (fetch) {
            calendarsRepository.fetchEvents(userId, fromDate, toDate, timezone)
        }
        return calendarsRepository.eventsFlow(fromDate, toDate, timezone)
    }
}
