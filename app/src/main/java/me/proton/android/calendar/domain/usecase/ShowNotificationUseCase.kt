package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.Logger

class ShowNotificationUseCase(private val logger: Logger) {

    suspend fun execute() {
        logger.v("executing ShowNotificationUseCase")
    }

}
