package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.PublicKeyEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.core.domain.entity.UserId

class FetchPublicKeysUseCase(
    private val logger: Logger,
    private val keysApi: KeysApi,
    private val database: AppDatabase
) : UseCase {

    private suspend fun fetchPublicKeys(userId: UserId, email: String): UseCase.Result {

        // TODO

        // 1. some emails have no public keys and will "always" fail to fetch them,
        //  try to optimize this somehow, at least put it in memory and don't try to fetch again for some time

        // 2. we need to refresh the cache once in a while, current logic will not add any new keys
        //  if we already have any in the database

        if (database.publicKeysDao().select(email).isEmpty()) {

            logger.v("fetching public keys for $email")

            val keysResponse = keysApi.getPublicKeys(userId, email)
            return if (keysResponse is ApiResponse.Success) {
                database.publicKeysDao().insert(*keysResponse.data.keys.map { PublicKeyEntity(email, it.flags, it.publicKey) }.toTypedArray())
                logger.v("persisted public keys for $email -> ${keysResponse.data}")
                UseCase.Result.Success<Unit>()
            } else {
                UseCase.Result.Error("error fetching public keys for email: ")
            }

        }

        return UseCase.Result.Success<Unit>()

    }

    suspend fun execute(userId: UserId, eventEntities: List<EventEntity>): UseCase.Result {

        val emails = eventEntities.flatMap {
            try {
                it.sharedEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content } +
                it.calendarEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content } +
                it.personalEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content }
            } catch (e: IllegalStateException) {
                logger.e("error getting event's author from JSON", e)
                emptyList()
            }
        }.filterNotNull()

        coroutineScope {
            val requests = emails.distinct().map {
                async {
                    fetchPublicKeys(userId, it)
                }
            }

            requests.awaitAll()
        }

        return UseCase.Result.Success<Unit>()

    }

}
