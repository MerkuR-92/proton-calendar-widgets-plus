package me.proton.android.calendar.uitest.rule

import me.proton.android.calendar.uitest.di.MockNetworkModule
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.EMPTY_STRING
import org.junit.rules.ExternalResource

class DynamicEnvironmentRule(host: String = "proton.black", proxyToken: String = EMPTY_STRING) : ExternalResource() {

    val quark: Quark

    init {
        MockNetworkModule.host.set(host)
        MockNetworkModule.proxyToken.set(proxyToken)

        quark = Quark.fromDefaultResources(MockNetworkModule.host.get(), MockNetworkModule.proxyToken.get())
    }
}
