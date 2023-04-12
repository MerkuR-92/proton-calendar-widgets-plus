package me.proton.android.calendar.uitest.rule

import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.uitest.BaseTest.Companion.testTag
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.CoreLogger
import me.proton.core.util.kotlin.EMPTY_STRING
import org.junit.rules.ExternalResource
import java.util.concurrent.atomic.AtomicReference

class DynamicEnvironmentRule(host: String = "proton.black") : ExternalResource() {

    val quark: Quark

    init {
        val args = InstrumentationRegistry .getArguments()

        testHost.set(args.getString("host") ?: host)
        proxyToken.set(args.getString("proxyToken") ?: EMPTY_STRING)

        CoreLogger.i(testTag, "Overriding host: ${testHost.get()}")

        quark = Quark.fromDefaultResources(testHost.get(), proxyToken.get())
    }

    companion object {
        val testHost = AtomicReference("proton.black")
        val proxyToken = AtomicReference(BuildConfig.PROXY_TOKEN)
    }
}
