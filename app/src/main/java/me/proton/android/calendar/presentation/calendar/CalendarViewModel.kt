package me.proton.android.calendar.presentation.calendar

import androidx.lifecycle.*
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.DeleteEventUseCase
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.proton.android.calendar.common.TestsLogger
import me.proton.android.calendar.domain.usecase.UseCase
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

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

    val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
    //            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//    val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

    suspend fun selectActiveCalendars(): List<CalendarEntity> {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        return calendarsRepository.getActiveCalendars(TODOuserID).filter { it.isActive }
    }


    init {

        viewModelScope.launch {
            withContext(Dispatchers.Default) {

                val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
                val TODOuserID = TODOvalueStore.getString("USERID") // TODO
                if (TODOuserID != null) {



                    timeZoneId = ZoneId.of(calendarsRepository.selectUserSettings(TODOuserID)?.primaryTimezone!!)

                    TestsLogger.d("viewmodel timeZoneId = ${timeZoneId}")

                    //val defaultCalendar = calendarsRepository.getDefaultCalendarId(TODOuserID)

                    // TODO get calendars that are selected from the sidebar
                    val selectedCalendarIds = calendarsRepository.getActiveCalendars(TODOuserID).map { it.id }.toList()

                    calendarsRepository.init(selectedCalendarIds, LocalDate.now().plusMonths(10 /*TODO create more events when we switch between months*/), timeZoneId.id)

                }

            }
        }



//        viewModelScope.launch {
//            calendarsRepository.persistCalendar(CalendarEntity("test_id", "test calendar from pojo", "", "ads", 1, 1))
//        }
        // we are launching a coroutine in UI scope because it affects the UI
//        uiScope.launch {
//            _calendars.value = withContext(Dispatchers.IO) {
//
//                calendarsRepository.selectCalendars()
//            }
//        }



    }

    suspend fun eventsFlow(date: LocalDate): Flow<List<Event>> {

//        return liveData<List<Event>>(Dispatchers.IO) {

            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
            val TODOuserID = TODOvalueStore.getString("USERID") // TODO
            return if (TODOuserID != null) {
                val defaultCalendar = calendarsRepository.getDefaultCalendarId(TODOuserID)

                val selectedCalendarIds = listOf<String>(
                    //"EbnnK81_v-QVK1qxxV4xT1O3amvVcnD4pvW3mRuHnj1591KY3oFwQILTptr1_ZiWx_WKmBQhZXp9fWux83dM5w==",
                    defaultCalendar!!) // TODO

//            calendarsRepository.eventsFlow(selectedCalendarIds, date, date, timeZoneId.id).asLiveData(Dispatchers.Default)
//        emit(.first())

//                timeZoneId = ZoneId.of(calendarsRepository.selectUserSettings(TODOuserID)?.primaryTimezone!!)

                calendarsRepository.eventsFlow(date, date, timeZoneId.id)
            } else flowOf<List<Event>>()
//        }

//        return calendarsRepository.eventsFlow(listOf("jnPU5bvPhktDV035mLlDyXjp6lUvBtVKEnnp--S8AWhZqvfFKNd7TkvFMtMcPZSs0lDpH2IqUthEfF8uHrncZg=="), date, date, timeZoneId.id).asLiveData(Dispatchers.Default)


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
