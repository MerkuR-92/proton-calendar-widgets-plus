package me.proton.android.calendar.uitest.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import me.proton.android.calendar.di.HumanVerificationModule
import me.proton.android.calendar.uitest.rule.DynamicEnvironmentRule.Companion.testHost
import me.proton.core.humanverification.presentation.HumanVerificationApiHost
import me.proton.core.humanverification.presentation.utils.HumanVerificationVersion

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [HumanVerificationModule::class]
)
class MockHumanVerificationModule {

    @Provides
    fun provideHumanVerificationVersion() = HumanVerificationVersion.HV3

    @Provides
    @HumanVerificationApiHost
    fun provideHumanVerificationApiHost(): String = "https://verify.${testHost.get()}"
}