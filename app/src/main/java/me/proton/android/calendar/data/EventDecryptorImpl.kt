package me.proton.android.calendar.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Calendar
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
    private val eventsMutex = Mutex()
    private val calendarsMutex = Mutex()

    private var allCalendars = listOf<Calendar>()

    override suspend fun setCalendars(calendars: List<Calendar>) {
        calendarsMutex.withLock {
            allCalendars = calendars
        }
    }

    override suspend fun decrypt(eventEntity: EventEntity): Event? {

        // check if Calendar hasn't changed since we put the Event into cache
        val calendar = allCalendars.find { it.id == eventEntity.calendarId }
            ?: database.calendarsDao().selectById(eventEntity.calendarId)?.joinToCalendar(database, json)
            ?: return null // Calendar is not in local cache or in DB, hard fail

        val cacheKey = CacheKey(eventEntity.id, calendar.id, eventEntity.modifyTime)
        val cacheValue = cache[cacheKey]
        val cachedEntity = cacheValue?.eventEntity

        return if (cachedEntity != null && cachedEntity.isTheSameAs(eventEntity)) {

            val cachedValue = cacheValue.event

            if (calendar.color != cachedValue.calendar.color) { // Calendar color changed since last decryption
                val eventCopy = Event.from(cachedValue, calendar = calendar)
                eventsMutex.withLock {
                    cache[cacheKey] = CacheValue(eventEntity, eventCopy)
                }
                eventCopy
            } else {
                cachedValue
            }

        } else {

            eventsMutex.withLock {
                cache.remove(cacheKey)
            }

            val decryptedEvent = transformEventUseCase.execute(eventEntity)

            if (decryptedEvent != null) {
                eventsMutex.withLock {
                    cache[cacheKey] = CacheValue(eventEntity, decryptedEvent)
                }
            }

            cache[cacheKey]?.event
        }
    }

    override suspend fun decryptAllowingApiCall(eventEntity: EventEntity): Event? {

        // we don't care about cache value and force decrypting again
        val decryptedEvent = transformEventUseCase.execute(eventEntity, allowApiCall = true)

        eventsMutex.withLock {
            return if (decryptedEvent != null) {
                val cacheKey = CacheKey(eventEntity.id, eventEntity.calendarId, eventEntity.modifyTime)
                cache[cacheKey] = CacheValue(eventEntity, decryptedEvent)
                decryptedEvent
            } else null
        }

    }

    override suspend fun clearCache() = eventsMutex.withLock {
        cache.clear()
    }

    override suspend fun getFromCache(eventId: String, calendarId: String, modifyTime: Long): Event? {

        // check if Calendar hasn't changed since we put the Event into cache
        val calendar = allCalendars.find { it.id == calendarId }
            ?: database.calendarsDao().selectById(calendarId)?.joinToCalendar(database, json)
            ?: return null // Calendar is not in local cache or in DB, hard fail

        val cacheKey = CacheKey(eventId, calendarId, modifyTime)
        val cacheValue = cache[cacheKey]

        return if (cacheValue != null) {
            if (calendar.color != cacheValue.event.calendar.color) { // Calendar color changed since last decryption
                val eventCopy = Event.from(cacheValue.event, calendar = calendar)
                eventsMutex.withLock {
                    cache[cacheKey] = CacheValue(cacheValue.eventEntity, eventCopy)
                }
                eventCopy
            } else cacheValue.event
        } else null
    }

    private fun EventEntity.isTheSameAs(other: EventEntity): Boolean {

        // TODO extract SEQUENCE if this is not enough
        return this.id == other.id &&
                this.calendarId == other.calendarId &&
                this.createTime == other.createTime &&
                this.modifyTime == other.modifyTime

    }

}
