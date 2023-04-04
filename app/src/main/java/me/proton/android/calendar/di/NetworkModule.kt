package me.proton.android.calendar.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.BASE_URL
import me.proton.android.calendar.data.api.CalendarApiClient
import me.proton.core.network.data.client.ExtraHeaderProviderImpl
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.Constants
import me.proton.core.network.data.di.DohProviderUrls
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.util.kotlin.takeIfNotBlank
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @BaseProtonApiUrl
    fun provideProtonApiUrl(): HttpUrl = BASE_URL.toHttpUrl()

    @DohProviderUrls
    @Provides
    fun provideDohProviderUrls(): Array<String> = Constants.DOH_PROVIDERS_URLS

    @CertificatePins
    @Provides
    fun provideCertificatePins() = if (BuildConfig.USE_DEFAULT_PINS) {
        Constants.DEFAULT_SPKI_PINS
    } else {
        emptyArray()
    }

    @AlternativeApiPins
    @Provides
    fun provideAlternativeApiPins() = if (BuildConfig.USE_DEFAULT_PINS) {
        Constants.ALTERNATIVE_API_SPKI_PINS
    } else {
        emptyList()
    }

    @Provides
    @Singleton
    fun provideDohAlternativesListener(): DohAlternativesListener? = null

    @Provides
    @Singleton
    fun provideExtraHeaderProvider(): ExtraHeaderProvider = ExtraHeaderProviderImpl().apply {
        BuildConfig.PROXY_TOKEN?.takeIfNotBlank()?.let { addHeaders("X-atlas-secret" to it) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {
    @Binds
    abstract fun provideApiClient(calendarApiClient: CalendarApiClient): ApiClient
}
