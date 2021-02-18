package me.proton.android.calendar.domain

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase.Companion.NOTIFICATION_ID_SYNC_SERVICE
import me.proton.android.calendar.domain.usecase.SyncServerEventsUseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.core.domain.entity.UserId
import org.koin.android.ext.android.inject

// TODO use ActivityManager#isBackgroundRestricted() to troubleshoot sync

class SyncService : Service() {

    private val logger: Logger by inject()
    private val database: AppDatabase by inject()

    private val syncServerEventsUseCase: SyncServerEventsUseCase by inject()

    private suspend fun syncServerEvents() {

        try {

            database.usersDao().select().forEach {
                syncServerEventsUseCase.execute(UserId(it.id)).ifSuccessAndLogErrors(logger) {}
            }

        } catch (e: Exception) {
            logger.e("exception in syncServerEvents()", e)
        }

    }

    override fun onCreate() {
        super.onCreate()

        startForeground(NOTIFICATION_ID_SYNC_SERVICE, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForeground(NOTIFICATION_ID_SYNC_SERVICE, createNotification())

        CoroutineScope(Dispatchers.IO).launch {

            logger.v("service coroutine start")

            syncServerEvents()

            logger.v("service stopping foreground")

            stopForeground(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    val notificationBuilder = NotificationCompat.Builder(this, ShowNotificationUseCase.CHANNEL_ID_SYNC_SERVICE)

    private fun createNotification(): Notification = notificationBuilder
        .setSmallIcon(R.drawable.ic_calendar_today)
        .setContentTitle(resources.getString(R.string.notification_sync))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setProgress(0, 0, true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

}
