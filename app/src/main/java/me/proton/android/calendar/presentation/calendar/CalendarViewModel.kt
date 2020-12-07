package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.proton.android.calendar.common.UseCaseWorker
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.User
import me.proton.android.calendar.domain.usecase.DeleteEventUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val MAX_CALENDAR_INDICATORS = 5

class CalendarViewModel(
    private val context: Context,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val logger: Logger) : ViewModel() {

    private var viewModelJob = Job() // TODO extract this to superclass
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    var initialised = false

    private lateinit var userId: UserId

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val _timeZoneId: MutableLiveData<ZoneId> = MutableLiveData()
    val timeZoneId: LiveData<ZoneId> = _timeZoneId

    private val _startWeekOn: MutableLiveData<DayOfWeek> = MutableLiveData()
    val startWeekOn: LiveData<DayOfWeek> = _startWeekOn

    private val _timeFormatIs24Hour: MutableLiveData<Boolean> = MutableLiveData()
    val timeFormatIs24Hour: LiveData<Boolean> = _timeFormatIs24Hour

    val initialToday: LocalDate = LocalDate.now()

    private val _selectedDate: MutableLiveData<LocalDate> = MutableLiveData()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    val lifeCycleScope: CoroutineScope = this.viewModelScope

    val fetchingEvents: MutableLiveData<String> = MutableLiveData(null)

    suspend fun getActiveCalendars(): List<CalendarEntity> {
        return calendarsRepository.getActiveCalendars(userId.id).filter { it.isActive }
    }

    fun selectActiveCalendars(): LiveData<List<CalendarEntity>> {
        return calendarsRepository.flowCalendars(userId.id).map { calendars ->
            calendars.filter { it.isActive }
        }.asLiveData(Dispatchers.Default)
    }

    fun selectDisabledCalendars(): LiveData<List<CalendarEntity>> {
        return calendarsRepository.flowCalendars(userId.id).map { calendars ->
            calendars.filter { it.isDisabled }
        }.asLiveData(Dispatchers.Default)
    }

    suspend fun selectUser(): User? {
        return usersRepository.selectUserById(userId.id)
    }

    // TODO go back to UserId as String
    suspend fun initForUser(userId: UserId): Flow<CalendarsRepository.InitingState> {
        return flow {
            // TODO make this prettier
            val calendarUserSettings = calendarsRepository.selectCalendarUserSettings(userId.id)
            if (calendarUserSettings == null) logger.e("initForUser: calendarUserSettings was null")
            val timeZone = calendarUserSettings?.primaryTimezone
            if (timeZone == null) logger.e("initForUser: timeZone was null")

            _timeZoneId.postValue(ZoneId.of(timeZone))
            _startWeekOn.postValue(usersRepository.selectUserSettings(userId.id)?.weekStartDayOfWeek()!!)
            _timeFormatIs24Hour.postValue(usersRepository.selectUserSettings(userId.id)
                    ?.timeFormatIs24Hour(DateFormat.is24HourFormat(context))!!)

            this@CalendarViewModel.userId = userId

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

    fun handleDaySelected(date: LocalDate) {

        _selectedDate.postValue(date)

        // adjust Mini Calendar
        val monthsOffset = ChronoUnit.MONTHS.between(initialToday.withDayOfMonth(1), date.withDayOfMonth(1)).toInt()
        val miniCalendarIndex = (miniCalendarPager.adapter as MiniCalendarPagerAdapter).startingPosition + monthsOffset
        if (miniCalendarPager.currentItem != miniCalendarIndex) {
            // smooth-scroll only when switching between adjacent months
            miniCalendarPager.setCurrentItem(miniCalendarIndex, Math.abs(miniCalendarPager.currentItem - miniCalendarIndex) == 1)
        }

        // adjust Agenda
        val agendaAdapter = (agendaPager.adapter as? AgendaPagerAdapter)
        if (agendaAdapter != null) {
            val selectedDayOffset = ChronoUnit.DAYS.between(agendaAdapter.startingDate, date).toInt()
            val agendaIndex = agendaAdapter.startingPosition + selectedDayOffset

            if (agendaPager.currentItem != agendaIndex) {
                agendaPager.setCurrentItem(agendaIndex, false)
            }
        }

    }

    private fun calculateCalendarIndicators(events: List<Event>): Map<LocalDate, List<String>> {

        val indicators = mutableMapOf<LocalDate, MutableSet<String>>().withDefault { mutableSetOf() }

        val timeZoneId = timeZoneId.value?.id
        if (timeZoneId == null) {
            logger.e("timeZoneId was null in CalendarViewModel calculateCalendarIndicators")
            return HashMap()
        }
        events.forEach { event ->
            var start = event.getActualStart(timeZoneId)!!.toLocalDate()
            val end = event.getActualEnd(timeZoneId)!!.toLocalDate()

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

    fun eventsLiveData(fromDate: LocalDate, toDate: LocalDate): LiveData<List<Event>?> {
        return liveData<List<Event>?> {
            val timeZoneId = timeZoneId.value?.id
            if (timeZoneId == null) {
                logger.e("timeZoneId was null in CalendarViewModel calculateCalendarIndicators")
                return@liveData
            }
            emitSource(calendarsRepository.eventsFlow(fromDate, toDate, timeZoneId).asLiveData(Dispatchers.Default))
        }
    }

    fun calendarIndicators(fromDate: LocalDate, toDate: LocalDate): LiveData<Map<LocalDate, List<String>>> {
        return eventsLiveData(fromDate, toDate).map {
            it?.let {
                calculateCalendarIndicators(it)
            } ?: emptyMap()
        }
    }

    val fetchingState: Flow<CalendarsRepository.FetchingState> = calendarsRepository.fetchingState

    suspend fun fetchEvents(fromDate: LocalDate,
                            toDate: LocalDate,
                            timeZoneId: String) {

        withContext(Dispatchers.IO) {
            calendarsRepository.fetchEvents(userId, fromDate, toDate, timeZoneId)
        }
    }

    suspend fun handleDeleteEvent(eventId: String,
                                  deleteOption: EventEditDeleteOption,
                                  occurrenceNumber: Int? = null): UseCase.Result {

        // TODO ÜBER IMPORTANT -- FIXME, PUT INTO WORKER!!!!!!!
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
        withContext(Dispatchers.IO) {
            calendarsRepository.updateCalendar(userId.id, calendarEntity)
        }
    }

    fun updateServerCalendarListDisplay(calendars: List<CalendarEntity>) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_CALENDAR_LIST,
                    UseCaseWorker.INPUT_USER_ID to userId.id
                )
            )
            .build()

        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_CALENDAR_LIST, ExistingWorkPolicy.REPLACE, work).state
    }

    fun updateServerCalendar(
        calendarId: String) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_CALENDAR,
                    UseCaseWorker.INPUT_USER_ID to userId.id,
                    UseCaseWorker.INPUT_CALENDAR_ID to calendarId
                )
            )
            .build()

        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_CALENDAR, ExistingWorkPolicy.REPLACE, work).state
    }
}
