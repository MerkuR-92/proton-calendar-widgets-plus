package me.proton.android.calendar.data.api

import com.google.gson.Gson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.AddressEntity
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.Logger
import retrofit2.Response
import retrofit2.http.GET

interface AddressesApiService {
    @GET("addresses")
    suspend fun getAddresses(): Response<AddressesApiResponse>
}

class AddressesApiImpl(private val service: AddressesApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), AddressesApi {

    override suspend fun getAddresses(): ApiResponse<AddressesApiResponse> =
        safeApiCall { service.getAddresses() }

}

@Serializable
data class AddressesApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Addresses")
    val addresses: List<AddressEntity>
) : BaseApiResponse()


