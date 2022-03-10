package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*
import me.proton.core.domain.entity.UserId

interface ReportsApi {
    suspend fun sendReport(userId: UserId, body: ReportsApiRequest): ApiResponse<ReportsApiResponse>
}
