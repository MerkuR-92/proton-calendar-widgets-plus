package me.proton.android.calendar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.core.auth.data.repository.AuthRepositoryImpl
import me.proton.core.auth.domain.ClientSecret
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.country.data.repository.CountriesRepositoryImpl
import me.proton.core.country.domain.repository.CountriesRepository
import me.proton.core.crypto.android.srp.GOpenPGPSrpCrypto
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.humanverification.data.repository.HumanVerificationRemoteRepositoryImpl
import me.proton.core.humanverification.domain.repository.HumanVerificationRemoteRepository
import me.proton.core.network.data.ApiProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @ClientSecret
    fun provideClientSecret(): String = ""

    @Provides
    @Singleton
    fun provideAuthRepository(apiProvider: ApiProvider): AuthRepository =
        AuthRepositoryImpl(apiProvider)

    @Provides
    @Singleton
    fun provideSrpCrypto(): SrpCrypto =
        GOpenPGPSrpCrypto()

    @Provides
    @Singleton
    fun provideRemoteRepository(apiProvider: ApiProvider): HumanVerificationRemoteRepository =
        HumanVerificationRemoteRepositoryImpl(apiProvider)

    @Provides
    fun provideCountriesRepository(@ApplicationContext context: Context): CountriesRepository =
        CountriesRepositoryImpl(context)
}
