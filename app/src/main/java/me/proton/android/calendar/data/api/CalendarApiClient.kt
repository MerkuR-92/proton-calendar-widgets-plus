package me.proton.android.calendar.data.api

import android.os.Build
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.API_APPLICATION_NAME
import me.proton.android.calendar.common.USER_AGENT_NAME
import me.proton.android.calendar.presentation.forceupdate.ForceUpdateViewModel
import me.proton.core.network.domain.ApiClient
import javax.inject.Inject

/**
 * Implementation of ApiClient for ProtonCore.
 */
class CalendarApiClient @Inject constructor(
    private val forceUpdateViewModel: ForceUpdateViewModel
) : ApiClient {

    override val appVersionHeader = "${API_APPLICATION_NAME}_${BuildConfig.VERSION_NAME}"
    override val enableDebugLogging = BuildConfig.DEBUG
    override val shouldUseDoh get() = true
    override val userAgent: String
        get() = "${USER_AGENT_NAME}/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.BRAND} ${Build.MODEL})"
    override val timeoutSeconds: Long get() = 20L

    /**
     * Tells client to force update (this client will no longer be accepted by the API).
     *
     * @param errorMessage the localized error message the user should see.
     */
    override fun forceUpdate(errorMessage: String) {
        forceUpdateViewModel.forceUpdate(errorMessage)
    }
}
