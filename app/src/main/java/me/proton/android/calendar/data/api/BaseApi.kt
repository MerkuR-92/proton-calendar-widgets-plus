package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.Logger

/**
 * Wrapper for calling Retrofit in a safe way.
 */
sealed class ApiResponse<out T : Any> {
    data class Success<out T : Any>(val data: T) : ApiResponse<T>()
    data class Error(val httpCode: Int, val errorCode: Int, val error: String) : ApiResponse<Nothing>()
    data class Exception(val exception: kotlin.Exception) : ApiResponse<Nothing>()
}

/**
 * Base class for all responses from API.
 */
@Serializable
abstract class BaseApiResponse {
    @SerialName("Code")
    abstract val code: Int

    @SerialName("Error")
    val error: String? = null

    @SerialName("ErrorDescription")
    val errorDescription: String? = null

    val isSuccessful: Boolean get() = code == 1000 || code == 1001 // single- and multiple-success
}

class StatusCodeApiResponse(override val code: Int) : BaseApiResponse()

/**
 * @return response object or `null` and logs errors, if any
 */
fun <T : Any> ApiResponse<T>.valueOrNullAndLogErrors(logger: Logger, tag: String? = null): T? = when (this) {
    is ApiResponse.Error -> {
        logger.e("${if (tag != null) { "[$tag] " } else ""}ApiResponse Error: ${this.httpCode}, ${this.errorCode}, ${this.error}")
        null
    }
    is ApiResponse.Exception -> {
        logger.e("${if (tag != null) { "[$tag] " } else ""}ApiResponse Exception", this.exception)
        null
    }
    is ApiResponse.Success -> this.data
}
