package me.proton.android.calendar.domain.usecase

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.repository.PublicAddressRepository
import me.proton.core.key.domain.repository.Source
import me.proton.core.util.kotlin.mapAsync
import javax.inject.Inject

/**
 * Forces fetching and caching Public Keys for Authors of Calendar Parts.
 */
class FetchPublicKeysUseCase @Inject constructor(
    private val logger: Logger,
    private val publicAddressRepository: PublicAddressRepository,
    @ApplicationContext private val context: Context,
) : UseCase {

    private suspend fun fetchPublicKeys(userId: UserId, email: String): UseCase.Result {
        // TODO returning right away, not to use the publicAddressRepository because it might cause app freezes
        return UseCase.Result.Success<Unit>()

        /*val publicAddress = kotlin.runCatching {
            publicAddressRepository.getPublicAddress(userId, email, source = Source.RemoteNoCache)
        }.getOrNull()

        return if (publicAddress != null) {
            UseCase.Result.Success<Unit>()
        } else {
            UseCase.Result.Error("error fetching public keys")
        }*/
    }

    suspend fun execute(userId: UserId, eventEntities: List<EventEntity>): UseCase.Result {
        val emails = getEmails(eventEntities)
        return fetchPublicKeys(userId, emails)
    }

    suspend fun fetchPublicKeys(userId: UserId, emails: List<String>): UseCase.Result {
        coroutineScope {
            emails.distinct().mapAsync {
                fetchPublicKeys(userId, it)
            }
        }

        return UseCase.Result.Success<Unit>()
    }

    fun enqueueFetchPublicKeys(userId: UserId, eventEntities: List<EventEntity>) {
        val chunkedEmails = getEmails(eventEntities).chunked(100)
        chunkedEmails.forEach { emails ->
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.FETCH_PUBLIC_KEYS,
                        UseCaseWorker.INPUT_USER_ID to userId.id,
                        UseCaseWorker.INPUT_USER_EMAILS to emails.toTypedArray(),
                    )
                )
                .build()

            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.FETCH_PUBLIC_KEYS, ExistingWorkPolicy.APPEND, work)
        }
    }

    private fun getEmails(eventEntities: List<EventEntity>): List<String> {
        return eventEntities.flatMap {
            try {
                it.sharedEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content } +
                        it.calendarEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content } +
                        it.personalEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content }
            } catch (e: IllegalStateException) {
                logger.e("error getting event's author from JSON", e)
                emptyList()
            }
        }.filterNotNull()
    }

    companion object {
        const val WORKER_ID = "FETCH_PUBLIC_KEYS"
    }
}
