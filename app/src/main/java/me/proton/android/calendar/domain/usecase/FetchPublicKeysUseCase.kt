package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.PublicKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.KeysApi

class FetchPublicKeysUseCase(
    private val logger: Logger,
    private val keysApi: KeysApi,
    private val database: AppDatabase

) : UseCase {

    suspend fun execute(email: String): UseCase.Result {

        // TODO verify this is working
        logger.v("executing FetchPublicKeysUseCase for $email")

        if (database.publicKeysDao().select(email).isEmpty()) {

            val keysResponse = keysApi.getPublicKeys(email)
            return if (keysResponse is ApiResponse.Success) {
                database.publicKeysDao().insert(*keysResponse.data.keys.map { PublicKeyEntity(email, it.flags, it.publicKey) }.toTypedArray())
                logger.v("persisted keys for $email -> ${keysResponse.data}")
                UseCase.Result.Success
            } else {
                UseCase.Result.Error("error fetching public keys for email: ")
            }

        }

        return UseCase.Result.Success

    }

}
