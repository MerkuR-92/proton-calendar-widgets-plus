package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class ImportFromGoogleUseCase @Inject constructor(
    private val importerApi: ImporterApi,
    private val logger: Logger
) {

    companion object {
        const val WORKER_ID = "IMPORT_FROM_GOOGLE"
    }

    suspend fun execute(
        userId: UserId
    ): UseCase.Result {

        return UseCase.Result.Success<Unit>()
    }
}
