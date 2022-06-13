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
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarMappingEntity
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

    suspend fun handleGoogleSignInRedirect(userId: UserId, code: String, calendarColors: IntArray): Boolean {
        // Create Access token resource
        return when (val createAccessTokenApiResponse = importerApi.createAccessToken(userId, code)) {
            is ApiResponse.Success -> {
                val tokenId = createAccessTokenApiResponse.data.token.id
                _sourceEmail.value = createAccessTokenApiResponse.data.token.account

                // Create the importer for the required products
                when (val createCalendarImporterApiResponse = importerApi.createCalendarImporter(userId, tokenId)) {
                    is ApiResponse.Success -> {
                        importerId = createCalendarImporterApiResponse.data.importerID

                        // Get all the importer mapping info
                        when (val getCalendarImportMappingInfoApiResponse = importerApi.getCalendarImportMappingInfo(userId, importerId)) {
                            is ApiResponse.Success -> {
                                val externalCalendarList = getCalendarImportMappingInfoApiResponse.data.calendars

                                val defaultUserEmail = getDefaultUserEmail() ?: run {
                                    // TODO HANDLE ERROR
                                    return false
                                }
                                val importCalendarMappingList = arrayListOf<ImportCalendarMapping>()
                                externalCalendarList.forEach {
                                    importCalendarMappingList.add(
                                        ImportCalendarMapping(
                                            importCalendar = true, // Set to true by default
                                            sourceId = it.id,
                                            sourceName = it.source,
                                            sourceEmail = createAccessTokenApiResponse.data.token.account,
                                            createDestinationCalendar = true,
                                            destinationId = null,
                                            destinationName = it.source,
                                            destinationEmail = defaultUserEmail,
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
        logger.e("Test test calendarMapping $calendarMapping")
        return when (val startImporterApiResponse = importerApi.startImporter(userId, importerId, customCalendarMapping, calendarMapping)) {
            is ApiResponse.Success -> {
                logger.e("Test test Import started")
                // TODO
                true
            }
            is ApiResponse.Error -> {
                logger.e("Test test Import error ${startImporterApiResponse.error}")
                logger.e(startImporterApiResponse.error)
                false
            }
            is ApiResponse.Exception -> {
                logger.e("Test test Import exception ${startImporterApiResponse.exception.message}")
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

    fun setImportCalendar(calendarToImport: ImportCalendarMapping, importCalendar: Boolean): Int? {
        val currentList = _importCalendarMappingList.value
        val indexOfItem = currentList?.indexOf(calendarToImport) ?: return null // TODO Handle null
        currentList[indexOfItem].importCalendar = importCalendar
        _importCalendarMappingList.value = currentList
        return indexOfItem
    }

    suspend fun setCreateNewCalendar(calendarToImport: ImportCalendarMapping, calendarColor: Int) {
        val defaultUserEmail = getDefaultUserEmail() ?: return // TODO Handle null
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
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return // TODO Handle null
        val indexOfItem = currentList.indexOf(calendarToImport)
        // Replace previous item
        currentList.removeAt(indexOfItem)
        currentList.add(indexOfItem, updatedCalendarToImport)
        _importCalendarMappingList.value = currentList
    }

    suspend fun setMergeExistingCalendar(calendarToImport: ImportCalendarMapping, calendarEntity: CalendarEntity) {
        val calendarEmail = getCalendarEmail(calendarEntity.id) ?: return // TODO Handle null
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
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return // TODO Handle null
        logger.e("Test test currentList $currentList")
        val indexOfItem = currentList.indexOf(calendarToImport)
        logger.e("Test test indexOfItem $indexOfItem")
        if (indexOfItem < 0 || indexOfItem > currentList.lastIndex) return // TODO Handle error
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
}
