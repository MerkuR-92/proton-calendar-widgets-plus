package me.proton.android.calendar.test.di

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.mockk.mockk
import me.proton.core.payment.domain.repository.GoogleBillingRepository
import me.proton.core.payment.domain.usecase.FindGooglePurchaseForPaymentOrderId
import me.proton.core.payment.domain.usecase.FindUnacknowledgedGooglePurchase

// core's test libs still bring the IAP view models along, and hilt checks the whole graph, so give it something to bind
@Module
@InstallIn(SingletonComponent::class)
object TestGoogleBillingModule {

    @Provides
    fun provideFindUnacknowledgedGooglePurchase(): FindUnacknowledgedGooglePurchase = mockk(relaxed = true)

    @Provides
    fun provideFindGooglePurchaseForPaymentOrderId(): FindGooglePurchaseForPaymentOrderId = mockk(relaxed = true)

    @Provides
    fun provideGoogleBillingRepository(): GoogleBillingRepository<Activity> = mockk(relaxed = true)
}
