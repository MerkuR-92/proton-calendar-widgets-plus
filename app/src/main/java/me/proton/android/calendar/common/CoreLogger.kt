package me.proton.android.calendar.common

import me.proton.android.calendar.BuildConfig
import me.proton.core.util.kotlin.Logger
import me.proton.core.util.kotlin.LoggerLogTag
import org.jetbrains.annotations.NonNls
import timber.log.Timber

object CoreLogger : Logger {

    override fun e(tag: String, e: Throwable) =
        Timber.tag(tag).e(e)

    override fun e(tag: String, e: Throwable, @NonNls message: String) =
        Timber.tag(tag).e(e, message)

    override fun i(tag: String, @NonNls message: String) =
        Timber.tag(tag).i(message)

    override fun i(tag: String, e: Throwable, message: String) =
        Timber.tag(tag).i(e, message)

    override fun d(tag: String, message: String) =
        Timber.tag(tag).d(message)

    override fun d(tag: String, e: Throwable, message: String) =
        Timber.tag(tag).d(e, message)

    override fun v(tag: String, message: String) =
        Timber.tag(tag).v(message)

    override fun v(tag: String, e: Throwable, message: String) =
        Timber.tag(tag).v(e, message)

    override fun log(tag: LoggerLogTag, message: String) = if (BuildConfig.DEBUG) {
        Timber.tag(tag.name).d(message)
    } else Unit
}
