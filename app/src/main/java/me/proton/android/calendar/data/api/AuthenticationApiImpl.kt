package me.proton.android.calendar.data.api

import com.google.gson.Gson
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

    //@POST("auth")
    //    suspend fun postLogin(@Body body: LoginBody): Response<LoginResponse>

    @POST("auth")
//    @Headers(RetrofitConstants.CONTENT_TYPE, RetrofitConstants.ACCEPT_HEADER_V1)
    suspend fun login(@Body requestBody: RequestBody): Response<LoginApiResponse>

//    @GET("auth/modulus")
//    @Headers(RetrofitConstants.CONTENT_TYPE, RetrofitConstants.ACCEPT_HEADER_V1)
//    fun randomModulus(): Call<ModulusResponse>
//
//    @Headers(RetrofitConstants.CONTENT_TYPE, RetrofitConstants.ACCEPT_HEADER_V1)
@POST("auth/info")
suspend fun fetchLoginInfo(@Body requestBody: RequestBody): Response<LoginInfoApiResponse>
//
//    @POST("auth/2fa")
//    @Headers(RetrofitConstants.CONTENT_TYPE, RetrofitConstants.ACCEPT_HEADER_V1)
//    fun post2fa(@Body twofaBody: TwoFABody, @Tag retrofitTag: RetrofitTag? = null): Call<TwoFAResponse>
//
    @POST("auth/refresh")
    suspend fun refreshAccessToken(@Body body: RefreshAccessTokenApiBody): Response<RefreshAccessTokenApiResponse>

    // TODO POST /auth

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

//@JsonProperty("Username") private String username;
//
//    public LoginInfoBody(final String username) {
//        this.username = username;
//    }

data class LoginInfoApiBody(
    val username: String
) {
    fun toRetrofitRequestBody(): RequestBody {
        return """{"Username": "$username"}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }
}

data class LoginInfoApiResponse(
    override val code: Int,
    val modulus: String,
    val serverEphemeral: String,
    val version: Int,
    val salt: String,
    val srpSession: String
): BaseApiResponse()


/*
@JsonProperty("Username") private String username;

    public LoginBody(final String username, final String srpSession, final String clientEphemeral,
                     final String clientProof, final String twoFactorCode) {
        super(srpSession, clientEphemeral, clientProof, twoFactorCode);
        this.username = username;
    }

    @Override
    public JsonObject toJson() {
        String jsonString = new GsonBuilder().create().toJson(this, LoginBody.class);
        JsonParser parser = new JsonParser();
        return parser.parse(jsonString).getAsJsonObject();
    }

 */

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

//data class LoginApiResponse(
//    override val code: Int,
//    val modulus: String,
//    val serverEphemeral: String,
//    val version: Int,
//    val salt: String,
//    val srpSession: String
//): BaseApiResponse()


data class LoginApiResponse(
    override val code: Int,
    val accessToken: String,
    val uid: String,
    val userId: String,
    val refreshToken: String,
    val eventID: String

    // TODO password mode, 2FA etc.


//    "ExpiresIn": 360000,
//    "TokenType": "Bearer",
//"Scope": "full other_scopes",
//"RefreshToken": "b894b4c4f20003f12d486900d8b88c7d68e67235"
): BaseApiResponse()

// {
//  "Code": 1000,
//  "AccessToken": "1999df52074e6a920b141836224cfaab764061e5",
//  "ExpiresIn": 864000,
//  "TokenType": "Bearer",
//  "Scope": "full self organization payments keys paid nondelinquent mail calendar",
//  "Uid": "8f18ca50bc64eef59991cc655a323835dedfe4c1",
//  "UID": "8f18ca50bc64eef59991cc655a323835dedfe4c1",
//  "UserID": "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==",
//  "RefreshToken": "81ae3b232e2cb8663bbf80629be6f524fd9bc3f3",
//  "EventID": "x7rVTLJ7CAMKkfwvAS2FmaVsZ2Bm0F1OlOjSO2lwSTdFBySRDMmxNOJMfRYGr1z4nFidAuWJV8ZwB78cy5QTAw==",
//  "PasswordMode": 1,
//  "ServerProof": "ctIOtnwVpSPCVaD1e+UG8tm6s1S6mZ8IR8yImQz46VJhhLqHt5lEAiBH4Iqqbv0S3Nt68bl60o74DH0TW23y2nk0fV4oDqeD33zV3sZ+/rr4RglwGIWZyzsET4jJgNpUCRacbxH+R1AYItsWhJ7Rz8fRKbodKDjeekfNK2bXVvLPjTLwFurPOV7cbVfB3+EeR3kfvlxK3tDNX1iEPC7mBoefyXDkDYBJAwVN5mkZm8bLQJVC0iY4M6z/NAdESorg6vzSENMSGOJs03SKtKBR0sKqgHsQUEXWnWVIYl55+KvD11fvhi0QtEfUNwQ09uYnmcpFNV9zOJD/lDTF+jO+4Q==",
//  "TwoFactor": 0,
//  "2FA": {
//    "Enabled": 0,
//    "U2F": null,
//    "TOTP": 0
//  }
//}