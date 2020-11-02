package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.LatestServerEventApiResponse
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.core.domain.entity.UserId

interface ServerEventsApi {
    /**
     * Gets latest event ID, only use with empty cache to bootstrap event stream.
     */
    suspend fun getLatestServerEvent(userId: UserId): ApiResponse<LatestServerEventApiResponse> // TODO remove because we don't need to use it

    /**
     * Gets events since last event ID.
     */
    suspend fun getServerEvents(userId: UserId, sinceServerEventId: String): ApiResponse<ServerEventsApiResponse>
}
