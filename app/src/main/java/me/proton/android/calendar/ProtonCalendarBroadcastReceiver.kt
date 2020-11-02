package me.proton.android.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.core.domain.entity.UserId
import org.koin.core.KoinComponent
import org.koin.core.inject

class ProtonCalendarBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val logger: Logger by inject()
    private val valueStoreProvider: ValueStoreProvider by inject()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    override fun onReceive(context: Context?, intent: Intent?) {

        if (intent == null) {
            logger.i("null intent in ProtonCalendarBroadcastReceiver")
            return
        }

        // TODO for all users
        val userId = valueStoreProvider.provideValueStore("TODO LOGIN").getString("USERID")?.let { UserId(it) }
        if (userId == null) {
            logger.e("userId null in ProtonCalendarBroadcastReceiver")
            return
        }

        logger.v("intent in ProtonCalendarBroadcastReceiver: ${intent}")

        when (intent.action) {
            // Intent.ACTION_LOCKED_BOOT_COMPLETED is probably not needed
            Intent.ACTION_BOOT_COMPLETED -> {
                runBlocking(Dispatchers.Default) {
                    handleAlarmsUseCase.execute(userId)
                }
            }
            INTENT_ACTION_EVENT_ALARM -> {
                runBlocking(Dispatchers.Default) {
                    handleAlarmsUseCase.execute(
                        userId,
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
