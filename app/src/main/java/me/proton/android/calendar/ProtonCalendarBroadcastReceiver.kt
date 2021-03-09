package me.proton.android.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.SyncWorker
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.core.accountmanager.domain.AccountManager
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.lang.Exception

class ProtonCalendarBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val logger: Logger by inject()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()
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
                    logger.e("null Context in ProtonCalendarBroadcastReceiver")
                } else {
                    SyncWorker.setup(context, logger)
                }

                try {
                    GlobalScope.launch(Dispatchers.IO){
                        handleAlarmsUseCase.execute(accountManager.getPrimaryUserId().firstOrNull()!!)
                    }
                } catch (e: Exception) {
                    logger.e("ProtonCalendarBroadcastReceiver, boot completed handle alarms error", e)
                }
            }
            INTENT_ACTION_EVENT_ALARM -> {
                try {
                    GlobalScope.launch(Dispatchers.IO){
                        handleAlarmsUseCase.execute(
                            accountManager.getPrimaryUserId().firstOrNull()!!,
                            if (intent.hasExtra(INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS)) intent.getLongExtra(
                                INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS,
                                0
                            ) else null
                        )
                    }
                } catch (e: Exception) {
                    logger.e("ProtonCalendarBroadcastReceiver, handle alarm intent error", e)
                }
            }
            else -> {
                logger.e("ProtonCalendarBroadcastReceiver unknown intent action: ${intent.action}")
            }
        }
    }

    companion object {
        const val INTENT_ACTION_EVENT_ALARM = "INTENT_ACTION_EVENT_ALARM"

        const val INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS = "INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS"
    }
}
