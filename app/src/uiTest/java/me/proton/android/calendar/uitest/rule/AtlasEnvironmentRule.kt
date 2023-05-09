package me.proton.android.calendar.uitest.rule

import androidx.test.platform.app.InstrumentationRegistry
import me.proton.android.calendar.uitest.di.AtlasEnvironmentConfig
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.EMPTY_STRING
import me.proton.core.util.kotlin.deserialize
import org.junit.rules.ExternalResource


class AtlasEnvironmentRule(
    private val host: String = "proton.black",
    private val proxyToken: String = EMPTY_STRING
) :
    ExternalResource() {

    val quark = Quark(
        host = atlasHost,
        proxyToken = atlasProxyToken,
        InstrumentationRegistry.getInstrumentation().context
            .assets
            .open("internal_api.json")
            .bufferedReader()
            .use { it.readText() }
            .deserialize())

    private val atlasHost get() = InstrumentationRegistry.getArguments().getString("host", host)

    private val atlasProxyToken get() = InstrumentationRegistry.getArguments().getString("proxyToken", proxyToken)

    init {
        AtlasEnvironmentConfig.atlasHost.set(atlasHost)
        AtlasEnvironmentConfig.atlasProxyToken.set(atlasProxyToken)
    }
}
