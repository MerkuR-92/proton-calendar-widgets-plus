package me.proton.android.calendar.data.api

import com.google.gson.Gson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.UserEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.UsersApi
import retrofit2.Response
import retrofit2.http.GET

interface UsersApiService {

    @GET("users")
    suspend fun getUser(): Response<UserApiResponse>

}

class UsersApiImpl(private val service: UsersApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger), UsersApi {

    override suspend fun getUser(): ApiResponse<UserApiResponse> = safeApiCall { service.getUser() }

}

@Serializable
data class UserApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("User")
    val user: UserEntity
) : BaseApiResponse()

