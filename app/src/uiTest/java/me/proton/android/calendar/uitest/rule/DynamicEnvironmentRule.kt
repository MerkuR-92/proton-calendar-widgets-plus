package me.proton.android.calendar.uitest.rule

import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.uitest.di.MockEnvironmentConfig
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.EMPTY_STRING
import org.junit.rules.ExternalResource

class DynamicEnvironmentRule(
    private val testHost: String = "proton.black",
    private val proxyToken: String = EMPTY_STRING
) :
    ExternalResource() {

    val quark: Quark get() = Quark.fromDefaultResources(host, token)

    private val host get() = InstrumentationRegistry.getArguments().getString("host", testHost)

    private val token get() = InstrumentationRegistry.getArguments().getString("proxyToken", proxyToken)

    init {
        MockEnvironmentConfig.host.set(host)
        MockEnvironmentConfig.token.set(token)
    }
}
