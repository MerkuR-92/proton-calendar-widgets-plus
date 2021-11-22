package me.proton.android.calendar.common.logger

import me.proton.android.calendar.BuildConfig
import me.proton.core.network.data.ProtonErrorException
import me.proton.core.util.kotlin.Logger
import me.proton.core.util.kotlin.LoggerLogTag
import org.jetbrains.annotations.NonNls
import timber.log.Timber

object LoggerImpl : Logger {

    private const val HTTP_ERROR_UNAUTHORIZED = 401
    private const val HTTP_ERROR_NOT_FOUND = 404
    private const val HTTP_ERROR_UNPROCESSABLE_ENTITY = 422

    private const val PROTON_ERROR_INVALID_REFRESH_TOKEN = 10013
    private const val PROTON_ERROR_INCORRECT_LOGIN_CREDENTIALS = 8002
    private const val PROTON_ERROR_PUBLIC_KEYS_ADDRESS_DOESNT_EXIST = 33102
    private const val PROTON_ERROR_FORCE_UPDATE = 5003

    private fun isLogNeeded(error: Throwable): Boolean {
        return when (error) {
            is ProtonErrorException -> when (error.response.code) {
                HTTP_ERROR_UNAUTHORIZED -> false
                HTTP_ERROR_NOT_FOUND -> false
                HTTP_ERROR_UNPROCESSABLE_ENTITY -> when (error.protonData.code) {
                    PROTON_ERROR_INVALID_REFRESH_TOKEN -> false
                    PROTON_ERROR_INCORRECT_LOGIN_CREDENTIALS -> false
                    PROTON_ERROR_PUBLIC_KEYS_ADDRESS_DOESNT_EXIST -> false
                    PROTON_ERROR_FORCE_UPDATE -> false
                    else -> true
                }
                else -> true
            }
            is java.net.SocketTimeoutException -> false
            is java.net.ConnectException -> false
            else -> true
        }
    }

    override fun e(tag: String, e: Throwable) =
        if (isLogNeeded(e)) Timber.tag(tag).e(e) else Unit

    override fun e(tag: String, e: Throwable, @NonNls message: String) =
        Timber.tag(tag).e(e, message)

    override fun i(tag: String, @NonNls message: String) =
        Timber.tag(tag).i(message)

    override fun i(tag: String, e: Throwable, message: String) =
        Timber.tag(tag).i(e, message)

    override fun d(tag: String, message: String) =
        Timber.tag(tag).d(message)

    override fun d(tag: String, e: Throwable, message: String) =
        Timber.tag(tag).d(e, message)

    override fun v(tag: String, message: String) =
        Timber.tag(tag).v(message)

    override fun v(tag: String, e: Throwable, message: String) =
        Timber.tag(tag).v(e, message)

    override fun log(tag: LoggerLogTag, message: String) = if (BuildConfig.DEBUG) {
        Timber.tag(tag.name).d(message)
    } else Unit
}
