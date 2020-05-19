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
            header("x-pm-apiversion", "3")
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

        /*return if (originalRequest.url.encodedPath in noAuthPaths) {
            requestBuilder.apply {
                removeHeader("x-pm-uid")
                removeHeader("Authorization")
            }

            chain.proceed(requestBuilder.build())
        } else {
//            val userId = chain.request().tag(RetrofitTag::class.java)?.userId ?: return buildExceptionResponse("no RetrofitTag in RequestInterceptor", originalRequest)
            val userId = "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="
            val valueStore = valueStoreProvider.provideValueStore(userId)

            requestBuilder.apply {
                header("x-pm-uid", "281dabe17d8797f367ad2cc5a66deb32e9b571b")
//                header("x-pm-uid", valueStore.getString(ValueKey.AUTH_UID) ?: return buildExceptionResponse("no UID in RequestInterceptor", originalRequest))
//                header("Authorization", "Bearer ${valueStore.getString(ValueKey.AUTH_ACCESS_TOKEN) ?: return buildExceptionResponse("no AccessToken in RequestInterceptor", originalRequest)}")
                // TODO don't use cookies
                header("Cookie", "AUTH-2f19f86ade02a6f59ebf6839f11eb8b6f07ec8ac=%7B%22AccessToken%22%3A%22278a68117d0e12a0c0fca4457472a2e7bc06a1b6%22%2C%22UID%22%3A%222f19f86ade02a6f59ebf6839f11eb8b6f07ec8ac%22%7D; AUTH-2a2263de93bf279e6c8a38f0b904b79969fac9df=%7B%22AccessToken%22%3A%224012329d474c8d76f8ffc1aa0476e9a2cd3116ba%22%2C%22UID%22%3A%222a2263de93bf279e6c8a38f0b904b79969fac9df%22%7D")
            }

            val response = chain.proceed(requestBuilder.build())

            when (response.code) {
                401 -> {
                    if (response.request.url.encodedPath == REFRESH_TOKEN_PATH) { // refresh access token request failed, force logout
                        Timber.e("refresh token request failed, force logout")

                    } else { // try to refresh access token
                        Timber.e("trying to refresh access token")

                        //val accessToken = refreshAccessToken(valueStore.getString(ValueKey.AUTH_REFRESH_TOKEN)!!, valueStore.getString(ValueKey.AUTH_UID)!!) // TODO
                        Timber.e("refreshed access token = ")
                        // TODO save credentials and retry
                    }
                    //if (authenticated) then need to re-auth
                    //            val refreshResponse = CoroutineScope(Dispatchers.IO).launch {
//                 authenticationApi.refreshAccessToken("")
//            }
                }
                // TODO other cases
            }

//            Timber.e("interceptor response code = ${response.code}")
            response
        }*/

//        val authenticated = false // TODO SET IF WE SUCCESSFULLY AUTHENTICATED THIS REQUEST

        // execute request with all headers added






//            if (refreshResponse.isSuccessful) {
        // persist new accessToken

        // retry the original request
//                val builder: Request.Builder =
//                    chain.request().newBuilder().header("Authorization", session.getToken())
//                        .method(chain.request().method(), chain.request().body()) ??????????
//                response = chain.proceed(builder.build())
//            } else {
        // TODO if it was network error, 429 or others, just fail the original request

        // TODO if it was 401/403, it means the credentials are invalid, return response to logout user
//            }
//        }
        // APPLICATION INTERCEPTOR:
        // Permitted to short-circuit and not call Chain.proceed().
        //Permitted to retry and make multiple calls to Chain.proceed().

//        return response
    }








    private fun refreshAccessToken(refreshToken: String, uid: String) : String? {

//        Content-Type: application/json;charset=utf-8
//        x-pm-uid: {session_uid}
//        x-pm-appversion: {app_version} or Other
//        x-pm-apiversion: 3
//        Accept: application/vnd.protonmail.v1+json

        val refreshPayload = RefreshAccessTokenApiBody(refreshToken)

        val refreshRequest = Request.Builder()
//            .url("".toString() + "/users/detail")
//            .post(body)
//            .build()
            .header("x-pm-appversion", headerAppName) // TODO
            .header("x-pm-locale", headerLocale)
            .header("x-pm-apiversion", "3")
            .header("x-pm-uid", uid)


            .url("https://protonmail.blue" + REFRESH_TOKEN_PATH)
            .post(refreshPayload.toJson().toRequestBody("application/json".toMediaType()))
            .build()




//        val response = okHttpClient.newCall(refreshRequest).execute()
//        return if (response.isSuccessful) {
//            response.body.toString()
//        } else {
//            null
//        }

        return null

        /* String postBody = "test post";

         val json = "{\"id\":1,\"name\":\"John\"}"



         val request: Request = Builder()
             .url(BASE_URL.toString() + "/users/detail")
             .post(body)
             .build()

         Request request = new Request.Builder()
             .url(URL_SECURED_BY_BASIC_AUTHENTICATION)
             .addHeader("Authorization", Credentials.basic("username", "password"))
             .post(RequestBody.create(
                 MediaType.parse("text/x-markdown), postBody))
                     .build();

         val call: Call = client.newCall(request)
         val response: Response = call.execute()
 */
        // {
        //  "ResponseType": "token",
        //  "GrantType": "refresh_token",
        //  "RefreshToken": "eaad5a7059835aac32c0bf99c2e208a59b8c1a55",
        //  "RedirectURI": "http://protonmail.ch"
        //}

    }


}