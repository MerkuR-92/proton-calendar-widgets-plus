package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.repository.PublicAddressRepository
import me.proton.core.key.domain.repository.Source

/**
 * Forces fetching and caching Public Keys for Authors of Calendar Parts.
 */
class FetchPublicKeysUseCase(
    private val logger: Logger,
    private val publicAddressRepository: PublicAddressRepository
) : UseCase {

    private suspend fun fetchPublicKeys(userId: UserId, email: String): UseCase.Result {

        val publicAddress = kotlin.runCatching {
            publicAddressRepository.getPublicAddress(userId, email, source = Source.RemoteNoCache)
        }.getOrNull()

        return if (publicAddress != null) {
            UseCase.Result.Success<Unit>()
        } else {
            UseCase.Result.Error("error fetching public keys")
        }
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
