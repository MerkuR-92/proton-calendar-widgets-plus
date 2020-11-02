package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET

interface AddressesApiService : BaseRetrofitApi {
    @GET("addresses")
    suspend fun getAddresses(): AddressesApiResponse
}

class AddressesApiImpl(private val apiProvider: ApiProvider) : AddressesApi {

    override suspend fun getAddresses(userId: UserId): ApiResponse<AddressesApiResponse> =
        apiProvider.get<AddressesApiService>(userId).invoke {
            getAddresses()
        }.toApiResponse()

}

@Serializable
data class AddressesApiResponse(
    @SerialName("Addresses")
    val addresses: List<AddressEntity>
)
