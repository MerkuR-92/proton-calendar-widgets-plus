package me.proton.android.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.core.mailmessage.data.repository.EmailMessageRepositoryImpl
import me.proton.core.mailmessage.domain.repository.EmailMessageRepository
import me.proton.core.network.data.ApiProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MailModule {

    @Provides
    @Singleton
    fun provideEmailMessageRepositoryImpl(
        provider: ApiProvider
    ): EmailMessageRepository = EmailMessageRepositoryImpl(provider)
}
