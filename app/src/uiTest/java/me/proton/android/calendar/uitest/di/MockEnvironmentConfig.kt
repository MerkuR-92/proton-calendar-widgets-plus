package me.proton.android.calendar.uitest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.android.calendar.di.EnvironmentConfig
import me.proton.android.calendar.domain.EnvironmentConfiguration
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
    val token = AtomicReference(EMPTY_STRING)

    @Provides
    @Singleton
    fun provideEnvironmentConfig(): EnvironmentConfiguration = object : EnvironmentConfiguration {
        override val apiHost: String = "https://api.${host.get()}"
        override val hvHost: String = "https://verify.${host.get()}"
        override val proxyToken: String? = token.get()
    }
}
