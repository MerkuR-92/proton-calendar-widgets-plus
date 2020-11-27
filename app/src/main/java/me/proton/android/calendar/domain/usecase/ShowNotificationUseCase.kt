package me.proton.android.calendar.domain.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import me.proton.android.calendar.R
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ShowNotificationUseCase(private val logger: Logger, private val context: Context, private val calendarsRepository: CalendarsRepository, private val transformEventUseCase: TransformEventUseCase, private val database: AppDatabase) {

    suspend fun execute(eventAlarms: List<EventAlarmEntity>, userId: String) {

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
        val systemDefaultZoneId = ZoneId.systemDefault()
        val displayTimeZoneId = database.calendarUserSettingsDao().select(userId)?.primaryTimezone

        if (displayTimeZoneId == null) {
            logger.e("empty displayTimeZoneId in ShowNotificationUseCase")
        }

        eventAlarms.forEach {

            val eventEntity = calendarsRepository.selectEventEntity(it.eventId)
            if (eventEntity == null) {
                logger.e("could not find EventEntity to show notification")
            } else {
                val dbEvent = transformEventUseCase.execute(eventEntity)
                if (dbEvent == null) {
                    logger.e("could not transform EventEntity to show notification")
                } else {

                    val eventWithOccurrence = if (dbEvent.isRecurring()) {

                        val occurrences = dbEvent.generateOccurrencesUntil(
                            ZonedDateTime.ofInstant(Instant.ofEpochSecond(it.occurrence), systemDefaultZoneId).toLocalDate(),
                            systemDefaultZoneId.id)

                        if (occurrences == null || occurrences.isEmpty()) {
                            logger.e("could not generate occurrences for notification of recurring event")
                            null
                        } else {
                            dbEvent.withOccurrence(occurrences.last())
                        }

                    } else null

                    val intent = MainViewModel.createMainIntentToShowEventDetails(context, dbEvent.id, eventWithOccurrence?.occurrence?.occurrenceNumber)
                    /* FLAG_ONE_SHOT cancels pending intent after it's sent */
                    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT)

                    val text = (eventWithOccurrence ?: dbEvent).formatStartForNotification(systemDefaultZoneId.id, context.resources) // formatting in phone's timezone

                    notificationBuilder
                        .setSmallIcon(R.drawable.ic_calendar_today)
                        .setContentTitle(dbEvent.summary ?: context.getString(R.string.default_event_summary))
                        .setContentText(text)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

                    notificationManager.notify(generateNotificationId(notificationManager, it), notificationBuilder.build())

                    eventWithOccurrence?.let { currentEvent ->
                        if (currentEvent.occurrence == null) {
                            logger.e("occurrence of currentEvent for notifications is null")
                        }
                        currentEvent.occurrence?.let {
                            createAlarmsForNextOccurrence(dbEvent, currentEvent, it, ZoneId.of(displayTimeZoneId ?: ZoneId.systemDefault().id))
                        }
                    }
                }
            }
        }

    }

    /**
     * For next occurrence of current Event, make sure we have its Alarms in the database.
     */
    private suspend fun createAlarmsForNextOccurrence(originalEvent: Event, currentEvent: Event, currentOccurrence: Event.Occurrence, zoneId: ZoneId) {

        val now = ZonedDateTime.now(zoneId)

        if (now.isAfter(currentEvent.getActualStart(zoneId.id))) {
            logger.v("createAlarmsForNextOccurrence for ${currentEvent.summary}, current occurrence: ${currentOccurrence}")

            val nextOccurrenceNumber = currentOccurrence.occurrenceNumber + 1
            val nextEvent = originalEvent.withOccurrence(nextOccurrenceNumber, zoneId.id)

            logger.v("createAlarmsForNextOccurrence: next event with next occurrence: ${nextEvent}")

            val alarmsForNextOccurrence = ICalUtils.calculateAlarmEntities(nextEvent!!, zoneId.id, "TODO")

            logger.v("created next alarms for occurrence ${nextEvent.occurrence}:")
            if (alarmsForNextOccurrence.isNotEmpty()) {
                alarmsForNextOccurrence.forEach {
                    calendarsRepository.deleteEventAlarmsForEvent(it.eventId)
                    logger.v("${Instant.ofEpochSecond(it.occurrence)}")
                    calendarsRepository.persistEventAlarm(it)
                }
            }

        } else {
            logger.v("createAlarmsForNextOccurrence it's not yet after occurrence for ${currentEvent.summary}, occurrence: ${currentOccurrence}")
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

        // TODO in the future tag notifications with userId and cancel only for given user
        fun cancelAllNotifications(context: Context) {
            val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
        }
    }

}
