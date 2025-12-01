package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.ProtonMeetApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import javax.inject.Inject

interface ProtonMeetApiService : BaseRetrofitApi {

    @POST("api/meet/v1/meetings")
    suspend fun createNewMeeting(@Body body: CreateMeetingApiRequest): CreateMeetingApiResponse

    @GET("api/meet/v1/meetings/by-link/{meetingLinkName}")
    suspend fun retrieveMeetingDetails(
        @Path("meetingLinkName") meetingLinkName: String,
    ): MeetingDetailsResponse
}

class ProtonMeetApiImpl @Inject constructor(private val apiProvider: ApiProvider) : ProtonMeetApi {

    override suspend fun getProtonMeetUrl(userId: UserId, body: CreateMeetingApiRequest): ApiResponse<CreateMeetingApiResponse> =
        apiProvider.get<ProtonMeetApiService>(userId).invoke {
            createNewMeeting(body)
        }.toApiResponse()

    override suspend fun getProtonMeetDetails(
        userId: UserId,
        meetingLinkName: String
    ): ApiResponse<MeetingDetailsResponse> =
        apiProvider.get<ProtonMeetApiService>(userId).invoke {
            retrieveMeetingDetails(meetingLinkName)
        }.toApiResponse()
}

@Serializable
data class CreateMeetingApiRequest(
    @SerialName("Name") val name: String,
    @SerialName("Salt") val salt: String,
    @SerialName("SessionKey") val sessionKey: String,
    @SerialName("SRPModulusID") val srpModulusId: String,
    @SerialName("SRPSalt") val srpSalt: String,
    @SerialName("SRPVerifier") val srpVerifier: String,
    @SerialName("AddressID") val addressId: String,
    @SerialName("Password") val passwordArmored: String?,
    @SerialName("Type") val type: Int,
    @SerialName("StartTime") val startTime: String?,
    @SerialName("EndTime") val endTime: String?,
    @SerialName("RRule") val rrule: String?,
    @SerialName("Timezone") val timezone: String?,
    @SerialName("CustomPassword") val customPassword: Int,
    @SerialName("ProtonCalendar") val protonCalendar: Int,
)

@Serializable
data class CreateMeetingApiResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Meeting") val meeting: Meeting?,
) {
    @Serializable
    data class Meeting(
        @SerialName("ID") val id: String,
        @SerialName("MeetingLinkName") val meetingLinkName: String,
    )
}

@Serializable
data class MeetingDetailsResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Meeting") val meeting: Meeting?,
) {
    @Serializable
    data class Meeting(
        @SerialName("SessionKey") val sessionKey: String,
        @SerialName("Salt") val salt: String,
        @SerialName("Password") val passwordArmored: String?,
    )
}
