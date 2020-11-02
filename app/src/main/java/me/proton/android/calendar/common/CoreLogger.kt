package me.proton.android.calendar.common

import me.proton.core.util.kotlin.Logger
import timber.log.Timber

object CoreLogger : Logger {

    override fun e(tag: String, msg: String?, e: Throwable?) =
        Timber.e(e ?: Throwable("no throwable provided"), msg ?: "no message provided")

    override fun i(tag: String, msg: String) =
        Timber.i(msg)
}
