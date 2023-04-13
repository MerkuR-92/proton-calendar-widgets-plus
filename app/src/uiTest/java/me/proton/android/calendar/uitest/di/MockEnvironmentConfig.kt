package me.proton.android.calendar.uitest.di

import androidx.test.platform.app.InstrumentationRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.android.calendar.di.EnvironmentConfig
import me.proton.android.calendar.domain.EnvironmentConfiguration
import me.proton.core.util.kotlin.CoreLogger
import me.proton.core.util.kotlin.EMPTY_STRING
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [EnvironmentConfig::class]
)
object MockEnvironmentConfig {

    val host = AtomicReference("proton.black")
    val atlasToken = AtomicReference(EMPTY_STRING)

    @Provides
    @Singleton
    fun provideEnvironmentConfig(): EnvironmentConfiguration = object : EnvironmentConfiguration {

        override val apiHost: String get() = "https://api.$testHost/"

        override val hvHost: String get() = "https://verify.$testHost/"

        override val proxyToken: String
            get() = InstrumentationRegistry
                .getArguments()
                .getString("proxyToken", atlasToken.get())

        private val testHost
            get() = InstrumentationRegistry
                .getArguments()
                .getString("host", host.get())
    }
}
