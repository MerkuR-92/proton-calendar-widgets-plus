package me.proton.android.calendar.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.proton.android.calendar.common.BASE_URL
import me.proton.android.calendar.data.api.CalendarApiClient
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.NetworkManager
import me.proton.core.network.data.NetworkPrefs
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.data.client.ClientIdProviderImpl
import me.proton.core.network.data.client.ClientVersionValidatorImpl
import me.proton.core.network.data.client.ExtraHeaderProviderImpl
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.client.ClientVersionValidator
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import me.proton.core.network.domain.scopes.MissingScopeListener
import me.proton.core.network.domain.server.ServerTimeListener
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkManager(@ApplicationContext context: Context): NetworkManager =
        NetworkManager(context)

    @Provides
    @Singleton
    fun provideNetworkPrefs(@ApplicationContext context: Context) =
        NetworkPrefs(context)

    @Provides
    @Singleton
    fun provideApiFactory(
        apiClient: ApiClient,
        clientIdProvider: ClientIdProvider,
        serverTimeListener: ServerTimeListener,
        networkManager: NetworkManager,
        networkPrefs: NetworkPrefs,
        protonCookieStore: ProtonCookieStore,
        sessionProvider: SessionProvider,
        sessionListener: SessionListener,
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        missingScopeListener: MissingScopeListener,
        clientVersionValidator: ClientVersionValidator,
        dohAlternativesListener: DohAlternativesListener? = null
    ): ApiManagerFactory = ApiManagerFactory(
        BASE_URL,
        apiClient,
        clientIdProvider,
        serverTimeListener,
        networkManager,
        networkPrefs,
        sessionProvider,
        sessionListener,
        humanVerificationProvider,
        humanVerificationListener,
        missingScopeListener,
        protonCookieStore,
        CoroutineScope(Job() + Dispatchers.Default),
        clientVersionValidator = clientVersionValidator,
        dohAlternativesListener = dohAlternativesListener
    )

    @Provides
    @Singleton
    fun provideClientIdProvider(protonCookieStore: ProtonCookieStore): ClientIdProvider =
        ClientIdProviderImpl(BASE_URL, protonCookieStore)

    @Provides
    @Singleton
    fun provideProtonCookieStore(@ApplicationContext context: Context): ProtonCookieStore =
        ProtonCookieStore(context)

    @Provides
    @Singleton
    fun provideApiProvider(apiFactory: ApiManagerFactory, sessionProvider: SessionProvider): ApiProvider =
        ApiProvider(apiFactory, sessionProvider)

    @Provides
    @Singleton
    fun provideDohAlternativesListener(): DohAlternativesListener? = null

    @Provides
    fun provideClientVersionValidator(): ClientVersionValidator = ClientVersionValidatorImpl()

    @Provides
    @Singleton
    fun provideServerTimeListener(
        context: CryptoContext
    ) = object : ServerTimeListener {
        override fun onServerTimeUpdated(epochSeconds: Long) {
            context.pgpCrypto.updateTime(epochSeconds)
        }
    }

    @Provides
    @Singleton
    fun provideExtraHeaderProvider(): ExtraHeaderProvider =
        ExtraHeaderProviderImpl()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {
    @Binds
    abstract fun provideApiClient(calendarApiClient: CalendarApiClient): ApiClient
}
