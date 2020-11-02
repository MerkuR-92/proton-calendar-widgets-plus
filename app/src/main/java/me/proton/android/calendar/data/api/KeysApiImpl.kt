package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET
import retrofit2.http.Query

interface KeysApiService : BaseRetrofitApi {

    /**
     * In order to get key salts, we need "locked" scope in our AccessToken,
     *  which expires few minutes after login (POST /auth) .
     */
    @GET("keys/salts")
    suspend fun getKeySalts(): KeySaltsApiResponse

    @GET("keys")
    suspend fun getPublicKeys(@Query("Email") email: String): PublicKeysApiResponse

}

class KeysApiImpl(private val apiProvider: ApiProvider) : KeysApi {

    override suspend fun getKeySalts(userId: UserId): ApiResponse<KeySaltsApiResponse> =
        apiProvider.get<KeysApiService>(userId).invoke {
            getKeySalts()
        }.toApiResponse()

    override suspend fun getPublicKeys(userId: UserId, email: String): ApiResponse<PublicKeysApiResponse> =
        apiProvider.get<KeysApiService>(userId).invoke {
            getPublicKeys(email)
        }.toApiResponse()

}

@Serializable
data class KeySaltsApiResponse(
    @SerialName("KeySalts")
    val keySalts: List<KeySalt>
)

@Serializable
data class KeySalt(
    @SerialName("ID")
    val id: String, // this ID is actually the ID of PrivateKey this Salt corresponds to
    @SerialName("KeySalt")
    val keySalt: String
)

@Serializable
data class PublicKeysApiResponse(
    @SerialName("Keys")
    val keys: List<PublicKeyApiEntity>
)

@Serializable
data class PublicKeyApiEntity(
    @SerialName("Flags")
    val flags: Int,
    @SerialName("PublicKey")
    val publicKey: String
)

