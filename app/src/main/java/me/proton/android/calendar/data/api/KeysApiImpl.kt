package me.proton.android.calendar.data.api

import com.google.gson.Gson
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.android.calendar.domain.Logger
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface KeysApiService {

    /**
     * In order to get key salts, we need "locked" scope in our AccessToken,
     *  which expires few minutes after login (POST /auth) .
     */
    @GET("keys/salts")
    suspend fun getKeySalts(): Response<KeySaltsApiResponse>

    // TODO test response is for adamtst@protonmail.blue
    @GET("keys")
    fun getPublicKeys(@Query("Email") email: String): Response<PublicKeysApiResponse>

}

class KeysApiImpl(private val service: KeysApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), KeysApi {

    override suspend fun getKeySalts(): ApiResponse<KeySaltsApiResponse> = safeApiCall { service.getKeySalts() }
    override suspend fun getPublicKeys(email: String): ApiResponse<PublicKeysApiResponse> = safeApiCall { service.getPublicKeys(email) }

}

data class KeySaltsApiResponse(
    override val code: Int,
    val keySalts: List<KeySalt>
) : BaseApiResponse()

data class KeySalt(
    val id: String, // this ID is actually the ID of PrivateKey this Salt corresponds to
    val keySalt: String
)

data class PublicKeysApiResponse(
    override val code: Int,
    val keys: List<PublicKeyApiEntity>
) : BaseApiResponse()

data class PublicKeyApiEntity(
    val flags: Int,
    val publicKey: String
)

