package me.proton.android.calendar.common

import me.proton.core.util.kotlin.Logger
import timber.log.Timber

// TODO temporarily stop logging to Sentry from Core-Networking, we log in our custom logic
object CoreNetworkingLogger : Logger {

    override fun e(tag: String, msg: String?, e: Throwable?) {}

    override fun i(tag: String, msg: String) {}
}
