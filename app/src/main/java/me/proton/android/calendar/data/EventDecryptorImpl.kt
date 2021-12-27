package me.proton.android.calendar.data

import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase

class EventDecryptorImpl(
    private val transformEventUseCase: TransformEventUseCase,
    private val database: AppDatabase
): EventDecryptor {

    private data class CacheKey(
        val eventId: String,
        val calendarId: String
    )

    private data class CacheValue(
        val eventEntity: EventEntity,
        val event: Event
    )

    private val cache = mutableMapOf<CacheKey, CacheValue>()

    // TODO better synchronisation, maybe coroutine scope
    @Synchronized
    override suspend fun decrypt(eventEntity: EventEntity): Event? {

        val cacheKey = CacheKey(eventEntity.id, eventEntity.calendarId)
        val cacheValue = cache[cacheKey]
        val cachedEntity = cacheValue?.eventEntity

        return if (cachedEntity != null && cachedEntity.isTheSameAs(eventEntity)) {

            val cachedValue = cacheValue.event

            database.calendarsDao().selectById(eventEntity.calendarId)?.let { calendarEntity ->
                val calendar = Calendar(
                    calendarEntity.id,
                    calendarEntity.name,
                    calendarEntity.color,
                    calendarEntity.flags,
                    calendarEntity.display == 1,
                    calendarEntity.type
                )

                if (calendar != cacheValue.event.calendar) {
                    val eventCopy = Event.from(cachedValue, calendar = calendar)
                    cache[cacheKey] = CacheValue(eventEntity, eventCopy)
                    return eventCopy
                }
            }

            cachedValue
        } else {

            cache.remove(cacheKey)

            val decryptedEvent = transformEventUseCase.execute(eventEntity)

            if (decryptedEvent != null) {
                cache[cacheKey] = CacheValue(eventEntity, decryptedEvent)
            }

            cache[cacheKey]?.event
        }
    }

    private fun EventEntity.isTheSameAs(other: EventEntity): Boolean {

        // TODO extract SEQUENCE if this is not enough
        return this.id == other.id &&
                this.calendarId == other.calendarId &&
                this.createTime == other.createTime &&
                this.modifyTime == other.modifyTime

    }

}
