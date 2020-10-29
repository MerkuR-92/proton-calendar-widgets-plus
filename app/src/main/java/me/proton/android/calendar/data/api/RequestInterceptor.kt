package me.proton.android.calendar.data.api

import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.API_APPLICATION_NAME
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.LoginUserUseCase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber


class RequestInterceptor(private val valueStoreProvider: ValueStoreProvider, androidUtils: AndroidUtils) : Interceptor {

    private val REFRESH_TOKEN_PATH = "/api/auth/refresh"
    private val noAuthPaths = listOf(
//        REFRESH_TOKEN_PATH,
        "/api/auth",
        "/api/auth/info"
    )

    private val headerAppName = "${API_APPLICATION_NAME}_${BuildConfig.VERSION_NAME}"
    private val headerLocale = androidUtils.locale

    private fun buildExceptionResponse(message: String, originalRequest: Request) =
        Response.Builder()
            .code(418)
            .protocol(Protocol.HTTP_2)
            .message(message)
            .request(originalRequest)
            .body("{Code: 418, Error: \"$message\"}".toResponseBody("application/json".toMediaType()))
            .build()

    override fun intercept(chain: Interceptor.Chain): Response {

        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        Timber.v("INTERCEPT called: ${originalRequest.url.encodedPath}")

        // set constant headers
        requestBuilder.apply {
//            header("Accept", "application/vnd.protonmail.v1+json")
            //header("Accept", "application/vnd.protonmail.v1+json") // TODO not always true
            //header("Content-Type", "application/json;charset=utf-8") // TODO not always true
            header("x-pm-appversion", headerAppName)
            header("x-pm-locale", headerLocale)
        }

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")

        if (TODOvalueStore.getString("USERID") != null && !noAuthPaths.contains(originalRequest.url.encodedPath)) {

            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
            requestBuilder.apply {
                header("x-pm-uid", valueStore.getString(ValueKey.AUTH_UID)!!)
                header("Authorization", "Bearer ${valueStore.getString(ValueKey.AUTH_ACCESS_TOKEN)}")
            }

        }

        return chain.proceed(requestBuilder.build())

    }

}
