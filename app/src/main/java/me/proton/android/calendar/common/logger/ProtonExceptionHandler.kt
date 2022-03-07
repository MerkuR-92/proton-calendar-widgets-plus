package me.proton.android.calendar.common.logger

import java.io.PrintWriter
import java.io.StringWriter

class ProtonExceptionHandler(val innerHandler: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        val writer = StringWriter()
        e.printStackTrace(PrintWriter(writer))
        TimberLogger.e("ProtonExceptionHandler: uncaught exception", e)
        innerHandler?.uncaughtException(t, e)
    }
}
