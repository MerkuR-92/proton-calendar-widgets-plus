package me.proton.android.calendar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.EmailMessageRepository
import me.proton.android.calendar.domain.usecase.GenerateEmailPackageUseCase
import me.proton.android.calendar.domain.usecase.SendEmailDirect
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.domain.entity.Product
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.key.domain.repository.PublicAddressRepository
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.domain.NetworkManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class ProtonCalendarApplication : Application() {

    @Inject
    lateinit var product: Product

    @Inject
    lateinit var apiProvider: ApiProvider

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userAddressRepository: UserAddressRepository

    @Inject
    lateinit var authOrchestrator: AuthOrchestrator

    @Inject
    lateinit var humanVerificationManager: HumanVerificationManager

    @Inject
    lateinit var humanVerificationOrchestrator: HumanVerificationOrchestrator

    @Inject
    lateinit var keyStoreCrypto: KeyStoreCrypto

    @Inject
    lateinit var getRecipientPublicAddresses: GetRecipientPublicAddresses

    @Inject
    lateinit var publicAddressRepository: PublicAddressRepository

    @Inject
    lateinit var contactEmailsRepository: ContactRepository

    @Inject
    lateinit var cryptoContext: CryptoContext

    @Inject
    lateinit var generateEmailPackageUseCase: GenerateEmailPackageUseCase

    @Inject
    lateinit var emailMessageRepository: EmailMessageRepository

    @Inject
    lateinit var sendEmailDirect: SendEmailDirect

    @Inject
    lateinit var networkManager: NetworkManager

    private val logger: Logger by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ProtonCalendarApplication)
            modules(
                commonModule,
                viewModelModule,
                repositoryModule,
                networkModule,
                useCaseModule,
                coreModule(
                    product,
                    apiProvider,
                    accountManager,
                    authOrchestrator,
                    humanVerificationManager,
                    humanVerificationOrchestrator,
                    userManager,
                    userRepository,
                    userAddressRepository,
                    keyStoreCrypto,
                    getRecipientPublicAddresses,
                    contactEmailsRepository,
                    cryptoContext,
                    sendEmailDirect,
                    publicAddressRepository,
                    networkManager
                )
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
