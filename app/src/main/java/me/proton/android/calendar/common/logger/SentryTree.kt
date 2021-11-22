package me.proton.android.calendar.common.logger

import android.os.Build
import android.util.Log
import io.sentry.Sentry
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import me.proton.android.calendar.BuildConfig
import timber.log.Timber

// TODO move this class to package that makes sense

internal class SentryTree : Timber.Tree() {

//    private val NO_TAG = "NO_TIMBER_TAG"
//    private val TAG_ANDROID_TAG = "TAG_ANDROID_TAG"
    private val TAG_APP_VERSION = "APP_VERSION"
    private val TAG_SDK_VERSION = "SDK_VERSION"
    private val TAG_DEVICE_MODEL = "DEVICE_MODEL"

    /**
     * This method is called by all other logging methods. It ignores all levels up to and including DEBUG.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority <= Log.DEBUG) {
            return
        }

        val eventBuilder = EventBuilder()
        when (priority) {
            Log.INFO -> eventBuilder.withLevel(Event.Level.INFO)
            Log.ERROR -> eventBuilder.withLevel(Event.Level.ERROR)
        }
        eventBuilder.withMessage(message)
//        eventBuilder.withTag(TAG_ANDROID_TAG, tag ?: NO_TAG)
        eventBuilder.withTag(TAG_APP_VERSION, BuildConfig.VERSION_NAME)
        eventBuilder.withTag(TAG_SDK_VERSION, "" + Build.VERSION.SDK_INT)
        eventBuilder.withTag(TAG_DEVICE_MODEL, Build.MODEL)
        Sentry.capture(eventBuilder.build())
    }

}
