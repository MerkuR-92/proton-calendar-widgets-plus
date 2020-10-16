package me.proton.android.calendar.domain.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import me.proton.android.calendar.R
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.MainViewModel
import java.util.*

class ShowNotificationUseCase(private val logger: Logger, private val context: Context, private val calendarsRepository: CalendarsRepository, private val transformEventUseCase: TransformEventUseCase) {



    suspend fun execute(eventAlarms: List<EventAlarmEntity>) {

        // Alarm's occurrence timestamp can be identical for multiple Alarms, but notification IDs
        //  have to be distinct for each one of them
        fun generateNotificationId(notificationManager: NotificationManager, alarmEntity: EventAlarmEntity): Int {

            val activeNotifications = notificationManager.activeNotifications
            var notificationId = alarmEntity.occurrence
            while (activeNotifications.find { it.id == notificationId.toInt() } != null) {
                notificationId++
            }

            return notificationId.toInt()
        }

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


                    val intent = MainViewModel.createIntentToShowEventDetails(context, dbEvent.id, dbEvent.occurrence?.occurrenceNumber)
                    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0/* TODO */, intent, 0)

                    val text = dbEvent.formatStartForNotification(TimeZone.getDefault().id, context.resources) // formatting in phone's timezone

                    notificationBuilder
                        .setSmallIcon(R.drawable.ic_calendar_today)
                        .setContentTitle(dbEvent.summary ?: context.getString(R.string.default_event_summary))
                        .setContentText(text)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

                    notificationManager.notify(generateNotificationId(notificationManager, it), notificationBuilder.build())
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
