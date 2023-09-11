package me.proton.android.calendar.common.logger

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.API_HOST
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.core.util.android.sentry.TimberLoggerIntegration
import me.proton.core.util.android.sentry.project.AccountSentryHubBuilder
import java.util.UUID

object SentryIntegration {

    @JvmStatic
    fun getInstallationId(sharedPreferences: SharedPreferences): String =
        sharedPreferences.getString(SharedPreferencesKeys.APP_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also {
                sharedPreferences.edit(commit = true) { putString(SharedPreferencesKeys.APP_INSTALLATION_ID, it) }
            }

    @JvmStatic
    fun initSentry(app: Application, sharedPreferences: SharedPreferences) {
        val installationId = getInstallationId(sharedPreferences)
        initSentry(app, installationId)
//        initAccountSentry(app, installationId)
    }

    private fun initSentry(context: Context, installationId: String) {
        val sentryDsn = BuildConfig.SENTRY_DSN_NEW ?: ""
        SentryAndroid.init(context) { options ->
            options.dsn = sentryDsn
            options.release = BuildConfig.VERSION_NAME
            options.isAnrEnabled = true
            options.isAttachStacktrace = true
            options.isEnableAutoSessionTracking = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.environment = "${if (BuildConfig.DEBUG) "debug" else "release"}\\$API_HOST"
            options.addIntegration(
                TimberLoggerIntegration(
                    minEventLevel = SentryLevel.ERROR,
                    minBreadcrumbLevel = SentryLevel.INFO
                )
            )
        }
        Sentry.setUser(User().apply { id = installationId } )
    }

//    private fun initAccountSentry(context: Context, installationId: String) {
//        val entryPoint = EntryPointAccessors.fromApplication(
//            context.applicationContext,
//            SentryIntegration::class.java
//        )
//
//        entryPoint.accountSentryHubBuilder().invoke(
//            sentryDsn = BuildConfig.ACCOUNT_SENTRY_DSN.takeIf { !BuildConfig.DEBUG }.orEmpty(),
//            installationId = installationId
//        )
//    }
//
//    @EntryPoint
//    @InstallIn(SingletonComponent::class)
//    internal interface SentryIntegration {
//        fun accountSentryHubBuilder(): AccountSentryHubBuilder
//    }
}
