package me.proton.android.calendar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.network.data.ApiProvider
import me.proton.core.user.domain.UserManager
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class ProtonCalendarApplication : Application() {

    @Inject
    lateinit var apiProvider: ApiProvider

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var authOrchestrator: AuthOrchestrator

    @Inject
    lateinit var keyStoreCrypto: KeyStoreCrypto

    private val logger: Logger by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ProtonCalendarApplication)
            modules(
                commonModule, viewModelModule, repositoryModule, networkModule, useCaseModule,
                coreModule(apiProvider, accountManager, authOrchestrator, userManager, keyStoreCrypto)
            )
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        } else {
            Sentry.init(BuildConfig.SENTRY_DSN, AndroidSentryClientFactory(this))
            Timber.plant(SentryTree())
        }

        ShowNotificationUseCase.createNotificationChannels(this)

        SyncWorker.setup(this, logger)
    }

}
