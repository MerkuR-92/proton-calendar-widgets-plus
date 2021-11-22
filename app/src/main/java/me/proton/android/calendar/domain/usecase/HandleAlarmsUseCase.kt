package me.proton.android.calendar.domain.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.proton.android.calendar.ProtonCalendarBroadcastReceiver
import me.proton.android.calendar.common.AlarmAction
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutDuplicates
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.domain.entity.UserId
import java.time.Duration
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

    companion object {
        const val WORKER_ID = "HANDLE_ALARMS"
    }

    suspend fun execute(userId: UserId, alarmEpochSeconds: Long? = null): UseCase.Result {
        logger.v("executing HandleAlarmsUseCase, alarmEpochSeconds: $alarmEpochSeconds")

        val nowInstant = Instant.now()

        val broadcastReceivedLateMinutes = if (alarmEpochSeconds != null) {
            Duration.ofSeconds(nowInstant.epochSecond - alarmEpochSeconds).toMinutes()
        } else 0

        if (broadcastReceivedLateMinutes >= 5) {
            logger.i("HandleAlarmsUseCase executed ${broadcastReceivedLateMinutes} minutes later than scheduled")
        }

        val lastHandledTimestamp =
            valueStoreProvider.provideValueStore(userId.id).getLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP)
                ?: (alarmEpochSeconds ?: nowInstant.epochSecond) - 1 // if no alarms were ever shown, let's pretend we've shown all until 1 second ago

        val alarmsToDisplayNow =
            database.eventAlarmsDao().selectAllBetweenInclusive(lastHandledTimestamp + 1, nowInstant.epochSecond)
                .filterOutDuplicates()
                .filter { it.action == AlarmAction.DISPLAY.value }

        logger.v("alarms to display at ${nowInstant}: ${alarmsToDisplayNow}")
        showNotificationUseCase.execute(alarmsToDisplayNow, userId.id)

        val maxAlarmOccurrenceSeconds =
            alarmsToDisplayNow.maxByOrNull { it.occurrence }?.occurrence ?: nowInstant.epochSecond
        valueStoreProvider.provideValueStore(userId.id)
            .putLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP, maxAlarmOccurrenceSeconds)

        val minAlarmOccurrenceSeconds =
            alarmsToDisplayNow.minByOrNull { it.occurrence }?.occurrence ?: nowInstant.epochSecond
        val minutesLate = ((nowInstant.epochSecond - minAlarmOccurrenceSeconds) / 60.0).roundToInt()
        if (alarmsToDisplayNow.isNotEmpty() && minutesLate >= 5) {
            logger.i("missed alarms to display: ${alarmsToDisplayNow.size} after ~${minutesLate} minutes")
        }

        // get next event alarms after currently shown and set system alarm to fire at that timestamp
        val alarmsToDisplayNext = database.eventAlarmsDao().selectUpcomingInclusive(maxAlarmOccurrenceSeconds + 1)
        logger.v("alarmsToDisplayNext: ${alarmsToDisplayNext}")
        alarmsToDisplayNext.firstOrNull()?.let {
            rescheduleSystemAlarm(Instant.ofEpochSecond(it.occurrence))
        }

        return UseCase.Result.Success<Unit>()

    }

    private fun rescheduleSystemAlarm(atInstant: Instant) {

        logger.d("HandleAlarmsUseCase scheduling next alarm at ${atInstant.atZone(ZoneId.systemDefault())}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ProtonCalendarBroadcastReceiver::class.java).apply {
            action = ProtonCalendarBroadcastReceiver.INTENT_ACTION_EVENT_ALARM
            putExtra(ProtonCalendarBroadcastReceiver.INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS, atInstant.epochSecond)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atInstant.toEpochMilli(), pendingIntent)
    }

}
