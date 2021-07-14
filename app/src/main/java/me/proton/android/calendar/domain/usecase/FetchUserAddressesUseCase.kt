package me.proton.android.calendar.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager

class FetchUserAddressesUseCase(
    private val userManager: UserManager
) : UseCase {

    companion object {
        const val WORKER_ID = "FETCH_ADDRESSES"
    }

    // Fetch addresses
    // TODO: Only rely on Events Loop.
    suspend fun executeGetAddresses(userId: UserId): UseCase.Result {
        return runCatching {
            userManager.getAddresses(userId, refresh = true)
            UseCase.Result.Success<Unit>()
        }.getOrElse {
            UseCase.Result.Error("FetchUserUseCase: addresses request failed.")
        }
    }
}
