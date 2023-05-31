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
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase,
    private val json: Json
) {

    suspend fun execute(userId: String, eventIds: List<String>) {

        val primaryTimezone = database.calendarUserSettingsDao().select(userId)?.primaryTimezone
        val fromZonedDateTime = if (primaryTimezone == null) {
            logger.e("no primary timezone in UpdateAlarmsUseCase")
            ZonedDateTime.now(ZoneId.systemDefault())
        } else ZonedDateTime.now(ZoneId.of(primaryTimezone))

        logger.v("executing UpdateAlarmsUseCase")

        val eventChains = eventIds.mapNotNull {
            val dbOriginalEvent = database.eventsDao().selectById(it)
            val originalEvent = dbOriginalEvent?.let { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptor.decrypt(it)
            } else {
                transformEventUseCase.execute(it)
            } }

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

            val transformedChain = it.second.mapNotNull { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptor.decrypt(it)
            } else {
                transformEventUseCase.execute(it)
            } }

            val upcomingAlarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(transformedChain, fromZonedDateTime, "TODO").onlyDisplayType()

            if (transformedChain.isEmpty()) {
                logger.v("transformedChain for event ${it.first.id} in UpdateAlarmsUseCase is empty")
                return@forEach
            }

            transformedChain.forEach {
                database.eventAlarmsDao().deleteAllByEventId(it.id)
            }

            safePersistEventAlarmUseCase.invoke(upcomingAlarms)

        }

        handleAlarmsUseCase.execute(UserId(userId))

    }

}
