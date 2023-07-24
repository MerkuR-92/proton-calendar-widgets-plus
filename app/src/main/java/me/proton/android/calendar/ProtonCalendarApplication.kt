package me.proton.android.calendar

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.logger.AppLogger
import me.proton.android.calendar.common.logger.SentryIntegration
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.utils.CustomLocale
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.init.MainInitializer
import me.proton.android.calendar.presentation.forceUpdate.ForceUpdateViewModel
import me.proton.core.presentation.ui.alert.ForceUpdateActivity
import me.proton.core.util.android.sentry.TimberLogger
import me.proton.core.util.kotlin.CoreLogger
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class ProtonCalendarApplication : Application() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    override fun onCreate() {
        super.onCreate()
        MainInitializer.init(this)

        // Forward Core Logs to Timber, using TimberLogger.
        CoreLogger.set(TimberLogger)

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        } else {
            SentryIntegration.initSentry(this, defaultSharedPreferencesProvider.sharedPreferences)
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
