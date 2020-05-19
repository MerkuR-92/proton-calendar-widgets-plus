package me.proton.android.calendar

import android.app.Application
import me.proton.android.calendar.common.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree

class ProtonCalendarApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ProtonCalendarApplication)
            modules(commonModule, viewModelModule, repositoryModule, networkModule, useCaseModule)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        } else {
            Timber.plant(SentryTree())
        }
    }

}