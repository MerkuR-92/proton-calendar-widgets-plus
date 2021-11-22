package me.proton.android.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.worker.SyncWorker
import me.proton.android.calendar.common.worker.UseCaseWorker
import me.proton.android.calendar.domain.Logger
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.inject

class ProtonCalendarBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val logger: Logger by inject()
    private val accountManager: AccountManager by inject()

    override fun onReceive(context: Context?, intent: Intent?) {

        if (intent == null) {
            logger.i("null intent in ProtonCalendarBroadcastReceiver")
            return
        }

        logger.v("intent in ProtonCalendarBroadcastReceiver: ${intent}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (context == null) {
                    logger.e("null Context in ProtonCalendarBroadcastReceiver ACTION_BOOT_COMPLETED")
                } else {
                    SyncWorker.setup(context, logger)

                    try {
                        GlobalScope.launch(Dispatchers.IO) {

                            val userId = accountManager.getPrimaryUserId().firstOrNull()
                            if (userId == null) {
                                logger.i("null userId in ProtonCalendarBroadcastReceiver ACTION_BOOT_COMPLETED")
                            } else {
                                handleAlarms(userId, context)
                            }

                        }
                    } catch (e: Exception) {
                        logger.e("ProtonCalendarBroadcastReceiver, boot completed handle alarms error", e)
                    }
                }
            }
            INTENT_ACTION_EVENT_ALARM -> {
                if (context == null) {
                    logger.e("null Context in ProtonCalendarBroadcastReceiver INTENT_ACTION_EVENT_ALARM")
                } else {
                    try {
                        GlobalScope.launch(Dispatchers.IO){

                            val alarmTimestamp = if (intent.hasExtra(INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS)) intent.getLongExtra(
                                INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS,
                                0
                            ) else null

                            val userId = accountManager.getPrimaryUserId().firstOrNull()
                            if (userId == null) {
                                logger.i("null userId in ProtonCalendarBroadcastReceiver INTENT_ACTION_EVENT_ALARM")
                            } else {
                                handleAlarms(userId, context, alarmTimestamp)
                            }

                        }
                    } catch (e: Exception) {
                        logger.e("ProtonCalendarBroadcastReceiver, handle alarm intent error", e)
                    }
                }
            }
            else -> {
                logger.e("ProtonCalendarBroadcastReceiver unknown intent action: ${intent.action}")
            }
        }
    }

    fun handleAlarms(userId: UserId, context: Context, alarmEpochSeconds: Long? = null): LiveData<Operation.State> {

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.HANDLE_ALARMS,
                    UseCaseWorker.INPUT_USER_ID to userId.id,
                    UseCaseWorker.INPUT_ALARM_EPOCH_SECONDS to alarmEpochSeconds
                )
            )
            .build()

        return WorkManager.getInstance(context)
            .enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.HANDLE_ALARMS, ExistingWorkPolicy.REPLACE, work).state

    }

    companion object {
        const val INTENT_ACTION_EVENT_ALARM = "INTENT_ACTION_EVENT_ALARM"

        const val INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS = "INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS"
    }
}
