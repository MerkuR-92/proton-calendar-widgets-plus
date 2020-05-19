package me.proton.android.calendar.common

import android.util.Log
import timber.log.Timber

// TODO move this class to package that makes sense

internal class SentryTree : Timber.Tree() {

//    private val NO_TAG = "NO_TIMBER_TAG"
//    private val TAG_ANDROID_TAG = "TAG_ANDROID_TAG"
//    private val TAG_APP_VERSION = "APP_VERSION"
//    private val TAG_SDK_VERSION = "SDK_VERSION"
//    private val TAG_DEVICE_MODEL = "DEVICE_MODEL"

    /**
     * This method is called by all other logging methods. It ignores all levels up to and including DEBUG.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority <= Log.DEBUG) {
            return
        }
        // TODO include Sentry, maybe in separate module?
//        val eventBuilder = EventBuilder()
//        eventBuilder.withMessage(message)
//        eventBuilder.withTag(TAG_ANDROID_TAG, tag ?: NO_TAG)
//        eventBuilder.withTag(TAG_APP_VERSION, AppUtil.getAppVersion())
//        eventBuilder.withTag(TAG_SDK_VERSION, "" + Build.VERSION.SDK_INT)
//        eventBuilder.withTag(TAG_DEVICE_MODEL, Build.MODEL)
//        Sentry.capture(eventBuilder.build())
        /**
         *
         */
        TODO("Sentry not implemented")
    }

}



//}