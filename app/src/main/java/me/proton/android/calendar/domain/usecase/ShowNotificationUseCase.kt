package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.Logger

class ShowNotificationUseCase(private val logger: Logger) {

    suspend fun execute(eventAlarms: List<EventAlarmEntity>) {
        logger.v("executing ShowNotificationUseCase, showing: ${eventAlarms}")
    }

}
