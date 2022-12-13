package me.proton.android.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.common.HV_HOST
import me.proton.core.humanverification.presentation.HumanVerificationApiHost
import me.proton.core.humanverification.presentation.utils.HumanVerificationVersion

@Module
@InstallIn(SingletonComponent::class)
object HumanVerificationModule {

    @Provides
    fun provideHumanVerificationVersion() = HumanVerificationVersion.HV3

    @Provides
    @HumanVerificationApiHost
    fun provideHumanVerificationApiHost(): String = "https://verify.${HV_HOST}"
}
