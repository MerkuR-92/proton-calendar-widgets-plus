package me.proton.android.calendar.domain.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import me.proton.android.calendar.R
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.MainActivity
import java.util.*

class ShowNotificationUseCase(private val logger: Logger, private val context: Context, private val calendarsRepository: CalendarsRepository, private val transformEventUseCase: TransformEventUseCase) {



    suspend fun execute(eventAlarms: List<EventAlarmEntity>) {
        logger.v("executing ShowNotificationUseCase, showing: ${eventAlarms}")

        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_EVENT_ALARMS)

        eventAlarms.forEach {

            val eventEntity = calendarsRepository.selectEventEntity(it.eventId)
            if (eventEntity == null) {
                logger.e("could not find EventEntity to show notification")
            } else {
                val dbEvent = transformEventUseCase.execute(eventEntity)
                if (dbEvent == null) {
                    logger.e("could not transform EventEntity to show notification")
                } else {

                    // TODO GENERATE OCCURRENCE IF NEEDED!!!!!!!!!!!!!!!!!!


                    val intent = MainActivity.createIntentToShowEventDetails(context, dbEvent.id, dbEvent.occurrence?.occurrenceNumber)
                    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0/* TODO */, intent, 0)

                    val eventStart = dbEvent.getStart(TimeZone.getDefault().id)
                    val text = if (dbEvent.isAllDay()) {
                        "TODO FORMAT: $eventStart"
                    } else {
                        "TODO FORMAT: $eventStart"
                    }

                    notificationBuilder
                        .setSmallIcon(R.drawable.ic_calendar_today)
                        .setContentTitle(dbEvent.summary)
                        .setContentText(text)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

                    notificationManager.notify(it.occurrence.toInt() /*TODO*/, notificationBuilder.build())
                }
            }
        }

    }

    companion object {

        private val CHANNEL_ID_EVENT_ALARMS = "CHANNEL_ID_EVENT_ALARMS"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // event alarms channel
                val name = context.getString(R.string.notification_channel_alarms)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID_EVENT_ALARMS, name, importance)
                notificationManager.createNotificationChannel(channel)

                // TODO other channels

            }
        }
    }

}
