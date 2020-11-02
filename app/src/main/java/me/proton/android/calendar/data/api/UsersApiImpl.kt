package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.UserEntity
import me.proton.android.calendar.domain.api.UsersApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET

interface UsersApiService: BaseRetrofitApi {

    @GET("users")
    suspend fun getUser(): UserApiResponse

}

class UsersApiImpl(private val apiProvider: ApiProvider) : UsersApi {

    override suspend fun getUser(userId: UserId): ApiResponse<UserApiResponse> =
        apiProvider.get<UsersApiService>(userId).invoke {
            getUser()
        }.toApiResponse()

}

@Serializable
data class UserApiResponse(
    @SerialName("User")
    val user: UserEntity
)
