package me.proton.android.calendar.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import javax.inject.Inject

class EventDecryptorImpl @Inject constructor(
    private val transformEventUseCase: TransformEventUseCase,
    private val database: AppDatabase,
    private val json: Json
): EventDecryptor {

    private data class CacheKey(
        val eventId: String,
        val calendarId: String,
        val modifyTime: Long
    )

    private data class CacheValue(
        val eventEntity: EventEntity,
        val event: Event
    )

    private val cache = mutableMapOf<CacheKey, CacheValue>()
    private val mutex = Mutex()

    override suspend fun decrypt(eventEntity: EventEntity): Event? = mutex.withLock {
        val cacheKey = CacheKey(eventEntity.id, eventEntity.calendarId, eventEntity.modifyTime)
        val cacheValue = cache[cacheKey]
        val cachedEntity = cacheValue?.eventEntity

        return if (cachedEntity != null && cachedEntity.isTheSameAs(eventEntity)) {

            val cachedValue = cacheValue.event

            database.calendarsDao().selectById(eventEntity.calendarId)?.joinToCalendar(database, json)?.let { calendar ->
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

    override suspend fun decryptAllowingApiCall(eventEntity: EventEntity): Event? {

        // we don't care about cache value and force decrypting again
        val decryptedEvent = transformEventUseCase.execute(eventEntity, allowApiCall = true)

        mutex.withLock {
            val cacheKey = CacheKey(eventEntity.id, eventEntity.calendarId, eventEntity.modifyTime)

            if (decryptedEvent != null) {
                cache[cacheKey] = CacheValue(eventEntity, decryptedEvent)
            }

            return cache[cacheKey]?.event
        }

    }

    override suspend fun clearCache() = mutex.withLock {
        cache.clear()
    }

    override suspend fun getFromCache(eventId: String, calendarId: String, modifyTime: Long): Event? {
        val cacheKey = CacheKey(eventId, calendarId, modifyTime)
        return cache[cacheKey]?.event
    }

    private fun EventEntity.isTheSameAs(other: EventEntity): Boolean {

        // TODO extract SEQUENCE if this is not enough
        return this.id == other.id &&
                this.calendarId == other.calendarId &&
                this.createTime == other.createTime &&
                this.modifyTime == other.modifyTime

    }

}
