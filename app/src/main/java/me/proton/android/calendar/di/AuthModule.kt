package me.proton.android.calendar.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.core.auth.data.repository.AuthRepositoryImpl
import me.proton.core.auth.domain.ClientSecret
import me.proton.core.auth.domain.crypto.CryptoProvider
import me.proton.core.auth.domain.crypto.SrpProofProvider
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.presentation.srp.CryptoProviderImpl
import me.proton.core.auth.presentation.srp.SrpProofProviderImpl
import me.proton.core.humanverification.data.repository.HumanVerificationLocalRepositoryImpl
import me.proton.core.humanverification.data.repository.HumanVerificationRemoteRepositoryImpl
import me.proton.core.humanverification.domain.repository.HumanVerificationLocalRepository
import me.proton.core.humanverification.domain.repository.HumanVerificationRemoteRepository
import me.proton.core.network.data.ApiProvider
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object AuthModule {

    @Provides
    @ClientSecret
    fun provideClientSecret(): String = ""

    @Provides
    @Singleton
    fun provideAuthRepository(apiProvider: ApiProvider): AuthRepository =
        AuthRepositoryImpl(apiProvider)

    @Provides
    fun provideLocalRepository(@ApplicationContext context: Context): HumanVerificationLocalRepository =
        HumanVerificationLocalRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideRemoteRepository(apiProvider: ApiProvider): HumanVerificationRemoteRepository =
        HumanVerificationRemoteRepositoryImpl(apiProvider)
}

@Module
@InstallIn(ApplicationComponent::class)
abstract class AuthBindsModule {

    @Binds
    abstract fun provideSrpProofProvider(srpProofProviderImpl: SrpProofProviderImpl): SrpProofProvider

    @Binds
    abstract fun provideCryptoProvider(cryptoProviderImpl: CryptoProviderImpl): CryptoProvider
}
