package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

interface KeysApiService : BaseRetrofitApi {

    @GET("keys")
    suspend fun getPublicKeys(@Query("Email") email: String): PublicKeysApiResponse

}

class KeysApiImpl @Inject constructor(private val apiProvider: ApiProvider) : KeysApi {

    override suspend fun getPublicKeys(userId: UserId, email: String): ApiResponse<PublicKeysApiResponse> =
        apiProvider.get<KeysApiService>(userId).invoke {
            getPublicKeys(email)
        }.toApiResponse()

}

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

