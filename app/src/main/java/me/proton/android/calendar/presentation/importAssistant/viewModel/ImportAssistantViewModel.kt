package me.proton.android.calendar.presentation.importAssistant.viewModel

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.CalendarImport.ACCESS_TYPE
import me.proton.android.calendar.common.CalendarImport.GOOGLE_AUTH_BASE_URL
import me.proton.android.calendar.common.CalendarImport.GOOGLE_SCOPES
import me.proton.android.calendar.common.CalendarImport.PROMPT
import me.proton.android.calendar.common.CalendarImport.REDIRECT_URI
import me.proton.android.calendar.common.CalendarImport.RESPONSE_TYPE
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.utils.AndroidUtils.ellipsize
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.CalendarMappingEntity
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.data.api.ReportEntity
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
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

    private suspend fun getGoogleClientId(userId: UserId): String? {
        importerApi.getGoogleClientId(userId)
        return importerApi.getGoogleClientId(userId).valueOrNullAndLogErrors(logger)?.config?.googleClientId
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
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return null

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
        val token = importerApi.createAccessToken(userId, code).valueOrNullAndLogErrors(logger)?.token ?: return false
        val tokenId = token.id
        _sourceEmail.value = token.account

        return if (importerId != null) {
            updateImporter(userId, tokenId, importerId, token.account)
        } else {
            createImporter(userId, tokenId, token.account, calendarColors)
        }
    }

    private suspend fun createImporter(userId: UserId, tokenId: String, account: String, calendarColors: IntArray): Boolean {
        // Create the importer for the required products
        importerId = importerApi.createCalendarImporter(userId, tokenId).valueOrNullAndLogErrors(logger)?.importerID ?: return false

        // Get all the importer mapping info
        val externalCalendarList = importerApi.getCalendarImportMappingInfo(userId, importerId).valueOrNullAndLogErrors(logger)?.calendars ?: return false

        defaultUserEmail.value = getDefaultUserEmail() ?: run {
            logger.e("ImportAssistantViewModel createImporter failed to get default user email")
            return false
        }

        // Map external calendars to ImportCalendarMapping list
        val importCalendarMappingList = arrayListOf<ImportCalendarMapping>()
        externalCalendarList.forEach {
            importCalendarMappingList.add(
                ImportCalendarMapping(
                    importCalendar = true, // Set to true by default
                    sourceId = it.id,
                    sourceName = it.source.ellipsize(100),
                    sourceEmail = account,
                    createDestinationCalendar = true, // Set to true by default
                    destinationId = null, // Set to null by default (calendar has yet to be created)
                    destinationName = it.source.ellipsize(100),
                    destinationEmail = defaultUserEmail.value!!,
                    destinationColor = calendarColors.random()
                )
            )
        }
        _importCalendarMappingList.value = importCalendarMappingList

        return true
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
        importerApi.updateCalendarImporter(userId, importerId, tokenId).valueOrNullAndLogErrors(logger) ?: return false

        // Resume import
        return if (resumeImport(importerId)) {
            if (_importerList.value?.any { it.id == importerId } == true) {
                // Refresh importers list if it has the importer
                getImporters()
            }
            true
        } else false
    }

    suspend fun startImport(customCalendarMapping: Boolean, importCalendarMappingList: List<ImportCalendarMapping>): Boolean {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return false

        // Map ImportCalendarMapping list to CalendarMappingEntity list
        val calendarMapping = importCalendarMappingList.mapNotNull {
            if (it.destinationId != null) {
                CalendarMappingEntity(
                    source = it.sourceId,
                    destination = it.destinationId!!
                )
            } else null
        }

        // Start importer
        importerApi.startImporter(userId, importerId, customCalendarMapping, calendarMapping).valueOrNullAndLogErrors(logger) ?: return false
        return true
    }

    suspend fun createCalendar(calendarName: String, calendarEmail: String, calendarColor: Int): String? {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return null

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

    fun setImportCalendarMappingList(calendarsToImport: List<ImportCalendarMapping>) {
        _importCalendarMappingList.value = calendarsToImport
    }

    fun setImportCalendar(calendarToImport: ImportCalendarMapping, importCalendar: Boolean): Int? {
        val currentList = _importCalendarMappingList.value ?: return null
        val indexOfItem = currentList.indexOf(calendarToImport)
        if (indexOfItem < 0 || indexOfItem > currentList.lastIndex) return null
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

    suspend fun setMergeExistingCalendar(calendarToImport: ImportCalendarMapping, calendar: Calendar) {
        val updatedCalendarToImport = ImportCalendarMapping(
            importCalendar = true,
            sourceId = calendarToImport.sourceId,
            sourceName = calendarToImport.sourceName,
            sourceEmail = calendarToImport.sourceEmail,
            createDestinationCalendar = false,
            destinationId = calendar.id,
            destinationName = calendar.name,
            destinationEmail = calendar.email,
            destinationColor = Color.parseColor(calendar.color)
        )
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return
        val indexOfItem = currentList.indexOf(calendarToImport)
        if (indexOfItem < 0 || indexOfItem > currentList.lastIndex) return
        // Replace previous item
        currentList.removeAt(indexOfItem)
        currentList.add(indexOfItem, updatedCalendarToImport)
        _importCalendarMappingList.value = currentList
    }

    suspend fun getImporters() {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return

        val importers = importerApi.getImporters(userId).valueOrNullAndLogErrors(logger)?.importers ?: return
        _importerList.value = importers
    }

    private suspend fun getImporter(importerId: String): ImporterEntity? {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return null

        return importerApi.getImporter(userId, importerId).valueOrNullAndLogErrors(logger)?.importer
    }

    suspend fun getReports() {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return

        val reports = importerApi.getReports(userId).valueOrNullAndLogErrors(logger)?.reports ?: return
        _reportList.value = reports
    }

    suspend fun cancelImport(importId: String): Boolean {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return false

        importerApi.cancelImport(userId, importId).valueOrNullAndLogErrors(logger) ?: return false
        return true
    }

    suspend fun resumeImport(importId: String): Boolean {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return false

        importerApi.resumeImport(userId, importId).valueOrNullAndLogErrors(logger) ?: return false
        return true
    }

    suspend fun deleteReport(reportId: String) {
        val userId = _userId.value ?: accountManager.getPrimaryUserId().firstOrNull()?.let {
            _userId.value = it
            return@let it
        } ?: return

        importerApi.deleteReport(userId, reportId).valueOrNullAndLogErrors(logger) ?: return

        // Update report list
        val currentList = _reportList.value?.let { ArrayList(it) } ?: return
        currentList.removeIf { it.id == reportId }
        _reportList.value = currentList
    }
}
