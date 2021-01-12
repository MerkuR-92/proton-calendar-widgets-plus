package me.proton.android.calendar.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.proton.android.calendar.common.API_BASE_URL
import me.proton.android.calendar.common.CoreLogger
import me.proton.android.calendar.data.api.CalendarApiClient
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.di.NetworkManager
import me.proton.core.network.data.di.NetworkPrefs
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
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
        networkManager: NetworkManager,
        networkPrefs: NetworkPrefs,
        sessionProvider: SessionProvider,
        sessionListener: SessionListener
    ): ApiFactory = ApiFactory(
        API_BASE_URL, apiClient, CoreLogger, networkManager, networkPrefs, sessionProvider, sessionListener,
        cookieStore = null, CoroutineScope(Job() + Dispatchers.Default), emptyArray(), emptyList()
    )

    @Provides
    @Singleton
    fun provideApiProvider(apiFactory: ApiFactory, sessionProvider: SessionProvider): ApiProvider =
        ApiProvider(apiFactory, sessionProvider)
}

@Module
@InstallIn(ApplicationComponent::class)
abstract class NetworkBindsModule {
    @Binds
    abstract fun provideApiClient(calendarApiClient: CalendarApiClient): ApiClient
}
