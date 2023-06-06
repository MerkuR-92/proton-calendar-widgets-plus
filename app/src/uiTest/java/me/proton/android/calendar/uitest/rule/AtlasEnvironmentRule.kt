package me.proton.android.calendar.uitest.rule

import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.uitest.di.AtlasEnvironmentConfig
import me.proton.core.util.kotlin.EMPTY_STRING
import org.junit.rules.ExternalResource


open class AtlasEnvironmentRule(
    private val atlasHost: String = "proton.black",
    private val atlasProxyToken: String = EMPTY_STRING
) : ExternalResource() {

    val host: String get() = InstrumentationRegistry.getArguments().getString("host", atlasHost)

    val proxyToken: String get() = InstrumentationRegistry.getArguments().getString("proxyToken", atlasProxyToken)

    init {
        AtlasEnvironmentConfig.atlasHost.set(host)
        AtlasEnvironmentConfig.atlasProxyToken.set(proxyToken)
    }
}
