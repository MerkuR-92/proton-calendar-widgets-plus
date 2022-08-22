package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.domain.Logger
import javax.inject.Inject

class IndexEventForSearchUseCase @Inject constructor(
    private val logger: Logger,
    private val searchDatabase: SearchDatabase,
    private val transformEventUseCase: TransformEventUseCase
) {

    suspend fun execute(userId: String, eventEntites: List<EventEntity>): UseCase.Result {

        // only index Entities that are newer than what we already have in DB
        //  this saves us unnecessary decryption
        val entitiesToIndex = eventEntites.filter { eventEntity ->
            !searchDatabase.searchDao().hasEventWithHigherModifyTime(userId, eventEntity.calendarId, eventEntity.id, eventEntity.modifyTime)
        }

        val searchEvents = entitiesToIndex.mapNotNull {
            transformEventUseCase.execute(it)?.let { event ->
                SearchEventEntity.from(userId, event)
            }
        }

        searchDatabase.searchDao().insertSearchEvents(searchEvents)

        return UseCase.Result.Success<Unit>()
    }

}
