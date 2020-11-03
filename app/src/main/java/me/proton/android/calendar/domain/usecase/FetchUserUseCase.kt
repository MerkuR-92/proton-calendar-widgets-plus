package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.UsersApi
import me.proton.core.domain.entity.UserId

class FetchUserUseCase(
    private val usersApi: UsersApi,
    private val addressesApi: AddressesApi,
    private val usersRepository: UsersRepository
) : UseCase {

    suspend fun execute(userId: UserId): UseCase.Result {
        val userResponse = usersApi.getUser(userId)
        if (userResponse is ApiResponse.Success) {
            usersRepository.persistUser(userResponse.data.user)
        } else {
            return UseCase.Result.Error("user request failed: $userResponse")
        }

        val user = userResponse.data.user.toUser()
        user.primaryKey ?: return UseCase.Result.Error("user has no primary key")

        val addressesResponse = addressesApi.getAddresses(userId)
        if (addressesResponse is ApiResponse.Success) {
            addressesResponse.data.addresses.forEach {
                usersRepository.persistAddress(userId.id, it)
            }
        } else {
            return UseCase.Result.Error("addresses request failed: $addressesResponse")
        }

        return UseCase.Result.Success
    }
}
