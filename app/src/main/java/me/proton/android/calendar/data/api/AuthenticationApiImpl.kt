package me.proton.android.calendar.data.api

import com.google.gson.Gson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.AuthenticationApi
import me.proton.android.calendar.domain.Logger
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthenticationApiService {

    @POST("auth")
    suspend fun login(@Body requestBody: RequestBody): Response<LoginApiResponse>

    @POST("auth/info")
    suspend fun fetchLoginInfo(@Body requestBody: RequestBody): Response<LoginInfoApiResponse>

    @POST("auth/refresh")
    suspend fun refreshAccessToken(@Body body: RefreshAccessTokenApiBody): Response<RefreshAccessTokenApiResponse>

}

class AuthenticationApiImpl(private val service: AuthenticationApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), AuthenticationApi {

    @Deprecated("needs fixing")
    override suspend fun refreshAccessToken(refreshToken: String): ApiResponse<RefreshAccessTokenApiResponse> = safeApiCall {
        service.refreshAccessToken(RefreshAccessTokenApiBody(refreshToken)) // TODO FIX THIS
    }

    override suspend fun fetchLoginInfo(username: String): ApiResponse<LoginInfoApiResponse> = safeApiCall {
        service.fetchLoginInfo(LoginInfoApiBody(username).toRetrofitRequestBody())
    }

    override suspend fun login(username: String, srpSession: String, clientEphemeral: String, clientProof: String): ApiResponse<LoginApiResponse> = safeApiCall {
        service.login(LoginApiBody(username, srpSession, clientEphemeral, clientProof).toRetrofitRequestBody())
    }

}

data class RefreshAccessTokenApiBody(
    val refreshToken: String
) {
    fun toJson(): String {
        return """{
            "RefreshToken": "$refreshToken",
            "ResponseType": "token",
            "GrantType": "refresh_token",
            "RedirectUri": "http://protonmail.ch"
        }""".trimIndent()
    }
}

data class RefreshAccessTokenApiResponse(
    override val code: Int,
    val accessToken: String
//    "ExpiresIn": 360000,
//    "TokenType": "Bearer",
//"Scope": "full other_scopes",
//"RefreshToken": "b894b4c4f20003f12d486900d8b88c7d68e67235"
): BaseApiResponse()

data class LoginInfoApiBody(
    val username: String
) {
    fun toRetrofitRequestBody(): RequestBody {
        return """{"Username": "$username"}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }
}

@Serializable
data class LoginInfoApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Modulus")
    val modulus: String,
    @SerialName("ServerEphemeral")
    val serverEphemeral: String,
    @SerialName("Version")
    val version: Int,
    @SerialName("Salt")
    val salt: String,
    @SerialName("SRPSession")
    val srpSession: String
): BaseApiResponse()

data class LoginApiBody(
    val username: String,
    val srpSession: String,
    val clientEphemeral: String,
    val clientProof: String,
    val twoFactorCode: String? = null

) {
    fun toRetrofitRequestBody(): RequestBody {
        return """{
            "Username": "$username",
            "SRPSession": "$srpSession",
            "ClientEphemeral": "$clientEphemeral",
            "ClientProof": "$clientProof",
            "TwoFactorCode": "${twoFactorCode ?: ""}"
        }""".trimIndent().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }
}

@Serializable
data class LoginApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("AccessToken")
    val accessToken: String,
    @SerialName("UID")
    val uid: String,
    @SerialName("UserID")
    val userId: String,
    @SerialName("RefreshToken")
    val refreshToken: String,
    @SerialName("EventID")
    val eventID: String
): BaseApiResponse()
