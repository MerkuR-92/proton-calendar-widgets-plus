package me.proton.android.calendar.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.common.API_HOST
import me.proton.android.calendar.common.HV_HOST
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.humanverification.data.HumanVerificationListenerImpl
import me.proton.core.humanverification.data.HumanVerificationManagerImpl
import me.proton.core.humanverification.data.HumanVerificationProviderImpl
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.repository.HumanVerificationRepositoryImpl
import me.proton.core.humanverification.data.repository.UserVerificationRepositoryImpl
import me.proton.core.humanverification.data.utils.NetworkRequestOverriderImpl
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.domain.HumanVerificationWorkflowHandler
import me.proton.core.humanverification.domain.repository.HumanVerificationRepository
import me.proton.core.humanverification.domain.repository.UserVerificationRepository
import me.proton.core.humanverification.domain.utils.NetworkRequestOverrider
import me.proton.core.humanverification.presentation.CaptchaApiHost
import me.proton.core.humanverification.presentation.HumanVerificationApiHost
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.humanverification.presentation.utils.HumanVerificationVersion
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HumanVerificationModule {

    @Provides
    fun provideHumanVerificationVersion() = HumanVerificationVersion.HV2

    @Provides
    @HumanVerificationApiHost
    fun provideHumanVerificationApiHost(): String = "https://verify.${HV_HOST}"

    @Provides
    @CaptchaApiHost
    fun provideCaptchaApiHost(): String = API_HOST

    @Provides
    fun provideHumanVerificationOrchestrator(
        humanVerificationVersion: HumanVerificationVersion
    ): HumanVerificationOrchestrator =
        HumanVerificationOrchestrator(humanVerificationVersion)

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
        db: HumanVerificationDatabase,
        keyStoreCrypto: KeyStoreCrypto
    ): HumanVerificationRepository =
        HumanVerificationRepositoryImpl(db, keyStoreCrypto)

    @Provides
    @Singleton
    fun provideUserVerificationRepository(
        apiProvider: ApiProvider
    ): UserVerificationRepository = UserVerificationRepositoryImpl(apiProvider)

    @Provides
    @Singleton
    fun provideHumanVerificationManagerImpl(
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        humanVerificationRepository: HumanVerificationRepository
    ): HumanVerificationManagerImpl =
        HumanVerificationManagerImpl(humanVerificationProvider, humanVerificationListener, humanVerificationRepository)

    @Provides
    fun provideNetworkRequestOverrider(
        @ApplicationContext context: Context,
    ): NetworkRequestOverrider =
        NetworkRequestOverriderImpl(OkHttpClient(), context)
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
