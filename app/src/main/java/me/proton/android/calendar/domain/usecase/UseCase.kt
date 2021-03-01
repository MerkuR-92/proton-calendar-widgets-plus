package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.Logger

interface UseCase {
    sealed class Result {
        class Success<T>(val returnValue: T? = null) : Result()
        class InvalidParams(val message: String) : Result()
        class Error(val message: String, val error: UseCase.Error? = null) : Result()
    }

    enum class Error {
        FREE_USER,
        DELINQUENT_USER,
        STORAGE_QUOTA_REACHED,
        NO_CALENDAR,
        NO_ACTIVE_CALENDAR,
        RESET_NEEDED,
        UPDATE_PASSPHRASE
    }
}

suspend fun UseCase.Result.ifSuccessAndLogErrors(logger: Logger, onSuccess: suspend () -> Unit) {
    when (this) {
        is UseCase.Result.Success<*> -> onSuccess.invoke()
        is UseCase.Result.InvalidParams -> logger.i("UseCase InvalidParams: ${this.message}")
        is UseCase.Result.Error -> logger.i("UseCase Error: ${this.message}")
    }
}
