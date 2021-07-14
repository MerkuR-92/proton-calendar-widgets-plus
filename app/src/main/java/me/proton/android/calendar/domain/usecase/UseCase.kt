package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.Logger

interface UseCase {
    sealed class Result {
        class Success<T>(val returnValue: T? = null) : Result()
        class InvalidParams(val message: String) : Result()
        class Error(val message: String, val error: UseCase.Error? = null) : Result()
    }

    enum class Error {
        /* BootstrapCalendarsUseCase */
        NO_CALENDAR,
        NO_ACTIVE_CALENDAR,
        RESET_NEEDED,
        UPDATE_PASSPHRASE,

        /* HandleSaveUseCase */
        EDIT_ERROR_SEND_MAIL,
        CREATE_ERROR_SEND_MAIL,
        USER_ADDRESS_INVALID_FOR_ENCRYPTION
    }
}

suspend fun UseCase.Result.ifSuccessAndLogErrors(logger: Logger, onSuccess: suspend () -> Unit) {
    when (this) {
        is UseCase.Result.Success<*> -> onSuccess.invoke()
        is UseCase.Result.InvalidParams -> logger.i("UseCase InvalidParams: ${this.message}")
        is UseCase.Result.Error -> logger.i("UseCase Error: ${this.message}")
    }
}
