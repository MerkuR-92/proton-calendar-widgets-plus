package me.proton.android.calendar.domain.usecase

interface UseCase {
    sealed class Result {
        object Success : Result()
        class InvalidParams(val message: String) : Result()
        class Error(val message: String) : Result()
    }
}