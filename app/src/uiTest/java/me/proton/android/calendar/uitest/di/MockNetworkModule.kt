package me.proton.android.calendar.uitest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.android.calendar.di.NetworkModule
import me.proton.android.calendar.uitest.rule.DynamicEnvironmentRule.Companion.proxyToken
import me.proton.android.calendar.uitest.rule.DynamicEnvironmentRule.Companion.testHost
import me.proton.core.network.data.client.ExtraHeaderProviderImpl
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.Constants
import me.proton.core.network.data.di.DohProviderUrls
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.util.kotlin.takeIfNotBlank
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object MockNetworkModule {

    @Provides
    @BaseProtonApiUrl
    fun provideProtonApiUrl(): HttpUrl = "https://api.${testHost.get()}/".toHttpUrl()

    @DohProviderUrls
    @Provides
    fun provideDohProviderUrls(): Array<String> = Constants.DOH_PROVIDERS_URLS

    @CertificatePins
    @Provides
    fun provideCertificatePins() = emptyArray<String>()

    @AlternativeApiPins
    @Provides
    fun provideAlternativeApiPins() = emptyList<String>()

    @Provides
    @Singleton
    fun provideDohAlternativesListener(): DohAlternativesListener? = null

    @Provides
    @Singleton
    fun provideExtraHeaderProvider(): ExtraHeaderProvider = ExtraHeaderProviderImpl().apply {
        proxyToken.get()?.takeIfNotBlank()?.let { addHeaders("X-atlas-secret" to it) }
    }
}