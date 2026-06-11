package me.proton.android.calendar.init

import android.content.Context
import androidx.startup.Initializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CancellationException
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.logging.SentryUserObserver
import me.proton.core.configuration.EnvironmentConfigurationDefaults
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.util.android.sentry.TimberLoggerIntegration
import me.proton.core.util.android.sentry.project.AccountSentryHubBuilder

class SentryInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN_NEW ?: ""
            options.release = BuildConfig.VERSION_NAME
            options.isAnrEnabled = true
            options.isAttachStacktrace = true
            options.isEnableAutoSessionTracking = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.environment =
                "${if (BuildConfig.DEBUG) "debug" else "release"}\\${EnvironmentConfigurationDefaults.apiHost}"

            options.addIntegration(
                TimberLoggerIntegration(
                    minEventLevel = SentryLevel.ERROR,
                    minBreadcrumbLevel = SentryLevel.INFO
                )
            )

            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (isExpectedNoise(event.throwable)) null else event
            }
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SentryInitializerEntryPoint::class.java
        )
        entryPoint.observer().start()

        entryPoint.accountSentryHubBuilder().invoke(
            sentryDsn = BuildConfig.ACCOUNT_SENTRY_DSN.takeIf { !BuildConfig.DEBUG }.orEmpty()
        ) { options ->
            options.isEnableUncaughtExceptionHandler = false // send uncaught exceptions to Calendar sentry, not Core
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private companion object {
        // transient/handled API failures (auth refresh, not-found, rate-limit, backend down) and
        // force-update (5003/5005, handled by ForceUpdateActivity): not captured as Sentry errors,
        // they survive only as breadcrumbs on whatever real error fires next
        val DROP_HTTP = setOf(401, 403, 404, 408, 429, 503, 504)
        val DROP_PROTON = setOf(5003, 5005)

        fun isExpectedNoise(t: Throwable?): Boolean = when {
            t is CancellationException -> true
            t is ApiException -> (t.error as? ApiResult.Error.Http)
                ?.let { it.httpCode in DROP_HTTP || it.proton?.code in DROP_PROTON } ?: false
            else -> false
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SentryInitializerEntryPoint {
        fun accountSentryHubBuilder(): AccountSentryHubBuilder
        fun observer(): SentryUserObserver
    }
}
