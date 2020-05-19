package me.proton.android.calendar.domain.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.KeySaltsApiResponse
import me.proton.android.calendar.data.api.PublicKeysApiResponse
import me.proton.android.calendar.data.api.UserApiResponse
import retrofit2.Response

interface KeysApi {
    suspend fun getKeySalts(): ApiResponse<KeySaltsApiResponse>
    suspend fun getPublicKeys(email: String): ApiResponse<PublicKeysApiResponse>
}
