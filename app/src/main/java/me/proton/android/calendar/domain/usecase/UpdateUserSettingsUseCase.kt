package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId

class UpdateUserSettingsUseCase(
    private val logger: Logger,
    private val settingsApi: SettingsApi,
    private val usersRepository: UsersRepository
): UseCase {

    companion object {
        const val WORKER_ID_TIME_FORMAT = "WORKER_ID_TIME_FORMAT"
    }

    suspend fun executeTimeFormat(userId: UserId, timeFormat: Int): UseCase.Result {
        return when (val updateUserTimeFormatResponse =
            settingsApi.updateUserTimeFormat(userId, timeFormat)
        ) {
            is ApiResponse.Success -> {
                usersRepository.persistUserSettings(userId.id, updateUserTimeFormatResponse.data.userSettings)
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                logger.e("api error updating user time format: $updateUserTimeFormatResponse")
                UseCase.Result.Error(updateUserTimeFormatResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating user time format: $updateUserTimeFormatResponse")
                UseCase.Result.Error(updateUserTimeFormatResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
