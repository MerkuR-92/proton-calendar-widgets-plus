package me.proton.android.calendar.domain.usecase

import android.content.Context
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateAlarmsUseCase(
    private val logger: Logger,
    private val eventDecryptor: EventDecryptor,
    private val database: AppDatabase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val transformEventUseCase: TransformEventUseCase
) {

    suspend fun execute(userId: String, eventIds: List<String>) {

        val primaryTimezone = database.calendarUserSettingsDao().select(userId)?.primaryTimezone
        val fromZonedDateTime = if (primaryTimezone == null) {
            logger.e("no primary timezone in UpdateAlarmsUseCase")
            ZonedDateTime.now(ZoneId.systemDefault())
        } else ZonedDateTime.now(ZoneId.of(primaryTimezone))

        logger.v("executing UpdateAlarmsUseCase")

        val eventChains = eventIds.mapNotNull {
            val originalEvent = database.eventsDao().selectById(it)?.let { if (FeatureFlag.USE_EVENT_DECRYPTOR) {
                eventDecryptor.decrypt(it)
            } else {
                transformEventUseCase.execute(it)
            } }

            if (originalEvent == null) {
                logger.e("could not transform event in UpdateAlarmsUseCase")
                null
            } else {
                Pair(originalEvent, database.eventsDao().selectByUid(formatUidForICal(originalEvent.uid)))
            }
        }

        eventChains.forEach {

            val transformedChain = it.second.mapNotNull { if (FeatureFlag.USE_EVENT_DECRYPTOR) {
                eventDecryptor.decrypt(it)
            } else {
                transformEventUseCase.execute(it)
            } }

            val upcomingAlarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(transformedChain, fromZonedDateTime, "TODO")

            if (transformedChain.isEmpty()) {
                logger.v("transformedChain for event ${it.first.id} in UpdateAlarmsUseCase is empty")
                return@forEach
            }

            transformedChain.forEach {
                database.eventAlarmsDao().deleteAllByEventId(it.id)
            }

            database.eventAlarmsDao().updateOrInsert(*upcomingAlarms.toTypedArray())

            upcomingAlarms.forEach {
                logger.v("${Instant.ofEpochSecond(it.occurrence).atZone(fromZonedDateTime.zone)}")
            }

        }

        handleAlarmsUseCase.execute(UserId(userId))

    }

}
