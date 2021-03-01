package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.UsersApi
import me.proton.android.calendar.domain.model.Delinquent
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.core.domain.entity.UserId

class FetchUserUseCase(
    private val usersApi: UsersApi,
    private val addressesApi: AddressesApi,
    private val usersRepository: UsersRepository,
    private val logger: Logger
) : UseCase {

    suspend fun execute(userId: UserId): UseCase.Result {
        val userResponse = usersApi.getUser(userId)
        if (userResponse is ApiResponse.Success) {
            usersRepository.persistUser(userResponse.data.user)
        } else {
            return UseCase.Result.Error("FetchUserUseCase: user request failed: $userResponse")
        }

        val user = userResponse.data.user.toUser()

        // Limit users
        // User has a free account
        if (user.isFree) return UseCase.Result.Error("FetchUserUseCase: user is free", UseCase.Error.FREE_USER)
        // User's payment failed or expired
        if (user.delinquent >= Delinquent.UNPAID_DELINQUENT) return UseCase.Result.Error("FetchUserUseCase: user is delinquent", UseCase.Error.DELINQUENT_USER)
        // User reached storage quota: creation of event is disabled
        if (user.usedSpace >= user.maxSpace) return UseCase.Result.Error("FetchUserUseCase: user reached storage quota", UseCase.Error.STORAGE_QUOTA_REACHED)

        user.primaryKey ?: return UseCase.Result.Error("FetchUserUseCase: user has no primary key")

        val addressesResponse = addressesApi.getAddresses(userId)
        if (addressesResponse is ApiResponse.Success) {
            addressesResponse.data.addresses.forEach {
                usersRepository.persistAddress(userId.id, it)
            }
        } else {
            logger.e("FetchUserUseCase: addresses request failed: $addressesResponse")
            return UseCase.Result.Error("FetchUserUseCase: addresses request failed: $addressesResponse")
        }

        return UseCase.Result.Success<Unit>()
    }
}
