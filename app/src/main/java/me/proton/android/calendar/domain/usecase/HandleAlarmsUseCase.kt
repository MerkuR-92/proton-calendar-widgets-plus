package me.proton.android.calendar.domain.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.proton.android.calendar.ProtonCalendarBroadcastReceiver
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import java.time.Instant


class HandleAlarmsUseCase(
    private val logger: Logger,
    private val context: Context,
    private val calendarsRepository: CalendarsRepository,
    private val showNotificationUseCase: ShowNotificationUseCase,
    private val valueStoreProvider: ValueStoreProvider
) {

    /**
     * @param alarmEpochSeconds if present, show notifications for alarms at this timestamp
     */
    suspend fun execute(userId: String, alarmEpochSeconds: Long? = null) {
        logger.v("executing HandleAlarmsUseCase, alarmEpochSeconds: $alarmEpochSeconds")

        // TODO CANCEL ALARM?

        // get event alarms we were supposed to show for this timestamp and show them
        if (alarmEpochSeconds != null) {

            // TODO
            // get event alarms to show

            // TODO
            showNotificationUseCase.execute()

            // TODO after reboot we need to fire all alarms that were not displayed during power-off
            valueStoreProvider.provideValueStore(userId).putLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP, alarmEpochSeconds)

        }

        // get upcoming event alarm and reschedule system alarm for its timestamp

        // TODO
        //val nextEventAlarm = //calendarsRepository
        //rescheduleSystemAlarm(nextEventAlarm.)

    }

    private fun rescheduleSystemAlarm(atInstant: Instant) {

        logger.v("HandleAlarmsUseCase scheduling next alarm at ${atInstant}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ProtonCalendarBroadcastReceiver::class.java).apply {
            action = ProtonCalendarBroadcastReceiver.INTENT_ACTION_EVENT_ALARM
            putExtra(ProtonCalendarBroadcastReceiver.INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS, atInstant.epochSecond)
        }

        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atInstant.toEpochMilli(), pendingIntent)
    }


}
