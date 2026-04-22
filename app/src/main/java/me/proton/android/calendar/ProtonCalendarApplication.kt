package me.proton.android.calendar

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.utils.CustomLocale
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.init.MainInitializer
import me.proton.android.calendar.presentation.forceUpdate.ForceUpdateViewModel
import me.proton.core.auth.data.db.AuthDatabase
import me.proton.core.presentation.ui.alert.ForceUpdateActivity
import me.proton.core.util.android.sentry.TimberLogger
import me.proton.core.util.kotlin.CoreLogger
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ProtonCalendarApplication : Application() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider

    @Inject
    lateinit var forceUpdateViewModel: ForceUpdateViewModel

    @Inject
    lateinit var authDatabase: AuthDatabase

    private var lastUiMode: Int = 0

    override fun onCreate() {
        super.onCreate()
        MainInitializer.init(this)

        // Forward Core Logs to Timber, using TimberLogger.
        CoreLogger.set(TimberLogger)

        System.loadLibrary("sqlcipher")

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        ShowNotificationUseCase.createNotificationChannels(this)

        forceUpdateViewModel.forceUpdate.observe(ProcessLifecycleOwner.get()) {
            if (it.forceUpdate) {
                startActivity(ForceUpdateActivity(this, it.apiErrorMessage))
            }
        }

        lastUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (newUiMode != lastUiMode) {
                    lastUiMode = newUiMode
                    refreshWidget()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        })
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(CustomLocale.applyCurrent(base))
    }

    fun getAppTheme(): AppTheme {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val appTheme =
            sharedPreferences.getInt(SharedPreferencesKeys.THEME, AppTheme.SYSTEM_DEFAULT.value)
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

        refreshWidget()
    }

    private fun refreshWidget() {
        val widgetComponent = ComponentName(this, CalendarWidget::class.java)
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(widgetComponent)
        if (ids.isNotEmpty()) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = widgetComponent
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            sendBroadcast(intent)
        }
    }
}
