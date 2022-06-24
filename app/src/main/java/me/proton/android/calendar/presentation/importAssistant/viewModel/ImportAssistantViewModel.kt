package me.proton.android.calendar.presentation.importAssistant.viewModel

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.CalendarImport
import me.proton.android.calendar.common.CalendarImport.ACCESS_TYPE
import me.proton.android.calendar.common.CalendarImport.GOOGLE_AUTH_BASE_URL
import me.proton.android.calendar.common.CalendarImport.GOOGLE_SCOPES
import me.proton.android.calendar.common.CalendarImport.PROMPT
import me.proton.android.calendar.common.CalendarImport.REDIRECT_URI
import me.proton.android.calendar.common.CalendarImport.RESPONSE_TYPE
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarMappingEntity
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.data.api.ReportEntity
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.replaceFirst
import javax.inject.Inject

@HiltViewModel
class ImportAssistantViewModel @Inject constructor(
    application: Application,
    private val logger: Logger,
    private val accountManager: AccountManager,
    private val userManager: UserManager,
    private val importerApi: ImporterApi,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val calendarsRepository: CalendarsRepository
) : AndroidViewModel(application) {

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private val _importCalendarMappingList: MutableLiveData<List<ImportCalendarMapping>> = MutableLiveData()
    val importCalendarMappingList: LiveData<List<ImportCalendarMapping>> = _importCalendarMappingList

    private val _sourceEmail: MutableLiveData<String> = MutableLiveData()
    val sourceEmail: LiveData<String> = _sourceEmail

    private val _importerList: MutableLiveData<List<ImporterEntity>> = MutableLiveData()
    val importerList: LiveData<List<ImporterEntity>> = _importerList

    private val _reportList: MutableLiveData<List<ReportEntity>> = MutableLiveData()
    val reportList: LiveData<List<ReportEntity>> = _reportList

    val defaultUserEmail: MutableLiveData<String?> = MutableLiveData()

    private lateinit var importerId: String

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private suspend fun getGoogleClientId(userId: UserId): String? {
        importerApi.getGoogleClientId(userId)
        return when (val googleClientIdApiResponse = importerApi.getGoogleClientId(userId)) {
            is ApiResponse.Success -> {
                googleClientIdApiResponse.data.config.googleClientId
            }
            is ApiResponse.Error -> {
                logger.e(googleClientIdApiResponse.error)
                null
            }
            is ApiResponse.Exception -> {
                logger.e(googleClientIdApiResponse.exception.message ?: "(no exception message)")
                null
            }
        }
    }

    /**
     * Use importerId parameter if we need to updated an existing importer
     */
    suspend fun getGoogleAuthenticationUrl(userId: UserId, importerId: String? = null): String {
        return GOOGLE_AUTH_BASE_URL +
                "scope=${GOOGLE_SCOPES}" +
                "&accessType=${ACCESS_TYPE}" +
                "&redirect_uri=${REDIRECT_URI}" +
                "&response_type=${RESPONSE_TYPE}"+
                "&client_id=${getGoogleClientId(userId)}" +
                "&prompt=${PROMPT}" +
                if (importerId.isNullOrBlank()) "" else "&state=$importerId" // Specifies any string value that your application uses to maintain state between your authorization request and the authorization server's response
    }

    private suspend fun getDefaultUserEmail(): String? {
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

    suspend fun handleGoogleSignInRedirect(userId: UserId, code: String, calendarColors: IntArray, importerId: String? = null): Boolean {
        // Create Access token resource
        return when (val createAccessTokenApiResponse = importerApi.createAccessToken(userId, code)) {
            is ApiResponse.Success -> {
                val tokenId = createAccessTokenApiResponse.data.token.id
                _sourceEmail.value = createAccessTokenApiResponse.data.token.account

                if (importerId != null) {
                    updateImporter(userId, tokenId, importerId, createAccessTokenApiResponse.data.token.account)
                } else {
                    createImporter(userId, tokenId, createAccessTokenApiResponse.data.token.account, calendarColors)
                }
            }
            is ApiResponse.Error -> {
                logger.e(createAccessTokenApiResponse.error)
                return false
            }
            is ApiResponse.Exception -> {
                logger.e(createAccessTokenApiResponse.exception.message ?: "(no exception message)")
                return false
            }
        }
    }

    private suspend fun createImporter(userId: UserId, tokenId: String, account: String, calendarColors: IntArray): Boolean {
        // Create the importer for the required products
        return when (val createCalendarImporterApiResponse = importerApi.createCalendarImporter(userId, tokenId)) {
            is ApiResponse.Success -> {
                importerId = createCalendarImporterApiResponse.data.importerID

                // Get all the importer mapping info
                when (val getCalendarImportMappingInfoApiResponse = importerApi.getCalendarImportMappingInfo(userId, importerId)) {
                    is ApiResponse.Success -> {
                        val externalCalendarList = getCalendarImportMappingInfoApiResponse.data.calendars

                        defaultUserEmail.value = getDefaultUserEmail() ?: run {
                            logger.e("ImportAssistantViewModel createImporter failed to get default user email")
                            return false
                        }
                        val importCalendarMappingList = arrayListOf<ImportCalendarMapping>()
                        externalCalendarList.forEach {
                            importCalendarMappingList.add(
                                ImportCalendarMapping(
                                    importCalendar = true, // Set to true by default
                                    sourceId = it.id,
                                    sourceName = it.source,
                                    sourceEmail = account,
                                    createDestinationCalendar = true,
                                    destinationId = null,
                                    destinationName = it.source,
                                    destinationEmail = defaultUserEmail.value!!,
                                    destinationColor = calendarColors.random()
                                )
                            )
                        }
                        _importCalendarMappingList.value = importCalendarMappingList

                        true
                    }
                    is ApiResponse.Error -> {
                        logger.e(getCalendarImportMappingInfoApiResponse.error)
                        return false
                    }
                    is ApiResponse.Exception -> {
                        logger.e(getCalendarImportMappingInfoApiResponse.exception.message ?: "(no exception message)")
                        return false
                    }
                }
            }
            is ApiResponse.Error -> {
                logger.e(createCalendarImporterApiResponse.error)
                return false
            }
            is ApiResponse.Exception -> {
                logger.e(createCalendarImporterApiResponse.exception.message ?: "(no exception message)")
                return false
            }
        }
    }

    private suspend fun updateImporter(userId: UserId, tokenId: String, importerId: String, sourceEmail: String): Boolean {

        val importer = getImporter(importerId) ?: run {
            logger.e("ImportAssistantViewModel updateImporter failed to get importer")
            return false
        }

        if (importer.account != sourceEmail) {
            logger.e("ImportAssistantViewModel updateImporter incorrect google account")
            return false
        }

        // Update the importer with the new token id
        return when (val updateCalendarImporterApiResponse = importerApi.updateCalendarImporter(userId, importerId, tokenId)) {
            is ApiResponse.Success -> {
                // Resume import
                if (resumeImport(importerId)) {
                    if (_importerList.value?.any { it.id == importerId } == true) getImporters() // Refresh importers list if it has the importer
                    true
                } else false
            }
            is ApiResponse.Error -> {
                logger.e(updateCalendarImporterApiResponse.error)
                return false
            }
            is ApiResponse.Exception -> {
                logger.e(updateCalendarImporterApiResponse.exception.message ?: "(no exception message)")
                return false
            }
        }
    }

    suspend fun startImport(customCalendarMapping: Boolean, importCalendarMappingList: List<ImportCalendarMapping>): Boolean {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return false
            _userId.value = userId
        }

        val calendarMapping = importCalendarMappingList.mapNotNull {
            if (it.destinationId != null) {
                CalendarMappingEntity(
                    source = it.sourceId,
                    destination = it.destinationId!!
                )
            } else null
        }
        return when (val startImporterApiResponse = importerApi.startImporter(userId, importerId, customCalendarMapping, calendarMapping)) {
            is ApiResponse.Success -> {
                true
            }
            is ApiResponse.Error -> {
                logger.e(startImporterApiResponse.error)
                false
            }
            is ApiResponse.Exception -> {
                logger.e(startImporterApiResponse.exception.message ?: "(no exception message)")
                false
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
                logger.i("ImportAssistantViewModel createCalendar failed to update calendar settings")
                return calendarId // Calendar has still been created
            }

            return calendarId
        }

        return null
    }

    fun setImportCalendar(calendarToImport: ImportCalendarMapping, importCalendar: Boolean): Int? {
        val currentList = _importCalendarMappingList.value
        val indexOfItem = currentList?.indexOf(calendarToImport) ?: return null
        currentList[indexOfItem].importCalendar = importCalendar
        _importCalendarMappingList.value = currentList
        return indexOfItem
    }

    suspend fun setCreateNewCalendar(calendarToImport: ImportCalendarMapping, calendarColor: Int) {
        val defaultUserEmail = defaultUserEmail.value ?: getDefaultUserEmail() ?: return
        val updatedCalendarToImport = ImportCalendarMapping(
            importCalendar = true,
            sourceId = calendarToImport.sourceId,
            sourceName = calendarToImport.sourceName,
            sourceEmail = calendarToImport.sourceEmail,
            createDestinationCalendar = true,
            destinationId = null,
            destinationName = calendarToImport.sourceName,
            destinationEmail = defaultUserEmail,
            destinationColor = calendarColor
        )
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return
        val indexOfItem = currentList.indexOf(calendarToImport)
        // Replace previous item
        currentList.removeAt(indexOfItem)
        currentList.add(indexOfItem, updatedCalendarToImport)
        _importCalendarMappingList.value = currentList
    }

    suspend fun setMergeExistingCalendar(calendarToImport: ImportCalendarMapping, calendarEntity: CalendarEntity) {
        val calendarEmail = getCalendarEmail(calendarEntity.id) ?: return
        val updatedCalendarToImport = ImportCalendarMapping(
            importCalendar = true,
            sourceId = calendarToImport.sourceId,
            sourceName = calendarToImport.sourceName,
            sourceEmail = calendarToImport.sourceEmail,
            createDestinationCalendar = false,
            destinationId = calendarEntity.id,
            destinationName = calendarEntity.name,
            destinationEmail = calendarEmail,
            destinationColor = Color.parseColor(calendarEntity.color)
        )
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return
        val indexOfItem = currentList.indexOf(calendarToImport)
        if (indexOfItem < 0 || indexOfItem > currentList.lastIndex) return
        // Replace previous item
        currentList.removeAt(indexOfItem)
        currentList.add(indexOfItem, updatedCalendarToImport)
        _importCalendarMappingList.value = currentList
    }

    private suspend fun getCalendarEmail(calendarId: String): String? {
        return calendarsRepository.selectMembers(calendarId).firstOrNull {
            it.hasPermission(MemberEntity.Permission.SUPEROWNER)
        }?.email
    }

    suspend fun getImporters() {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return
            _userId.value = userId
        }

        when (val getImportersApiResponse = importerApi.getImporters(userId)) {
            is ApiResponse.Success -> {
                _importerList.value = getImportersApiResponse.data.importers
            }
            is ApiResponse.Error -> {
                logger.e(getImportersApiResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e(getImportersApiResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun getImporter(importerId: String): ImporterEntity? {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return null
            _userId.value = userId
        }

       return when (val getImporterApiResponse = importerApi.getImporter(userId, importerId)) {
            is ApiResponse.Success -> {
                return getImporterApiResponse.data.importer
            }
            is ApiResponse.Error -> {
                logger.e(getImporterApiResponse.error)
                null
            }
            is ApiResponse.Exception -> {
                logger.e(getImporterApiResponse.exception.message ?: "(no exception message)")
                null
            }
        }
    }

    suspend fun getReports() {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return
            _userId.value = userId
        }

        when (val getReportsApiResponse = importerApi.getReports(userId)) {
            is ApiResponse.Success -> {
                _reportList.value = getReportsApiResponse.data.reports
            }
            is ApiResponse.Error -> {
                logger.e(getReportsApiResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e(getReportsApiResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun cancelImport(importId: String): Boolean {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return false
            _userId.value = userId
        }

        return when (val cancelImportApiResponse = importerApi.cancelImport(userId, importId)) {
            is ApiResponse.Success -> {
                false
            }
            is ApiResponse.Error -> {
                logger.e(cancelImportApiResponse.error)
                true
            }
            is ApiResponse.Exception -> {
                logger.e(cancelImportApiResponse.exception.message ?: "(no exception message)")
                true
            }
        }
    }

    suspend fun resumeImport(importId: String): Boolean {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return false
            _userId.value = userId
        }

        return when (val resumeImportApiResponse = importerApi.resumeImport(userId, importId)) {
            is ApiResponse.Success -> {
                true
            }
            is ApiResponse.Error -> {
                logger.e(resumeImportApiResponse.error)
                false
            }
            is ApiResponse.Exception -> {
                logger.e(resumeImportApiResponse.exception.message ?: "(no exception message)")
                false
            }
        }
    }

    suspend fun deleteReport(reportId: String) {
        var userId = userId.value
        if (userId == null) {
            userId = accountManager.getPrimaryUserId().firstOrNull() ?: return
            _userId.value = userId
        }

        when (val deleteReportApiResponse = importerApi.deleteReport(userId, reportId)) {
            is ApiResponse.Success -> {
                val currentList = _reportList.value?.let { ArrayList(it) }
                currentList?.removeIf { it.id == reportId }
                _reportList.value = currentList
            }
            is ApiResponse.Error -> {
                logger.e(deleteReportApiResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e(deleteReportApiResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
