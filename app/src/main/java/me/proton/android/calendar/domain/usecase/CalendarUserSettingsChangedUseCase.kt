package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject

class CalendarUserSettingsChangedUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase
) : UseCase {

    suspend fun handlePrimaryTimezoneChange(userId: String): UseCase.Result {
        // get all all-day events
        val events = database.eventsDao().selectAllDayOnly()
        logger.v("all-day events: ${events.map { it.id }}")

        // 2. recalculate their alarms
        updateAlarmsUseCase.execute(userId, events.map { it.id })

        return UseCase.Result.Success<Unit>()
    }

}
