package me.proton.android.calendar.data.api

import com.google.gson.Gson
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

data class UserApiResponse(
    override val code: Int,
    val user: UserEntity
) : BaseApiResponse()

