package me.proton.android.calendar.data.api

import android.os.Build
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.API_APPLICATION_NAME
import me.proton.core.network.domain.ApiClient

/**
 * Implementation of ApiClient for ProtonCore.
 */
class CalendarApiClient : ApiClient {

    override val appVersionHeader = "${API_APPLICATION_NAME}_${BuildConfig.VERSION_NAME}"
    override val enableDebugLogging = BuildConfig.DEBUG
    override val shouldUseDoh get() = true
    override val userAgent: String
        get() = "${API_APPLICATION_NAME}/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.BRAND} ${Build.MODEL})"

    override fun forceUpdate() {
        TODO("forceUpdate not yet implemented")
    }
}
