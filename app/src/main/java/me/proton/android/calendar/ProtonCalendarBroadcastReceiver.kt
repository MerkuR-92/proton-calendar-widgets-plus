package me.proton.android.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.SyncWorker
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.SyncService
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.inject

class ProtonCalendarBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val logger: Logger by inject()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()
    private val accountManager: AccountManager by inject()

    override fun onReceive(context: Context?, intent: Intent?) {

        if (intent == null) {
            logger.i("null intent in ProtonCalendarBroadcastReceiver")
            return
        }

        // TODO for all users
        var userId: UserId?
        runBlocking(Dispatchers.Default) {
            userId = accountManager.getPrimaryUserId().firstOrNull()
        }

        if (userId == null) {
            logger.e("userId null in ProtonCalendarBroadcastReceiver")
            return
        }

        logger.v("intent in ProtonCalendarBroadcastReceiver: ${intent}")

        when (intent.action) {
            // Intent.ACTION_LOCKED_BOOT_COMPLETED is probably not needed
            Intent.ACTION_BOOT_COMPLETED -> {

                if (context == null) {
                    logger.e("null Context in ProtonCalendarBroadcastReceiver")
                } else {
                    ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java))
                    SyncWorker.setup(context, logger)
                }

                runBlocking(Dispatchers.Default) {
                    handleAlarmsUseCase.execute(userId!!)
                }
            }
            INTENT_ACTION_EVENT_ALARM -> {
                runBlocking(Dispatchers.Default) {
                    handleAlarmsUseCase.execute(
                        userId!!,
                        if (intent.hasExtra(INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS)) intent.getLongExtra(
                            INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS,
                            0
                        ) else null
                    )
                }
            }
            else -> {
                logger.i("unknown intent action")
            }
        }
    }

    companion object {
        const val INTENT_ACTION_EVENT_ALARM = "INTENT_ACTION_EVENT_ALARM"

        const val INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS = "INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS"
    }
}
