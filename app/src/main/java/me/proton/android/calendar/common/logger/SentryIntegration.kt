package me.proton.android.calendar.common.logger

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.API_HOST
import me.proton.android.calendar.common.SharedPreferencesKeys
import java.util.UUID

object SentryIntegration {

    private lateinit var application: Application

    @JvmStatic
    fun getInstallationId(sharedPreferences: SharedPreferences): String =
        sharedPreferences.getString(SharedPreferencesKeys.APP_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also {
                sharedPreferences.edit(commit = true) { putString(SharedPreferencesKeys.APP_INSTALLATION_ID, it) }
            }

    @JvmStatic
    fun initSentry(app: Application, sharedPreferences: SharedPreferences) {
        application = app
        initSentry(getInstallationId(sharedPreferences))

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(ProtonExceptionHandler(currentHandler))
    }

    private fun initSentry(installationId: String) {
        val sentryDsn = BuildConfig.SENTRY_DSN_NEW
        SentryAndroid.init(application) { options ->
            options.dsn = sentryDsn
            options.release = BuildConfig.VERSION_NAME
            options.isAnrEnabled = true
            options.isAttachStacktrace = true
            options.isEnableAutoSessionTracking = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.environment = "${if (BuildConfig.DEBUG) "debug" else "release"}\\$API_HOST"
            /*options.setBeforeSend { event, _ ->
                // filter out events
            }*/
        }
        Sentry.setUser(User().apply { id = installationId } )
    }


}
