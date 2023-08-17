package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.common.utils.ICalUtilsImpl.onlyDisplayType
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

class UpdateAlarmsUseCase @Inject constructor(
    private val logger: Logger,
    private val eventDecryptor: EventDecryptor,
    private val database: AppDatabase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase
) {

    companion object {
        const val UPDATE_ALARMS = "UPDATE_ALARMS"
    }

    suspend fun execute(userId: String, eventIds: List<String>): UseCase.Result {

        val primaryTimezone = database.calendarUserSettingsDao().select(userId)?.primaryTimezone
        val fromZonedDateTime = if (primaryTimezone == null) {
            logger.e("no primary timezone in UpdateAlarmsUseCase")
            ZonedDateTime.now(ZoneId.systemDefault())
        } else ZonedDateTime.now(ZoneId.of(primaryTimezone))

        val eventChains = eventIds.mapNotNull {
            val dbOriginalEvent = database.eventsDao().selectById(it)
            val originalEvent = dbOriginalEvent?.let { eventEntity ->
                if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
            }

            if (dbOriginalEvent == null) {
                logger.e("could not get dbOriginalEvent in UpdateAlarmsUseCase")
                null
            } else if (originalEvent == null) {
                logger.e("could not transform event in UpdateAlarmsUseCase")
                null
            } else {
                Pair(originalEvent, database.eventsDao().selectByUid(formatUidForICal(originalEvent.uid)))
            }
        }

        eventChains.forEach {

            val transformedChain = it.second.mapNotNull { eventEntity ->
                if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
            }

            val upcomingAlarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(transformedChain, fromZonedDateTime, "TODO").onlyDisplayType()

            if (transformedChain.isEmpty()) {
                return@forEach
            }

            transformedChain.forEach { event ->
                database.eventAlarmsDao().deleteAllByEventId(event.id)
            }

            safePersistEventAlarmUseCase.invoke(upcomingAlarms)
        }

        return handleAlarmsUseCase.execute(UserId(userId))
    }

}
