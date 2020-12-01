package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.proton.android.calendar.domain.Logger
import kotlin.coroutines.CoroutineContext

interface UseCase {
    sealed class Result {
        object Success : Result()
        class InvalidParams(val message: String) : Result()
        class Error(val message: String) : Result()
    }
}

suspend fun UseCase.Result.ifSuccessAndLogErrors(logger: Logger, onSuccess: suspend () -> Unit) {
    when (this) {
        UseCase.Result.Success -> onSuccess.invoke()
        is UseCase.Result.InvalidParams -> logger.i("UseCase InvalidParams: ${this.message}")
        is UseCase.Result.Error -> logger.i("UseCase Error: ${this.message}")
    }
}
