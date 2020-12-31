package me.proton.android.calendar.common

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.SyncService
import org.koin.core.KoinComponent
import org.koin.core.inject

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams),
    KoinComponent {

    private val logger: Logger by inject()

    override suspend fun doWork(): Result {
        ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, SyncService::class.java))
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "SYNC_SERVER_EVENTS_PERIODIC"
    }

}
