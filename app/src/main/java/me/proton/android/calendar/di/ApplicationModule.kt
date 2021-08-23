package me.proton.android.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.common.DEFAULT_DOMAIN_HOST
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.Logger
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.domain.entity.Product
import me.proton.core.user.data.DefaultDomainHost
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    @Singleton
    fun provideProduct(): Product = Product.Calendar

    @Provides
    @Singleton
    fun provideRequiredAccountType(): AccountType = AccountType.Internal

    @Provides
    @Singleton
    fun provideLogger(): Logger = TimberLogger

    @Provides
    @DefaultDomainHost
    fun provideDefaultDomainHost() = DEFAULT_DOMAIN_HOST // "protonmail.com"
}
