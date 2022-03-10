package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.logger.SentryIntegration
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ReportsApiRequest
import me.proton.android.calendar.domain.api.ReportsApi
import me.proton.core.domain.entity.UserId

class SendBugReportUseCase(
    private val defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider,
    private val reportsApi: ReportsApi
) {

    companion object {
        const val WORKER_ID = "SEND_BUG_REPORT"
    }

    suspend fun execute(
        userId: UserId,
        osName: String,
        osVersion: String,
        client: String,
        appVersionName: String,
        title: String,
        description: String,
        username: String,
        email: String
    ): UseCase.Result {

        val installationId = SentryIntegration.getInstallationId(defaultSharedPreferencesProvider.sharedPreferences)

        val reportsApiRequest = ReportsApiRequest(
            osName,
            osVersion,
            client,
            appVersionName,
            title,
            "$description\n\nSentry user ID: $installationId",
            username,
            email)

        return when (val reportsApiResponse = reportsApi.sendReport(userId, reportsApiRequest)) {
            is ApiResponse.Success -> {
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> UseCase.Result.Error(reportsApiResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(reportsApiResponse.exception.message ?: "(no exception message)")
        }
    }
}
