package me.proton.android.calendar.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.common.CAPTCHA_BASE_URL
import me.proton.core.accountmanager.data.db.AccountManagerDatabase
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.humanverification.data.HumanVerificationListenerImpl
import me.proton.core.humanverification.data.HumanVerificationManagerImpl
import me.proton.core.humanverification.data.HumanVerificationProviderImpl
import me.proton.core.humanverification.data.repository.HumanVerificationRepositoryImpl
import me.proton.core.humanverification.data.repository.UserVerificationRepositoryImpl
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.domain.HumanVerificationWorkflowHandler
import me.proton.core.humanverification.domain.repository.HumanVerificationRepository
import me.proton.core.humanverification.domain.repository.UserVerificationRepository
import me.proton.core.humanverification.presentation.CaptchaBaseUrl
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HumanVerificationModule {

    @Provides
    @CaptchaBaseUrl
    fun provideCaptchaBaseUrl(): String = CAPTCHA_BASE_URL

    @Provides
    fun provideHumanVerificationOrchestrator(): HumanVerificationOrchestrator =
        HumanVerificationOrchestrator()

    @Provides
    @Singleton
    fun provideHumanVerificationListener(
        humanVerificationRepository: HumanVerificationRepository
    ): HumanVerificationListener =
        HumanVerificationListenerImpl(humanVerificationRepository)

    @Provides
    @Singleton
    fun provideHumanVerificationProvider(
        humanVerificationRepository: HumanVerificationRepository
    ): HumanVerificationProvider =
        HumanVerificationProviderImpl(humanVerificationRepository)

    @Provides
    @Singleton
    fun provideHumanVerificationRepository(
        db: AccountManagerDatabase,
        keyStoreCrypto: KeyStoreCrypto
    ): HumanVerificationRepository =
        HumanVerificationRepositoryImpl(db, keyStoreCrypto)

    @Provides
    @Singleton
    fun provideUserVerificationRepository(
        apiProvider: ApiProvider,
        clientIdProvider: ClientIdProvider,
        humanVerificationRepository: HumanVerificationRepository
    ): UserVerificationRepository =
        UserVerificationRepositoryImpl(apiProvider, clientIdProvider, humanVerificationRepository)

    @Provides
    @Singleton
    fun provideHumanVerificationManagerImpl(
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        humanVerificationRepository: HumanVerificationRepository
    ): HumanVerificationManagerImpl =
        HumanVerificationManagerImpl(humanVerificationProvider, humanVerificationListener, humanVerificationRepository)
}

@Module
@InstallIn(SingletonComponent::class)
interface HumanVerificationBindModule {
    @Binds
    fun bindHumanVerificationManager(
        humanVerificationManagerImpl: HumanVerificationManagerImpl
    ): HumanVerificationManager

    @Binds
    fun bindHumanVerificationWorkflowHandler(
        humanVerificationManagerImpl: HumanVerificationManagerImpl
    ): HumanVerificationWorkflowHandler
}
