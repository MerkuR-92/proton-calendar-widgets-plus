package me.proton.android.calendar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.presentation.account.CalendarUserCheck
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.data.repository.AuthRepositoryImpl
import me.proton.core.auth.domain.ClientSecret
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.auth.domain.usecase.SetupAccountCheck
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.DefaultUserCheck
import me.proton.core.country.data.repository.CountriesRepositoryImpl
import me.proton.core.country.domain.repository.CountriesRepository
import me.proton.core.crypto.android.srp.GOpenPGPSrpCrypto
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.network.data.ApiProvider
import me.proton.core.user.domain.UserManager
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
    fun provideAuthOrchestrator(): AuthOrchestrator = AuthOrchestrator()

    @Provides
    @Singleton
    fun provideSrpCrypto(): SrpCrypto =
        GOpenPGPSrpCrypto()

    @Provides
    fun provideCountriesRepository(@ApplicationContext context: Context): CountriesRepository =
        CountriesRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideUserCheck(
        @ApplicationContext context: Context,
        accountManager: AccountManager,
        userManager: UserManager
    ): PostLoginAccountSetup.UserCheck = CalendarUserCheck(context, accountManager, userManager)
}
