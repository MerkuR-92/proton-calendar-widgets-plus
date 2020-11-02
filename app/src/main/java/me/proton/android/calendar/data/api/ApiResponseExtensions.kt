package me.proton.android.calendar.data.api

import me.proton.core.network.domain.ApiResult

fun <T : Any> ApiResult<T>.toApiResponse(): ApiResponse<T> = when (this) {
    is ApiResult.Success -> ApiResponse.Success(this.value)
    is ApiResult.Error.Http -> {
        ApiResponse.Error(
            error = proton?.error ?: message,
            errorCode = proton?.code ?: 0,
            httpCode = httpCode
        )
    }
    is ApiResult.Error.Parse -> ApiResponse.Error(
        error = cause!!.message!!,
        errorCode = 0,
        httpCode = 0
    )
    is ApiResult.Error.Connection -> ApiResponse.Error(
        error = cause!!.message!!,
        errorCode = 0,
        httpCode = 0
    )
}