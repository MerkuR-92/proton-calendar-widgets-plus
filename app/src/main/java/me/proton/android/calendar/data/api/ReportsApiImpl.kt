package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.ReportsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.Body
import retrofit2.http.POST

interface ReportsApiService : BaseRetrofitApi {
    @POST("reports/bug")
    suspend fun sendReport(@Body body: ReportsApiRequest): ReportsApiResponse
}

class ReportsApiImpl(private val apiProvider: ApiProvider) : ReportsApi {

    override suspend fun sendReport(userId: UserId, body: ReportsApiRequest): ApiResponse<ReportsApiResponse> =
        apiProvider.get<ReportsApiService>(userId).invoke {
            sendReport(body)
        }.toApiResponse()

}

@Serializable
data class ReportsApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()

@Serializable
data class ReportsApiRequest(
    @SerialName("OS")
    val osName: String,
    @SerialName("OSVersion")
    val osVersion: String,
    @SerialName("Client")
    val client: String,
    @SerialName("ClientVersion")
    val appVersionName: String,
    @SerialName("Title")
    val title: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Username")
    val username: String,
    @SerialName("Email")
    val email: String
)
