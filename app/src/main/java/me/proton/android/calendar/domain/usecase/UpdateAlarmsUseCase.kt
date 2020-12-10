package me.proton.android.calendar.domain.usecase

import android.content.Context
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.formatUidForICal
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateAlarmsUseCase(
    private val logger: Logger,
    private val context: Context,
    private val database: AppDatabase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val transformEventUseCase: TransformEventUseCase
) {

    suspend fun execute(userId: String, eventIds: List<String>) {

        val primaryTimezone = database.calendarUserSettingsDao().select(userId)?.primaryTimezone
        val fromZonedDateTime = if (primaryTimezone == null) {
            logger.e("no primary timezone in UpdateAlarmsUseCase")
            ZonedDateTime.now()
        } else ZonedDateTime.now(ZoneId.systemDefault())

        logger.v("executing UpdateAlarmsUseCase")
        eventIds.forEach {
            logger.v("eventId: $it")
        }

        val eventChains = eventIds.mapNotNull {
            val originalEvent = database.eventsDao().selectById(it)?.let { transformEventUseCase.execute(it) }

            if (originalEvent == null) {
                logger.e("could not transform event in UpdateAlarmsUseCase")
                null
            } else {
                Pair(originalEvent, database.eventsDao().selectByUid(formatUidForICal(originalEvent.uid)))
            }
        }

        eventChains.forEach {

            val transformedChain = it.second.mapNotNull { transformEventUseCase.execute(it) }

            val upcomingAlarms = ICalUtils.calculateUpcomingAlarmEntities(transformedChain, fromZonedDateTime, "TODO")

            transformedChain.forEach {
                database.eventAlarmsDao().deleteAllByEventId(it.id)
            }

            database.eventAlarmsDao().updateOrInsert(*upcomingAlarms.toTypedArray())

            logger.v("upcoming alarms for ${transformedChain.first().summary}")
            upcomingAlarms.forEach {
                logger.v("${Instant.ofEpochSecond(it.occurrence).atZone(fromZonedDateTime.zone)}")
            }

        }

        handleAlarmsUseCase.execute(UserId(userId))

    }

}
