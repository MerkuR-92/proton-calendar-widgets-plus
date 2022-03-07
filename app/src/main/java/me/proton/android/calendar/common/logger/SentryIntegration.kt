package me.proton.android.calendar.common.logger

import android.app.Application
import io.sentry.android.core.SentryAndroid
import me.proton.android.calendar.BuildConfig

object SentryIntegration {

    private lateinit var application: Application

    @JvmStatic
    fun initSentry(app: Application) {
        application = app
        initSentry()

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(ProtonExceptionHandler(currentHandler))
    }

    private fun initSentry() {
        val sentryDsn = BuildConfig.SENTRY_DSN_NEW
        SentryAndroid.init(application) { options ->
            options.dsn = sentryDsn
            options.release = BuildConfig.VERSION_NAME
            options.isAnrEnabled = true
            options.isAttachStacktrace = true
            options.isEnableAutoSessionTracking = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            /*options.setBeforeSend { event, _ ->
                // filter out events
            }*/
        }
        // Sentry.setUser(User().apply { id = getInstallationId() } )
    }


}