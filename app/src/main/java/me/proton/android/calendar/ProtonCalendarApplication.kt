package me.proton.android.calendar

import android.app.Application
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree

class ProtonCalendarApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ProtonCalendarApplication)
            modules(commonModule, viewModelModule, repositoryModule, networkModule, useCaseModule, coreModule)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        } else {
            Sentry.init(BuildConfig.SENTRY_DSN, AndroidSentryClientFactory(this))
            Timber.plant(SentryTree())
        }

        ShowNotificationUseCase.createNotificationChannels(this)
    }

}
