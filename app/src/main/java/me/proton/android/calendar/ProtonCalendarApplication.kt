package me.proton.android.calendar

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.logger.LoggerImpl
import me.proton.android.calendar.common.logger.SentryIntegration
import me.proton.android.calendar.common.logger.SentryTree
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.utils.CustomLocale
import me.proton.android.calendar.common.worker.PeriodicCalendarWorker
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.EmailMessageRepository
import me.proton.android.calendar.domain.usecase.GenerateEmailPackageUseCase
import me.proton.android.calendar.domain.usecase.SendEmailDirect
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.presentation.forceUpdate.ForceUpdateViewModel
import me.proton.core.accountmanager.data.AccountStateHandler
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
import me.proton.core.network.domain.scopes.MissingScopeListener
import me.proton.core.presentation.ui.alert.ForceUpdateActivity
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.CoreLogger
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
    lateinit var accountStateHandler: AccountStateHandler

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userAddressRepository: UserAddressRepository

    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    @Inject
    lateinit var authOrchestrator: AuthOrchestrator

    @Inject
    lateinit var missingScopeListener: MissingScopeListener

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

    @Inject
    lateinit var defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var calendarsRepository: CalendarsRepository

    @Inject
    lateinit var eventDecryptor: EventDecryptor

    @Inject
    lateinit var crypto: Crypto

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
                    accountStateHandler,
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
                    networkManager,
                    defaultSharedPreferencesProvider,
                    appDatabase,
                    calendarsRepository,
                    userSettingsRepository,
                    missingScopeListener,
                    eventDecryptor,
                    crypto
                )
            )
        }

        CoreLogger.set(LoggerImpl)
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        } else {
            SentryIntegration.initSentry(this, defaultSharedPreferencesProvider.sharedPreferences)
            Timber.plant(SentryTree())
        }

        ShowNotificationUseCase.createNotificationChannels(this)

        accountStateHandler.start()

        forceUpdateViewModel.forceUpdate.observe(ProcessLifecycleOwner.get()) {
            if (it.forceUpdate) {
                startActivity(ForceUpdateActivity(this, it.apiErrorMessage))
            }
        }

        PeriodicCalendarWorker.setup(this, logger)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(CustomLocale.apply(base))
    }

    fun getAppTheme(): AppTheme {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val appTheme = sharedPreferences.getInt(SharedPreferencesKeys.THEME, AppTheme.SYSTEM_DEFAULT.value)
        return AppTheme.values()[appTheme]
    }

    fun changeAppTheme(theme: AppTheme) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val editor = sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.THEME, theme.value)
        editor.apply()

        when (theme) {
            AppTheme.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            AppTheme.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
