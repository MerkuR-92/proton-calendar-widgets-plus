package me.proton.android.calendar.domain.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.Delinquent

class FetchUserUseCase(
    private val userManager: UserManager
) : UseCase {

    companion object {
        const val WORKER_ID = "FETCH_ADDRESSES"
    }

    // TODO: Extends DefaultUserCheck and add user.usedSpace >= user.maxSpace.
    suspend fun executeFetchUserAndAddresses(userId: UserId): UseCase.Result {
        val user = userManager.getUser(userId)

        // Limit users
        // User's payment failed or expired
        when (user.delinquent) {
            Delinquent.InvoiceDelinquent,
            Delinquent.InvoiceMailDisabled -> return UseCase.Result.Error(
                "FetchUserUseCase: user is delinquent",
                UseCase.Error.DELINQUENT_USER
            )
            else -> Unit
        }
        // User reached storage quota: creation of event is disabled
        if (user.usedSpace >= user.maxSpace) return UseCase.Result.Error(
            "FetchUserUseCase: user reached storage quota",
            UseCase.Error.STORAGE_QUOTA_REACHED
        )

        user.keys.primary() ?: return UseCase.Result.Error("FetchUserUseCase: user has no primary key")

        return UseCase.Result.Success<Unit>()
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
