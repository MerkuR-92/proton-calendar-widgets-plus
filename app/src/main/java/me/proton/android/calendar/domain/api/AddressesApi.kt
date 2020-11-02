package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.AddressesApiResponse
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.core.domain.entity.UserId

interface AddressesApi {
    suspend fun getAddresses(userId: UserId): ApiResponse<AddressesApiResponse>
}
