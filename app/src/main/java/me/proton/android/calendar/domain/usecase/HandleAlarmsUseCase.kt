package me.proton.android.calendar.domain.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.proton.android.calendar.ProtonCalendarBroadcastReceiver
import me.proton.android.calendar.common.ICalUtilsImpl.filterOutDuplicates
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.domain.entity.UserId
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

class HandleAlarmsUseCase(
    private val logger: Logger,
    private val context: Context,
    private val database: AppDatabase,
    private val showNotificationUseCase: ShowNotificationUseCase,
    private val valueStoreProvider: ValueStoreProvider
) {

    /**
     * @param alarmEpochSeconds if present, show notifications for alarms at this timestamp or show all missed ones until now
     */
    suspend fun execute(userId: UserId, alarmEpochSeconds: Long? = null) {
        logger.v("executing HandleAlarmsUseCase, alarmEpochSeconds: $alarmEpochSeconds")

        val nowInstant = Instant.now()

        val maxHandledAlarmOccurrenceSeconds = if (alarmEpochSeconds != null) { // handle only event alarms we were supposed to show for this timestamp

            // get only those alarms that we were supposed to show for this use case execution
            val alarmsToDisplayNow = database.eventAlarmsDao().selectUpcoming(alarmEpochSeconds).filter { it.occurrence == alarmEpochSeconds }

            logger.v("alarms to display at alarmEpochSeconds $alarmEpochSeconds: $alarmsToDisplayNow")

            if (alarmsToDisplayNow.isNotEmpty()) {
                showNotificationUseCase.execute(alarmsToDisplayNow.filterOutDuplicates(), userId.id)
            }

            valueStoreProvider.provideValueStore(userId.id).putLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP, alarmEpochSeconds)

            alarmEpochSeconds

        } else { // no timestamp provided, show missed alarms up until now

            val lastHandledTimestamp = valueStoreProvider.provideValueStore(userId.id).getLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP) ?: nowInstant.epochSecond

            // if no alarms were ever shown, this will return empty result
            val alarmsToDisplayNow = database.eventAlarmsDao().select(lastHandledTimestamp + 1, nowInstant.epochSecond).filterOutDuplicates()

            // TODO get most X recent alarms so we don't bombard user with obsolete alarms if they haven't used the app for a while

            logger.v("missed alarms to display at ${nowInstant}: ${alarmsToDisplayNow}")
            showNotificationUseCase.execute(alarmsToDisplayNow, userId.id)

            val maxAlarmOccurrenceSeconds = alarmsToDisplayNow.maxByOrNull { it.occurrence }?.occurrence ?: nowInstant.epochSecond
            valueStoreProvider.provideValueStore(userId.id).putLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP, maxAlarmOccurrenceSeconds)

            val minAlarmOccurrenceSeconds = alarmsToDisplayNow.minByOrNull { it.occurrence }?.occurrence ?: nowInstant.epochSecond
            if (alarmsToDisplayNow.isNotEmpty()) {
                logger.i("missed alarms to display: ${alarmsToDisplayNow.size} after ~${((nowInstant.epochSecond - minAlarmOccurrenceSeconds) / 60.0).roundToInt()} minutes")
            }

            maxAlarmOccurrenceSeconds

        }

        // get next event alarms after currently shown and set system alarm to fire at that timestamp
        val alarmsToDisplayNext = database.eventAlarmsDao().selectUpcoming(maxHandledAlarmOccurrenceSeconds + 1)
        logger.v("alarmsToDisplayNext: ${alarmsToDisplayNext}")
        alarmsToDisplayNext.firstOrNull()?.let {
            rescheduleSystemAlarm(Instant.ofEpochSecond(it.occurrence))
        }

    }

    private fun rescheduleSystemAlarm(atInstant: Instant) {

        logger.d("HandleAlarmsUseCase scheduling next alarm at ${atInstant.atZone(ZoneId.systemDefault())}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ProtonCalendarBroadcastReceiver::class.java).apply {
            action = ProtonCalendarBroadcastReceiver.INTENT_ACTION_EVENT_ALARM
            putExtra(ProtonCalendarBroadcastReceiver.INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS, atInstant.epochSecond)
        }

        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atInstant.toEpochMilli(), pendingIntent)
    }

}
