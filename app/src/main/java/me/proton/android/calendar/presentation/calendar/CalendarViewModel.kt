package me.proton.android.calendar.presentation.calendar

import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.DeleteEventUseCase
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.domain.usecase.UseCase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class CalendarViewModel(private val calendarsRepository: CalendarsRepository, private val deleteEventUseCase: DeleteEventUseCase, private val createEventUseCase: EditCreateEventUseCase, private val valueStoreProvider: ValueStoreProvider) : ViewModel() {

    private var viewModelJob = Job() // TODO extract this to superclass
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

//    private lateinit var selectedCalendarIds: List<String>

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    lateinit var timeZoneId: ZoneId //ZoneId.of(TimeZone.getDefault().id) // TODO get timezone from settings OR fallback to default
    lateinit var startWeekOn: DayOfWeek

    val initialToday = LocalDate.now()

    private val _selectedDate: MutableLiveData<LocalDate> = MutableLiveData()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    private val _calendarIndicators: MutableLiveData<LocalDate> = MutableLiveData()
    val calendarIndicators: LiveData<LocalDate> = _calendarIndicators


    val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
    //            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//    val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

    suspend fun selectActiveCalendars(): List<CalendarEntity> {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        return calendarsRepository.getActiveCalendars(TODOuserID).filter { it.isActive }
    }

    suspend fun init(coroutineScope: CoroutineScope) {
        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
        val TODOuserID = TODOvalueStore.getString("USERID") // TODO
        if (TODOuserID != null) {

            timeZoneId = ZoneId.of(calendarsRepository.selectUserSettings(TODOuserID)?.primaryTimezone!!)
            startWeekOn = if (calendarsRepository.selectUserSettings(TODOuserID)?.weekStart == 7) {
                DayOfWeek.SUNDAY
            } else {
                DayOfWeek.MONDAY
            }

            TestsLogger.d("viewmodel timeZoneId = ${timeZoneId}")

            //val defaultCalendar = calendarsRepository.getDefaultCalendarId(TODOuserID)




            // TODO get calendars that are selected from the sidebar
            val selectedCalendarIds = calendarsRepository.getActiveCalendars(TODOuserID).map { it.id }.toList()


            coroutineScope.launch {
                // TODO this method never returns
                calendarsRepository.init(selectedCalendarIds, LocalDate.now().plusMonths(10 /*TODO create more events when we switch between months*/), timeZoneId.id)
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

//        return liveData<List<Event>>(Dispatchers.IO) {

            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
            val TODOuserID = TODOvalueStore.getString("USERID") // TODO
            return if (TODOuserID != null) {

                calendarsRepository.eventsFlow(date, date, timeZoneId.id)
            } else flowOf<List<Event>>()
//        }

//        return calendarsRepository.eventsFlow(listOf("jnPU5bvPhktDV035mLlDyXjp6lUvBtVKEnnp--S8AWhZqvfFKNd7TkvFMtMcPZSs0lDpH2IqUthEfF8uHrncZg=="), date, date, timeZoneId.id).asLiveData(Dispatchers.Default)


    }

    fun eventsLiveData(fromDate: LocalDate, toDate: LocalDate): LiveData<List<Event>> {
        return liveData<List<Event>> { emit(calendarsRepository.eventsFlow(fromDate, toDate, timeZoneId.id).first()) }
    }

    val fetchingState: Flow<CalendarsRepository.FetchingState> = calendarsRepository.fetchingState

    suspend fun prefetchEvents(fromDate: LocalDate,
                               toDate: LocalDate,
                               timeZoneId: String) {

        withContext(Dispatchers.IO) {
            calendarsRepository.prefetchEvents(fromDate, toDate, timeZoneId)
        }

    }



    suspend fun handleDeleteEvent(eventId: String, deleteOption: EventEditDeleteOption, occurrenceNumber: Int? = null): UseCase.Result {
        // TODO ÜBER IMPORTANT -- FIXME, PUT INTO WORKER!!!!!!!

        return viewModelScope.async {
            withContext(Dispatchers.IO) {


                val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
                val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO


                deleteEventUseCase.execute(TODOuserID, eventId, deleteOption, occurrenceNumber)
            }
        }.await()
    }




//    fun TEST_CREATE_EVENT_TODO() {
//        ioScope.launch {
//
//            createEventUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==", "m-dPNuHcP8N4xfv6iapVg2wHifktAD1A1pFDU95qo5f14Vaw8I9gEHq-3GACk6ef3O12C3piRviy_D43Wh7xxQ==")
//
//        }
//    }


}
