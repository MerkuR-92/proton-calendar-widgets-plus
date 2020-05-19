package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.AddressesApiResponse
import me.proton.android.calendar.data.api.ApiResponse

interface AddressesApi {
    suspend fun getAddresses(): ApiResponse<AddressesApiResponse>
}
