package me.proton.android.calendar.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import me.proton.android.calendar.domain.Logger
import retrofit2.Response

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
abstract class BaseApiResponse {
    abstract val code: Int

    val error: String? = null
    val errorDescription: String? = null

    val isSuccessful: Boolean get() = code == 1000 || code == 1001 // single- and multiple-success
}

class StatusCodeApiResponse(override val code: Int) : BaseApiResponse()

/**
 * Base implementation of Retrofit Service.
 */
abstract class BaseApi(private val gson: Gson, private val logger: Logger) {

    protected suspend fun <T : Any> safeApiCall(call: suspend () -> Response<T>): ApiResponse<T> = try {
        val response = call.invoke()
        if (response.isSuccessful) {
            ApiResponse.Success(response.body()!!)
        } else {
            val errorApiResponse: ErrorApiResponse = gson.fromJson(response.errorBody()!!.charStream(), errorApiResponseBodyType)
            logger.i("Error in Api request [${response.code()}, ${errorApiResponse.code}, ${errorApiResponse.error}]")
            ApiResponse.Error(response.code(), errorApiResponse.code, errorApiResponse.error)
        }
    } catch (e: Exception) {
        logger.i("Exception in Api request", e)
        ApiResponse.Exception(e)
    }

    private data class ErrorApiResponse(val code: Int, val error: String)

    private val errorApiResponseBodyType = object : TypeToken<ErrorApiResponse>() {}.type
}

