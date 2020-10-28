package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.UseCaseWorker
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.User
import me.proton.android.calendar.domain.usecase.DeleteEventUseCase
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
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
    private val createEventUseCase: EditCreateEventUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val valueStoreProvider: ValueStoreProvider) : ViewModel() {

    private var viewModelJob = Job() // TODO extract this to superclass
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    private var initialised = false

//    private lateinit var selectedCalendarIds: List<String>

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    lateinit var timeZoneId: ZoneId
    lateinit var startWeekOn: DayOfWeek
    var timeFormatIs24Hour: Boolean = false // TODO maybe use settings directly

    val initialToday = LocalDate.now()

    private val _selectedDate: MutableLiveData<LocalDate> = MutableLiveData()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    private val _calendarIndicators: MutableLiveData<LocalDate> = MutableLiveData()
    val calendarIndicators: LiveData<LocalDate> = _calendarIndicators

    val lifeCycleScope: CoroutineScope = this.viewModelScope

    val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
    private var userId: UserId? = null

    //            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//    val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

    suspend fun getActiveCalendars(): List<CalendarEntity> {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID") // TODO

        return if (TODOuserID != null) calendarsRepository.getActiveCalendars(TODOuserID).filter { it.isActive } else ArrayList()
    }

    fun selectActiveCalendars(): LiveData<List<CalendarEntity>>? {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID") // TODO

        return if (TODOuserID != null) calendarsRepository.flowCalendars(TODOuserID).map { calendars -> calendars.filter { it.isActive } }.asLiveData(Dispatchers.Default)
        else null
    }

    fun selectDisabledCalendars(): LiveData<List<CalendarEntity>>? {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID") // TODO

        return if (TODOuserID != null) calendarsRepository.flowCalendars(TODOuserID).map { calendars -> calendars.filter { it.isDisabled } }.asLiveData(Dispatchers.Default)
        else null
    }

    suspend fun selectUser(): User? {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID") // TODO

        return if (TODOuserID != null) usersRepository.selectUserById(TODOuserID) else null
    }

    suspend fun init(coroutineScope: CoroutineScope) {

        if (!initialised) {
            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
            val TODOuserID = TODOvalueStore.getString("USERID") // TODO
            if (TODOuserID != null) {
                userId = UserId(TODOuserID)

                timeZoneId = ZoneId.of(calendarsRepository.selectCalendarUserSettings(TODOuserID)?.primaryTimezone!!)
                startWeekOn = usersRepository.selectUserSettings(TODOuserID)?.weekStartDayOfWeek()!!
                timeFormatIs24Hour = usersRepository.selectUserSettings(TODOuserID)?.timeFormatIs24Hour(DateFormat.is24HourFormat(context))!!

                TimberLogger.d("viewmodel timeZoneId = ${timeZoneId}")

                //val defaultCalendar = calendarsRepository.getDefaultCalendarId(TODOuserID)




                // TODO get calendars that are selected from the sidebar
                val selectedActiveCalendarIds = calendarsRepository.getActiveCalendars(TODOuserID).map { it.id }.toList()
                val selectedDisabledCalendarIds = calendarsRepository.getDisabledCalendars(TODOuserID).map { it.id }.toList()
                val selectedCalendarIds = selectedActiveCalendarIds + selectedDisabledCalendarIds


                coroutineScope.launch {
                    // TODO this method never returns
                    val firstDayOfTheMonth = LocalDate.now(timeZoneId).withDayOfMonth(1).plusMonths(1)
                    val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
                    //calendarsRepository.init(selectedCalendarIds, TODOuserID, toDate, timeZoneId.id)
                }

                initialised = true
            }
        }

    }

    private lateinit var miniCalendarPager: ViewPager2
    private lateinit var agendaPager: ViewPager2

    fun setCalendarPagers(miniCalendarPager: ViewPager2, agendaPager: ViewPager2) {
        this.miniCalendarPager = miniCalendarPager
        this.agendaPager = agendaPager
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


    suspend fun eventsFlow(date: LocalDate): Flow<List<Event>> {
        return eventsFlow(date, date)
    }

    suspend fun eventsFlow(fromDate: LocalDate, toDate: LocalDate): Flow<List<Event>> {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID") // TODO
        return if (TODOuserID != null) {
            calendarsRepository.eventsFlow(fromDate, toDate, timeZoneId.id)
        } else {
            TimberLogger.e("eventsFlow is returning empty!!!")
            flowOf<List<Event>>()
        }

    }

    fun calculateCalendarIndicators(events: List<Event>): Map<Int, List<String>> {

        val indicators = mutableMapOf<Int, MutableSet<String>>().withDefault { mutableSetOf() }

        events.forEach { event ->
            val firstDayOfEvent = event.getActualStart(timeZoneId.id)!!.toLocalDate()
            val days = event.calculateFullDayCounter(firstDayOfEvent, timeZoneId.id)

            for (i in 0 until days.second) {
                val current = indicators.getValue(firstDayOfEvent.dayOfMonth + i)
                current.add(event.calendar.color)
                indicators.put(firstDayOfEvent.dayOfMonth + i, current)
            }
        }

        return indicators.mapValues { it.value.toList().sorted().take(MAX_CALENDAR_INDICATORS) }
    }

    private val eventsLiveDataMap = mutableMapOf<Pair<LocalDate, LocalDate>, LiveData<List<Event>>>()

    fun eventsLiveData(fromDate: LocalDate, toDate: LocalDate): LiveData<List<Event>> {
//        return eventsLiveDataMap.getOrDefault(Pair(fromDate, toDate), calendarsRepository.eventsFlow(fromDate, toDate, timeZoneId.id).asLiveData(Dispatchers.Default))
        return calendarsRepository.eventsFlow(fromDate, toDate, timeZoneId.id).asLiveData(Dispatchers.Default)
    }

    fun calendarIndicators(fromDate: LocalDate, toDate: LocalDate): LiveData<Map<Int, List<String>>> {
        return eventsLiveData(fromDate, toDate).map {
            calculateCalendarIndicators(it)
        }
    }

    val fetchingState: Flow<CalendarsRepository.FetchingState> = calendarsRepository.fetchingState

    suspend fun fetchEvents(fromDate: LocalDate,
                            toDate: LocalDate,
                            timeZoneId: String) {

        withContext(Dispatchers.IO) {
            calendarsRepository.fetchEvents(userId!!, fromDate, toDate, timeZoneId) // TODO UserId
        }

    }



    suspend fun handleDeleteEvent(eventId: String, deleteOption: EventEditDeleteOption, occurrenceNumber: Int? = null): UseCase.Result {
        // TODO ÜBER IMPORTANT -- FIXME, PUT INTO WORKER!!!!!!!

        return viewModelScope.async {
            withContext(Dispatchers.IO) {
                deleteEventUseCase.execute(userId!!, eventId, deleteOption, occurrenceNumber) // TODO UserId
            }
        }.await()
    }

    fun updateServerCalendar(
        calendarId: String,
        name: String? = null,
        description: String? = null,
        color: String? = null,
        display: Int? = null) : LiveData<Operation.State> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_SERVER_CALENDAR,
                    UseCaseWorker.INPUT_USER_ID to userId?.id, // TODO UserId
                    UseCaseWorker.INPUT_CALENDAR_ID to calendarId,
                    UseCaseWorker.INPUT_CALENDAR_NAME to name,
                    UseCaseWorker.INPUT_CALENDAR_DESCRIPTION to description,
                    UseCaseWorker.INPUT_CALENDAR_COLOR to color,
                    UseCaseWorker.INPUT_CALENDAR_DISPLAY to display
                )
            )
            .build()

        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.UPDATE_SERVER_CALENDAR, ExistingWorkPolicy.REPLACE, work).state
    }
}
