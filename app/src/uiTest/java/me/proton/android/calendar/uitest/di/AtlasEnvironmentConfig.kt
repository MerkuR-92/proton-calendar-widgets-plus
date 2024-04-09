package me.proton.android.calendar.uitest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.core.configuration.EnvironmentConfiguration
import me.proton.core.configuration.dagger.ContentResolverEnvironmentConfigModule
import me.proton.core.util.kotlin.EMPTY_STRING
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ContentResolverEnvironmentConfigModule::class]
)
object AtlasEnvironmentConfig {

    val atlasHost = AtomicReference("proton.black")
    val atlasProxyToken = AtomicReference(EMPTY_STRING)

    @Provides
    @Singleton
    fun provideEnvironmentConfig(): EnvironmentConfiguration {
        val map = mapOf(
            "apiHost" to "https://api.${atlasHost.get()}",
            "hvHost" to "https://verify.${atlasHost.get()}",
            "proxyToken" to atlasProxyToken.get(),
        )
        return EnvironmentConfiguration.fromMap(map)
    }
}
