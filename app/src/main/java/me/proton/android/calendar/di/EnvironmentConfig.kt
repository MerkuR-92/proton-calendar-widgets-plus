package me.proton.android.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.domain.EnvironmentConfiguration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EnvironmentConfig {
    @Provides
    @Singleton
    fun provideEnvironmentConfig(): EnvironmentConfiguration = object : EnvironmentConfiguration {
        override val apiHost: String get() = "https://${BuildConfig.API_HOST}/"
        override val hvHost: String get() = "https://${BuildConfig.HV3_HOST}/"
        override val proxyToken: String? get() = BuildConfig.PROXY_TOKEN
    }
}
