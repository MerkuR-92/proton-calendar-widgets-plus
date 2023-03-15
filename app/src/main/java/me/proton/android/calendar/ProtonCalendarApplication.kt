package me.proton.android.calendar

import android.app.Application
import android.content.Context
import android.database.CursorWindow
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.commonModule
import me.proton.android.calendar.common.coreModule
import me.proton.android.calendar.common.logger.LoggerImpl
import me.proton.android.calendar.common.logger.SentryIntegration
import me.proton.android.calendar.common.logger.SentryTree
import me.proton.android.calendar.common.networkModule
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.repositoryModule
import me.proton.android.calendar.common.useCaseModule
import me.proton.android.calendar.common.utils.CustomLocale
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.init.MainInitializer
import me.proton.android.calendar.presentation.forceUpdate.ForceUpdateViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.network.data.ApiProvider
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
import java.lang.reflect.Field
import javax.inject.Inject

@HiltAndroidApp
class ProtonCalendarApplication : Application() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var appDatabase: AppDatabase

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
    lateinit var userSettingsRepository: UserSettingsRepository

    @Inject
    lateinit var contactEmailsRepository: ContactRepository

    @Inject
    lateinit var getRecipientPublicAddresses: GetRecipientPublicAddresses

    @Inject
    lateinit var defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    @Inject
    lateinit var calendarsRepository: CalendarsRepository

    @Inject
    lateinit var crypto: Crypto

    @Inject
    lateinit var cryptoContext: CryptoContext

    @Inject
    lateinit var eventDecryptor: EventDecryptor

    override fun onCreate() {
        super.onCreate()
        MainInitializer.init(this)

        startKoin {
            androidContext(this@ProtonCalendarApplication)
            modules(
                commonModule,
                repositoryModule,
                networkModule,
                useCaseModule,
                coreModule(
                    appDatabase,
                    apiProvider,
                    crypto,
                    cryptoContext,
                    eventDecryptor,
                    accountManager,
                    userManager,
                    userRepository,
                    userAddressRepository,
                    calendarsRepository,
                    contactEmailsRepository,
                    userSettingsRepository,
                    getRecipientPublicAddresses
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

        // hack for android.database.sqlite.SQLiteBlobTooBigException: Row too big to fit into CursorWindow
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 5 * 1024 * 1024) // 5 MB
        } catch (e: Exception) {
            logger.e("exception setting cursor window size", e)
        }

        ShowNotificationUseCase.createNotificationChannels(this)

        forceUpdateViewModel.forceUpdate.observe(ProcessLifecycleOwner.get()) {
            if (it.forceUpdate) {
                startActivity(ForceUpdateActivity(this, it.apiErrorMessage))
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(CustomLocale.applyCurrent(base))
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
