package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.getCachedMonthViewsTimeWindow
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetMinimalCalendarEventsUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val logger: Logger,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase
) {

    /**
     * @return success or failure
     */
    suspend fun execute(userId: UserId, calendarId: String): Boolean {
        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id
        val zoneId = if (timezone.isBlank()) ZoneId.systemDefault() else ZoneId.of(timezone)
        val now = LocalDate.now(zoneId)
        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val timeWindow = getCachedMonthViewsTimeWindow(now, weekStart)
        val fromDate = timeWindow.first
        val toDate = timeWindow.second

        val fetchEventsResult = fetchEventsUseCase.splitFetchEvents(userId, listOf(calendarId), fromDate, toDate, zoneId.id)
        fetchEventsResult.first.ifSuccessAndLogErrors(logger) { }

        if (fetchEventsResult.first is UseCase.Result.Success<*>) {
            if (fetchEventsResult.second == null) {
                logger.e("GetMinimalCalendarEventsUseCase: null event list when Sucess")
                return false
            }

            fetchEventsResult.second?.let {
                logger.v("GetMinimalCalendarEventsUseCase fetchEventsResult success: ${it.size}")
                calendarsRepository.persistEvents(*it.toTypedArray())
                updateAlarmsUseCase.execute(userId.id, it.map { it.id })
                return true
            }
        }

        return false
    }
}
