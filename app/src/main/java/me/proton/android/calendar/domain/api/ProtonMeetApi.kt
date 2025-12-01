package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateMeetingApiRequest
import me.proton.android.calendar.data.api.CreateMeetingApiResponse
import me.proton.android.calendar.data.api.MeetingDetailsResponse
import me.proton.core.domain.entity.UserId

interface ProtonMeetApi {
    suspend fun getProtonMeetUrl(userId: UserId, body: CreateMeetingApiRequest): ApiResponse<CreateMeetingApiResponse>
    suspend fun getProtonMeetDetails(userId: UserId, meetingLinkName: String): ApiResponse<MeetingDetailsResponse>
}
