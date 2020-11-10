package me.proton.android.calendar.data.api

import me.proton.android.calendar.common.TimberLogger
import me.proton.core.network.domain.ApiResult

fun <T : Any> ApiResult<T>.toApiResponse(): ApiResponse<T> = when (this) {

    is ApiResult.Success -> ApiResponse.Success(this.value)

    is ApiResult.Error.Http -> {

        proton?.let {
            TimberLogger.i("ApiResult.Error.Http (${httpCode}) ${it.code}: ${it.error}")
        }

        ApiResponse.Error(
            error = proton?.error ?: message,
            errorCode = proton?.code ?: 0,
            httpCode = httpCode
        )
    }

    is ApiResult.Error.Parse -> {

        if (cause == null) {
            TimberLogger.e("ApiResult.Error.Parse without cause")
        }

        cause?.let {
            TimberLogger.e("ApiResult.Error.Parse", it)
        }

        ApiResponse.Error(
            error = cause?.message ?: "no message in ApiResult.Error.Parse",
            errorCode = 0,
            httpCode = 0
        )
    }

    is ApiResult.Error.Connection -> {

        if (cause == null) {
            TimberLogger.e("ApiResult.Error.Connection without cause")
        }

        cause?.let {
            TimberLogger.e("ApiResult.Error.Connection", it)
        }

        ApiResponse.Error(
            error = cause?.message ?: "no message in ApiResult.Error.Connection",
            errorCode = 0,
            httpCode = 0
        )
    }
}
