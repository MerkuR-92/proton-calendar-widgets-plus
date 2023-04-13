package me.proton.android.calendar.uitest.rule

import me.proton.android.calendar.uitest.di.MockEnvironmentConfig
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.EMPTY_STRING
import org.junit.rules.ExternalResource

class DynamicEnvironmentRule(host: String = "proton.black", proxyToken: String = EMPTY_STRING) : ExternalResource() {

    val quark: Quark

    init {
        MockEnvironmentConfig.host.set(host)
        MockEnvironmentConfig.atlasToken.set(proxyToken)

        quark = Quark.fromDefaultResources(MockEnvironmentConfig.host.get(), MockEnvironmentConfig.atlasToken.get())
    }
}
