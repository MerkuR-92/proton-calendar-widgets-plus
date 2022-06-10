package me.proton.android.calendar.presentation.importAssistant.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarMappingEntity
import me.proton.android.calendar.data.api.ExternalCalendarEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

@HiltViewModel
class ImportAssistantViewModel @Inject constructor(
    application: Application,
    private val logger: Logger,
    private val accountManager: AccountManager,
    private val userManager: UserManager,
    private val importerApi: ImporterApi,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase
) : AndroidViewModel(application) {

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private lateinit var importerId: String

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    suspend fun getDefaultUserEmail(): String? {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return null
            _userId.value = userId
        }
        val userAddresses = userManager.getAddressesOrNull(userId) ?: emptyList()

        val defaultUserEmail = userManager.getUser(userId).email
        val defaultUserAddress =
            if (defaultUserEmail != null) {
                userAddresses.find {
                    ProtonUtilsImpl.canonicalizeProtonEmail(
                        it.email,
                        forceCanonicalization = true
                    ) == ProtonUtilsImpl.canonicalizeProtonEmail(
                        defaultUserEmail,
                        forceCanonicalization = true
                    )
                }
            } else null

        return if (defaultUserEmail != null && defaultUserAddress != null && defaultUserAddress.enabled && defaultUserAddress.canReceive && defaultUserAddress.canSend) {
            defaultUserEmail
        } else {
            val userEmails = userAddresses.filter { it.enabled && it.canSend && it.canReceive }.sortedBy { it.order }.map { it.email }
            userEmails.firstOrNull()
        }
    }

    suspend fun handleGoogleSignInRedirect(userId: UserId, code: String): Pair<String, List<ExternalCalendarEntity>>? {
        // Create Access token resource
        when (val createAccessTokenApiResponse = importerApi.createAccessToken(userId, code)) {
            is ApiResponse.Success -> {
                val tokenId = createAccessTokenApiResponse.data.token.id
                val account = createAccessTokenApiResponse.data.token.account

                // Create the importer for the required products
                when (val createCalendarImporterApiResponse = importerApi.createCalendarImporter(userId, tokenId)) {
                    is ApiResponse.Success -> {
                        importerId = createCalendarImporterApiResponse.data.importerID

                        // Get all the importer mapping info
                        when (val getCalendarImportMappingInfoApiResponse = importerApi.getCalendarImportMappingInfo(userId, importerId)) {
                            is ApiResponse.Success -> {
                                getCalendarImportMappingInfoApiResponse.data.calendars

                                // Return the list of calendars
                                return Pair(account, getCalendarImportMappingInfoApiResponse.data.calendars)
                            }
                            is ApiResponse.Error -> {
                                logger.e(getCalendarImportMappingInfoApiResponse.error)
                                return null
                            }
                            is ApiResponse.Exception -> {
                                logger.e(getCalendarImportMappingInfoApiResponse.exception.message ?: "(no exception message)")
                                return null
                            }
                        }
                    }
                    is ApiResponse.Error -> {
                        logger.e(createCalendarImporterApiResponse.error)
                        return null
                    }
                    is ApiResponse.Exception -> {
                        logger.e(createCalendarImporterApiResponse.exception.message ?: "(no exception message)")
                        return null
                    }
                }
            }
            is ApiResponse.Error -> {
                logger.e(createAccessTokenApiResponse.error)
                return null
            }
            is ApiResponse.Exception -> {
                logger.e(createAccessTokenApiResponse.exception.message ?: "(no exception message)")
                return null
            }
        }
    }

    suspend fun startImport(customCalendarMapping: Boolean, importCalendarMappingList: List<ImportCalendarMapping>) {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return
            _userId.value = userId
        }

        // Create new calendars
        importCalendarMappingList.forEachIndexed { index, importCalendarMapping ->
            if (importCalendarMapping.createDestinationCalendar && importCalendarMapping.destinationId == null) {
                val newCalendarId = createCalendar(importCalendarMapping.destinationName, importCalendarMapping.destinationEmail, importCalendarMapping.destinationColor)
                importCalendarMappingList[index].destinationId = newCalendarId
            }
        }

        val calendarMapping = importCalendarMappingList.mapNotNull {
            if (it.destinationId != null) {
                CalendarMappingEntity(
                    source = it.sourceId,
                    destination = it.destinationId!!
                )
            } else null
        }
        logger.e("Test test calendarMapping $calendarMapping")
        when (val startImporterApiResponse = importerApi.startImporter(userId, importerId, customCalendarMapping, calendarMapping)) {
            is ApiResponse.Success -> {
                logger.e("Test test Import started")
                // TODO
            }
            is ApiResponse.Error -> {
                logger.e(startImporterApiResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e(startImporterApiResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun createCalendar(calendarName: String, calendarEmail: String, calendarColor: Int): String? {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return null
            _userId.value = userId
        }

        // Create calendar
        val createCalendarResult = createCalendarUseCase.execute(
            userId = userId,
            name = calendarName,
            color = calendarColor,
            email = calendarEmail
        )
        if (createCalendarResult !is UseCase.Result.Success<*>) {
            createCalendarResult.ifSuccessAndLogErrors(logger) {}
            return null
        }
        logger.e("Test test created calendar $calendarName")

        createCalendarResult.returnValue.tryCast<String> {
            val calendarId = this

            // Update newly created calendar settings
            val defaultPartDayAlarms = arrayListOf(CalendarForm.DEFAULT_PART_DAY_ALARM)
            if (FeatureFlag.ADD_EMAIL_NOTIFICATIONS) defaultPartDayAlarms.add(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM)
            val defaultAllDayAlarms = arrayListOf(CalendarForm.DEFAULT_ALL_DAY_ALARM)
            if (FeatureFlag.ADD_EMAIL_NOTIFICATIONS) defaultAllDayAlarms.add(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM)

            val updateCalendarSettingsUseCaseResult = updateCalendarSettingsUseCase.updateCalendarSettings(
                userId,
                calendarId,
                CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first(),
                defaultPartDayAlarms,
                defaultAllDayAlarms
            )
            if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*>) {
                // TODO HANDLE ERROR
                return calendarId // Calendar has still been created
            }
            logger.e("Test test updated calendar settings for $calendarName")

            return calendarId
        }

        return null
    }
}
