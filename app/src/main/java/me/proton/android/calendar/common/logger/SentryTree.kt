package me.proton.android.calendar.common.logger

import android.util.Log
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import timber.log.Timber

internal class SentryTree : Timber.Tree() {

    /**
     * This method is called by all other logging methods. It ignores all levels up to and including DEBUG.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority <= Log.DEBUG) {
            return
        }

        val event = SentryEvent(t).apply {
            this.message = Message().apply { this.message = message }
            this.level = when (priority) {
                Log.DEBUG -> SentryLevel.DEBUG
                Log.INFO -> SentryLevel.INFO
                Log.WARN -> SentryLevel.WARNING
                Log.ERROR -> SentryLevel.ERROR
                Log.ASSERT -> SentryLevel.FATAL
                else -> SentryLevel.DEBUG
            }
            tag?.let { this.setTag("TAG", tag) }
        }
        Sentry.captureEvent(event)
    }

}
