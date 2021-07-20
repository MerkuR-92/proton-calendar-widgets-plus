package me.proton.android.calendar.presentation.calendar

import android.app.Application
import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.text.format.DateFormat
import android.view.LayoutInflater
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import androidx.work.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.dialog_calendar_list.view.*
import kotlinx.android.synthetic.main.dialog_checkbox.view.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.DateTimeUtilsImpl.areTimeZoneOffsetsDifferent
import me.proton.android.calendar.common.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.FeatureFlag.WEEK_COMPONENT
import me.proton.android.calendar.common.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.usecase.*
import me.proton.core.domain.arch.mapSuccessValueOrNull
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.toBoolean
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap

private const val MAX_CALENDAR_INDICATORS = 5

class CalendarViewModel(
    application: Application,
    private val userManager: UserManager,
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val reactivateCalendarKeyUseCase: ReactivateCalendarKeyUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val logger: Logger,
    private val getCanonicalEmailsUseCase: GetCanonicalEmailsUseCase) : AndroidViewModel(application) {

    private var viewModelJob = Job() // TODO extract this to superclass
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    var initialised = false

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    var dayViewScrollYPosition: MutableLiveData<Int> = MutableLiveData(0)
    var weekViewStartingPositionAndDate: MutableLiveData<Pair<Int, LocalDate>?> = MutableLiveData(null)
    var monthViewStartingPositionAndDate: MutableLiveData<Pair<Int, LocalDate>?> = MutableLiveData(null)

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val _selectedDate: MutableLiveData<LocalDate> = MutableLiveData()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    var userCalendars: LiveData<List<CalendarEntity>> = MutableLiveData()
    var activeUserCalendars: LiveData<List<CalendarEntity>> = MutableLiveData()
    var disabledUserCalendars: LiveData<List<CalendarEntity>> = MutableLiveData()
    var inactiveUserCalendars: LiveData<List<CalendarEntity>> = MutableLiveData()
    var subscribedCalendars: LiveData<List<CalendarEntity>> = MutableLiveData()
    var calendarSubscriptions: LiveData<List<CalendarSubscriptionEntity>> = MutableLiveData()

    var timeZoneId: LiveData<ZoneId> = MutableLiveData()
    var timeFormat: LiveData<Int> = MutableLiveData()
    var autoDetectPrimaryTimezone: LiveData<Boolean> = MutableLiveData()
    var weekStart: LiveData<Int> = MutableLiveData()
    var displayWeekNumber: LiveData<Boolean> = MutableLiveData()

    var viewMode: MutableLiveData<ViewMode> = MutableLiveData(ViewMode.AGENDA)
    var monthView: MutableLiveData<Boolean> = MutableLiveData(true)
    var jumpToCurrentTime: MutableLiveData<Boolean> = MutableLiveData(false)

    var loading: MutableLiveData<Boolean> = MutableLiveData(false)
    var currentLoadingProcesses: Int = 0
    var viewPagerFragmentsLoadingState: HashMap<Int, Boolean> = hashMapOf()

    // Those addresses contain canonical email addresses
    var userAddresses: LiveData<List<UserAddress>> = MutableLiveData() // TODO Check usage of those values, make sure we compare canonical values

    val initialToday: LocalDate = LocalDate.now()

    val lifeCycleScope: CoroutineScope = this.viewModelScope

    val fetchingEvents: MutableLiveData<String> = MutableLiveData(null)

    private var updateTimeZoneDialogLastShown: LocalDate? = null
    var updatingCalendarPassphrase: Boolean = false

    var showAutoDetectPrimaryTimezone = true
    var initialAutoDetectPrimaryTimezoneValue: Boolean? = null

    var currentPosDesiredMonthHeight = 0
    var currentPosDesiredWeekHeight = 0

    suspend fun getActiveCalendars(): List<CalendarEntity> {
        val userId = userId.value?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getActiveCalendars")
            return arrayListOf()
        }
        return calendarsRepository.getActiveUserCalendars(userId).filter { it.isActive }
    }

    fun selectCalendars() {
        val userId = userId.value?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel selectDisabledCalendars")
            return
        }
        activeUserCalendars = calendarsRepository.flowActiveUserCalendars(userId).asLiveData(Dispatchers.Default)
        disabledUserCalendars = calendarsRepository.flowDisabledUserCalendars(userId).asLiveData(Dispatchers.Default)
        inactiveUserCalendars = calendarsRepository.flowInactiveUserCalendars(userId).asLiveData(Dispatchers.Default)
        userCalendars = calendarsRepository.flowUserCalendars(userId).asLiveData(Dispatchers.Default)
        subscribedCalendars = calendarsRepository.flowSubscribedCalendars(userId).asLiveData(Dispatchers.Default)
        calendarSubscriptions = calendarsRepository.flowCalendarSubscriptions().asLiveData(Dispatchers.Default)
    }

    suspend fun selectUser(): User? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel selectUser")
            return null
        }
        return userManager.getUser(userId)
    }

    // TODO go back to UserId as String
    suspend fun initForUser(userId: UserId): Flow<CalendarsRepository.InitingState> {
        return flow {

            // TODO make this prettier
            val calendarUserSettings = calendarsRepository.selectCalendarUserSettings(userId.id)
            if (calendarUserSettings == null) logger.e("CalendarViewModel initForUser: calendarUserSettings was null")
            val timeZone = calendarUserSettings?.primaryTimezone
            if (timeZone == null) {
                logger.e("CalendarViewModel initForUser: timeZone was null. Emitting error state and going back to login screen.")
                emit(CalendarsRepository.InitingState.Error)
                return@flow
            }

            userAddresses = userManager.getAddressesFlow(userId).mapSuccessValueOrNull().map {
                val canonicalEmailsAddresses = arrayListOf<UserAddress>()
                it?.forEach { address ->
                    val canonicalEmailAddress = address.copy(email = canonicalizeProtonEmail(address.email))
                    canonicalEmailsAddresses.add(canonicalEmailAddress)
                }
                canonicalEmailsAddresses
            }.asLiveData(Dispatchers.Default)

            timeZoneId = calendarsRepository.flowCalendarUserSettingsPrimaryTimezone(userId.id).map {
                if (it != null) {
                    ZoneId.of(it)
                } else {
                    ZoneId.of(timeZone)
                }
            }.asLiveData(Dispatchers.Default)

            autoDetectPrimaryTimezone = calendarsRepository.flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId.id).map {
                it?.toBoolean() ?: true // Show week numbers by default
            }.asLiveData(Dispatchers.Default)

            displayWeekNumber = calendarsRepository.flowCalendarUserSettingsDisplayWeekNumber(userId.id).map {
                it?.toBoolean() ?: true // Show week numbers by default
            }.asLiveData(Dispatchers.Default)

            timeFormat = userSettingsRepository.flowTimeFormat(userId.id).map {
                it ?: 0 // Locale default
            }.asLiveData(Dispatchers.Default)

            weekStart = userSettingsRepository.flowWeekStart(userId.id).map {
                it ?: 0 // Locale default
            }.asLiveData(Dispatchers.Default)

            this@CalendarViewModel._userId.postValue(userId)

            calendarsRepository.initForUser(userId.id, ZoneId.of(timeZone)).collect {
                when (it) {
                    CalendarsRepository.InitingState.Initing -> {
                        emit(it)
                        logger.v("initing calendars repo")
                    }
                    CalendarsRepository.InitingState.Finished -> {
                        logger.v("finished initing calendars repo")
                        initialised = true
                        emit(it)
                    }
                    CalendarsRepository.InitingState.Error -> {
                        logger.e("error in CalendarViewModel initForUser")
                        emit(it)
                    }
                }
            }

        }
    }

    suspend fun shutdown() {
        initialised = false
        calendarsRepository.shutdown()
    }

    private lateinit var miniCalendarPager: ViewPager2
    private lateinit var agendaPager: ViewPager2
    var pagersInitialised = false
    var updateSelectedLocalDate: LocalDate? = null

    fun setCalendarPagers(miniCalendarPager: ViewPager2, agendaPager: ViewPager2) {
        this.miniCalendarPager = miniCalendarPager
        this.agendaPager = agendaPager
        pagersInitialised = true
    }

    fun handleInitialDaySelection(date: LocalDate) {
        // Select specific date if it has been provided instead of default init value.
        // Lets us handle selected date when navigating back from event details / form if it was opened from a notification
        val immutableUpdateSelectedLocalDate = updateSelectedLocalDate
        if (immutableUpdateSelectedLocalDate != null) {
            handleDaySelected(immutableUpdateSelectedLocalDate)
            updateSelectedLocalDate = null
        } else handleDaySelected(date)
    }

    fun handleDaySelected(date: LocalDate, fromMonthPagerCallback: Boolean = false) {
        // prevent mini-calendar scroll from overriding selected date
        _selectedDate.value?.let {
            if (fromMonthPagerCallback && (!WEEK_COMPONENT || monthView.value == true) && it.month == date.month && it.year == date.year) {
                return
            }
            weekStart.value?.let { weekStart ->
                val startWeekOn = AndroidUtils.getWeekStartDayOfWeek(weekStart)
                if (fromMonthPagerCallback && monthView.value == false && it.weekNumber(startWeekOn) == date.weekNumber(startWeekOn) && it.year == date.year) {
                    // TODO is this early return logic really needed for week view here ?
                    return
                }
            }
        }

        _selectedDate.value = date

        // adjust Mini Calendar
        val miniCalendarIndex =
            if (monthView.value == true || !WEEK_COMPONENT) {
                val monthStartingDate = monthViewStartingPositionAndDate.value?.second ?: initialToday.withDayOfMonth(1)
                val offset = ChronoUnit.MONTHS.between(monthStartingDate, date.withDayOfMonth(1)).toInt()
                val monthStartingPosition = monthViewStartingPositionAndDate.value?.first ?: (miniCalendarPager.adapter as MiniCalendarPagerAdapter).startingPosition
                monthStartingPosition + offset
            } else {
                val startWeekOn = AndroidUtils.getWeekStartDayOfWeek(weekStart.value!!)
                val weekStartingDate = weekViewStartingPositionAndDate.value?.second ?: initialToday.withDayOfMonth(1)
                val offset = DateTimeUtilsImpl.calculateWeekNumberBetween(weekStartingDate, date, startWeekOn)
                val weekStartingPosition = weekViewStartingPositionAndDate.value?.first ?: (miniCalendarPager.adapter as MiniCalendarPagerAdapter).startingPosition
                weekStartingPosition + offset
            }
        if (miniCalendarPager.currentItem != miniCalendarIndex) {
            // smooth-scroll only when switching between adjacent months
            miniCalendarPager.post {
                miniCalendarPager.setCurrentItem(miniCalendarIndex, Math.abs(miniCalendarPager.currentItem - miniCalendarIndex) == 1)
            }
        }

        // adjust Agenda
        val agendaAdapter = if (viewMode.value == ViewMode.AGENDA) agendaPager.adapter as? AgendaPagerAdapter else agendaPager.adapter as? DayPagerAdapter
        if (agendaAdapter != null) {
            val startingDate =
                if (viewMode.value == ViewMode.AGENDA) (agendaAdapter as AgendaPagerAdapter).startingDate
                else (agendaPager.adapter as DayPagerAdapter).startingDate
            val startingPosition =
                if (viewMode.value == ViewMode.AGENDA) (agendaPager.adapter as AgendaPagerAdapter).startingPosition
                else (agendaPager.adapter as DayPagerAdapter).startingPosition

            val selectedDayOffset = ChronoUnit.DAYS.between(startingDate, date).toInt()
            val agendaIndex = startingPosition + selectedDayOffset

            if (agendaPager.currentItem != agendaIndex) {
                agendaPager.post {
                    agendaPager.setCurrentItem(agendaIndex, false)
                }
            }
        }

    }

    private fun calculateCalendarIndicators(events: List<Event>, timeZoneId: String): Map<LocalDate, List<String>> {

        val indicators = mutableMapOf<LocalDate, MutableSet<String>>().withDefault { mutableSetOf() }

        events.forEach { event ->
            var start = event.getOccurrenceStart(timeZoneId).toLocalDate()
            val end = event.getOccurrenceEnd(timeZoneId).toLocalDate()

            // Use !start.isAfter(end) to iterate inclusive
            while (!start.isAfter(end)) {
                val current = indicators.getValue(start)
                current.add(event.calendar.color)
                indicators[start] = current
                start = start.plusDays(1)

                // All day events end on next day 00:00 so we need to break loop to exclude end day
                if (event.isAllDay() && start == end) break
            }
        }

        return indicators.mapValues { it.value.toList().sorted().take(MAX_CALENDAR_INDICATORS) }
    }

    fun eventsLiveData(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): LiveData<List<Event>?> {
        return liveData<List<Event>?> {
            emitSource(calendarsRepository.eventsFlow(fromDate, toDate, timeZoneId).asLiveData(Dispatchers.Default))
        }
    }

    private fun skeletonEventsForIndicatorsLiveData(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): LiveData<CalendarsRepository.GetEventsResult<SkeletonEvent>> {
        return calendarsRepository.getSkeletonEvents(fromDate, toDate, timeZoneId).asLiveData()
    }

    fun calendarIndicators(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): LiveData<Map<LocalDate, List<String>>> {

        return if (FeatureFlag.NEW_EVENT_DECRYPTION) {

            skeletonEventsForIndicatorsLiveData(fromDate, toDate, timeZoneId).map { skeletonResult ->
                when (skeletonResult) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        emptyMap()
                    }
                    is CalendarsRepository.GetEventsResult.Success -> calculateCalendarIndicators(
                        skeletonResult.events,
                        timeZoneId
                    )
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        logger.e("exception getting skeletonEventsLiveData", skeletonResult.throwable)
                        emptyMap()
                    }
                }
            }

        } else {

            eventsLiveData(fromDate, toDate, timeZoneId).map {
                it?.let {
                    calculateCalendarIndicators(it, timeZoneId)
                } ?: emptyMap()
            }

        }
    }

    val fetchingState: Flow<CalendarsRepository.FetchingState> = calendarsRepository.fetchingState

    suspend fun fetchEvents(fromDate: LocalDate,
                            toDate: LocalDate,
                            timeZoneId: String) {

        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel fetchEvents")
            return
        }
        withContext(Dispatchers.IO) {
            calendarsRepository.fetchEvents(userId, fromDate, toDate, timeZoneId)
        }
    }

    fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, lifecycle: Lifecycle): LiveData<CalendarsRepository.GetEventsResult<Event>> {
        return calendarsRepository.getEvents(fromDate, toDate, timeZoneId).flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).asLiveData()
    }

    suspend fun handleDeleteEvent(eventId: String,
                                  deleteOption: EventEditDeleteOption,
                                  occurrenceNumber: Int? = null): UseCase.Result {

        // TODO ÜBER IMPORTANT -- FIXME, PUT INTO WORKER!!!!!!!
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel handleDeleteEvent")
            return UseCase.Result.Error("User ID was null in CalendarViewModel handleDeleteEvent")
        }

        return viewModelScope.async {
            withContext(Dispatchers.IO) {
                deleteEventUseCase.execute(userId, eventId, deleteOption, occurrenceNumber) // TODO UserId
            }
        }.await()
    }

    suspend fun updateCalendarVisibility(calendarId: String, display: Int) {
        withContext(Dispatchers.IO) {
            calendarsRepository.updateCalendarDisplay(calendarId, display)
        }
    }

    suspend fun updateCalendar(calendarEntity: CalendarEntity) {
        val userId = userId.value?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel updateCalendar")
            return
        }
        withContext(Dispatchers.IO) {
            calendarsRepository.updateCalendar(userId, calendarEntity)
        }
    }

    fun updatePrimaryTimezone(primaryTimezone: String) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_PRIMARY_TIMEZONE,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_PRIMARY_TIMEZONE to primaryTimezone
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_PRIMARY_TIMEZONE, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone: Boolean) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_AUTO_DETECT_PRIMARY_TIMEZONE to autoDetectPrimaryTimezone
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateDisplayWeekNumber(displayWeekNumber: Boolean) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_DISPLAY_WEEK_NUMBER,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_DISPLAY_WEEK_NUMBER to displayWeekNumber
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_DISPLAY_WEEK_NUMBER, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateTimeFormat(timeFormat: Int) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_TIME_FORMAT,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_TIME_FORMAT to timeFormat
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_TIME_FORMAT, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateWeekStart(weekStart: Int) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_WEEK_START,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_WEEK_START to weekStart
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_WEEK_START, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateServerCalendarListDisplay() : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_CALENDAR_LIST,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_CALENDAR_LIST, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateServerCalendar(calendarId: String) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_CALENDAR,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_CALENDAR_ID to calendarId
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_CALENDAR, ExistingWorkPolicy.REPLACE, work).state
    }

    fun sendBugReport(
        osName: String,
        osVersion: String,
        client: String,
        appVersionName: String,
        title: String,
        description: String,
        username: String,
        email: String
    ) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SEND_BUG_REPORT,
                    UseCaseWorker.INPUT_USER_ID to userId.value?.id,
                    UseCaseWorker.INPUT_OS_NAME to osName,
                    UseCaseWorker.INPUT_OS_VERSION to osVersion,
                    UseCaseWorker.INPUT_CLIENT to client,
                    UseCaseWorker.INPUT_APP_VERSION_NAME to appVersionName,
                    UseCaseWorker.INPUT_TITLE to title,
                    UseCaseWorker.INPUT_DESCRIPTION to description,
                    UseCaseWorker.INPUT_USERNAME to username,
                    UseCaseWorker.INPUT_EMAIL to email,
                )
            )
            .build()

        return WorkManager.getInstance(getApplication<Application>()).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SEND_BUG_REPORT, ExistingWorkPolicy.REPLACE, work).state
    }

    suspend fun updateInactiveCalendarsPassphrase() {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel updateInactiveCalendarsPassphrase")
            updatingCalendarPassphrase = false
            return
        }

        inactiveUserCalendars.value?.forEach { calendar ->
            if (calendar.hasUpdatePassphrase) {
                // Handle flag UPDATE_PASSPHRASE
                val reactivateCalendarKeyResult = reactivateCalendarKeyUseCase.execute(userId, calendar.id)
                reactivateCalendarKeyResult.ifSuccessAndLogErrors(logger) { }
            }
        }

        val refreshResult = calendarsRepository.refreshCalendars(userId)
        updatingCalendarPassphrase = false
    }

    suspend fun fetchCalendars(userId: UserId): List<CalendarEntity>? {
        return calendarsRepository.fetchCalendars(userId)
    }

    suspend fun getCalendarUserSettingsAutoDetectPrimaryTimezone(): Boolean {
        val userId = userId.value?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel checkLocalTimezone")
            return true // TODO Define default value for auto detect primary timezone
        }
        return calendarsRepository.selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId)?.toBoolean() ?: true
    }

    private fun checkLocalTimezone(context: Context) {
        if (LocalDate.now() == updateTimeZoneDialogLastShown) return

        updateTimeZoneDialogLastShown = LocalDate.now()
        val timeZoneId = timeZoneId.value
        timeZoneId?.let {
            val systemTimeZone = fallbackTimeZone(TimeZone.getDefault().id, fallbackToDefault = true)!!
            if (areTimeZoneOffsetsDifferent(timeZoneId.id, systemTimeZone) == true) {
                // We add tags to the timezone string argument directly because it is not supported otherwise
                val dialogMessage: Spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(
                        context.getString(R.string.update_timezone_dialog_message, "<b>${systemTimeZone}</b>"),
                        Html.FROM_HTML_MODE_COMPACT
                    )
                } else {
                    Html.fromHtml(context.getString(R.string.update_timezone_dialog_message, "<b>${systemTimeZone}</b>"))
                }

                val view = LayoutInflater.from(context)
                    .inflate(R.layout.dialog_checkbox, null, false)

                view.dialog_checkbox_header.text = dialogMessage
                view.dialog_checkbox_press.setOnClickListener {
                    view.dialog_checkbox.performClick()
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.update_timezone_dialog_title)
                    .setView(view)
                    .setPositiveButton(R.string.update_timezone_dialog_confirmation) { _, _ ->
                        updatePrimaryTimezone(systemTimeZone)
                    }
                    .setNegativeButton(R.string.update_timezone_dialog_cancel) { _, _ -> }
                    .show().setOnDismissListener {
                        if (view.dialog_checkbox.isChecked) {
                            updateAutoDetectPrimaryTimezone(false)
                        }
                    }
            }
        }
    }

    fun handleAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone: Boolean, context: Context) {
        // Use initial value to avoid showing dialog when user changes it in app settings. We only show the dialog when opening the app.
        if (initialAutoDetectPrimaryTimezoneValue == null) initialAutoDetectPrimaryTimezoneValue =
            autoDetectPrimaryTimezone
        if (showAutoDetectPrimaryTimezone && autoDetectPrimaryTimezone && initialAutoDetectPrimaryTimezoneValue == true) {
            checkLocalTimezone(context)
            showAutoDetectPrimaryTimezone = false
        }
    }

    fun timeFormatIs24Hour(context: Context): Boolean {
        return when (timeFormat.value) {
            1 -> true
            2 -> false
            else -> DateFormat.is24HourFormat(context)
        }
    }

    suspend fun getCanonicalEmails(emails: List<String>): Map<String, String?>? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getCanonicalEmails")
            return null
        }
        return getCanonicalEmailsUseCase.invoke(userId, emails)
    }

    suspend fun getCalendarDefaultEmail(calendarId: String): String? {
        return calendarsRepository.selectMembers(calendarId).firstOrNull {
            it.hasPermission(MemberEntity.Permission.SUPEROWNER)
        }?.email
    }

    fun getUserEmails(): List<String>? {
        return userAddresses.value?.map { it.email }
    }

    suspend fun getDefaultCalendarSettings(): CalendarSettingsEntity? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getDefaultCalendarSettings")
            return null
        }
        val defaultCalendarId = calendarsRepository.getDefaultCalendarId(userId.id)
        if (defaultCalendarId == null) {
            logger.e("defaultCalendarId was null in CalendarViewModel getDefaultCalendarSettings")
            return null
        }
        return calendarsRepository.selectCalendarSettings(defaultCalendarId)
    }

    fun setLoading(loading: Boolean, position: Int? = null) {
        if (loading) {
            currentLoadingProcesses++
            if (position != null) viewPagerFragmentsLoadingState[position] = true
            this.loading.value = true
        } else if (position != null) {
            if (viewPagerFragmentsLoadingState[position] == true && currentLoadingProcesses > 0) currentLoadingProcesses--
            viewPagerFragmentsLoadingState.remove(position)
        } else {
            if (currentLoadingProcesses > 0) currentLoadingProcesses--
        }
        if (currentLoadingProcesses == 0) this.loading.value = false
    }
}
