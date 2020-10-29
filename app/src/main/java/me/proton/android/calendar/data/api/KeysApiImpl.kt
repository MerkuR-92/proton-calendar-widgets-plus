package me.proton.android.calendar.data.api

import com.google.gson.Gson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    @GET("keys")
    suspend fun getPublicKeys(@Query("Email") email: String): Response<PublicKeysApiResponse>

}

class KeysApiImpl(private val service: KeysApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), KeysApi {

    override suspend fun getKeySalts(): ApiResponse<KeySaltsApiResponse> = safeApiCall { service.getKeySalts() }
    override suspend fun getPublicKeys(email: String): ApiResponse<PublicKeysApiResponse> = safeApiCall { service.getPublicKeys(email) }

}

@Serializable
data class KeySaltsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("KeySalts")
    val keySalts: List<KeySalt>
) : BaseApiResponse()

@Serializable
data class KeySalt(
    @SerialName("ID")
    val id: String, // this ID is actually the ID of PrivateKey this Salt corresponds to
    @SerialName("KeySalt")
    val keySalt: String
)

@Serializable
data class PublicKeysApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Keys")
    val keys: List<PublicKeyApiEntity>
) : BaseApiResponse()

@Serializable
data class PublicKeyApiEntity(
    @SerialName("Flags")
    val flags: Int,
    @SerialName("PublicKey")
    val publicKey: String
)

