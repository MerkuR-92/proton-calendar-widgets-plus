package me.proton.android.calendar.presentation.calendar

import androidx.lifecycle.*
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.DeleteEventUseCase
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import me.proton.android.calendar.domain.usecase.UseCase
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class CalendarViewModel(private val calendarsRepository: CalendarsRepository, private val deleteEventUseCase: DeleteEventUseCase, private val createEventUseCase: EditCreateEventUseCase, private val valueStoreProvider: ValueStoreProvider) : ViewModel() {

    private var viewModelJob = Job() // TODO extract this to superclass
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    val timeZoneId = ZoneId.of(TimeZone.getDefault().id) // TODO get timezone from settings OR fallback to default

    val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
    //            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
    val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

    suspend fun selectCalendars(): List<CalendarEntity> {

        val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
        val TODOuserID = TODOvalueStore.getString("USERID")!! // TODO

        return calendarsRepository.selectCalendars(TODOuserID)
    }


//    fun getCalendarsFlow(): Flow<List<CalendarEntity>> = calendarsRepository.flowCalendars(/*TODO*/ "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")
//    val calendars: LiveData<List<CalendarEntity>> = calendarsRepository.calendarsFlow(/*TODO*/ "IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==").asLiveData(Dispatchers.Default)
    // TODO events need calendarID as param

    fun observeEvent(eventId: String): LiveData<Event?> = calendarsRepository.eventFlow(eventId).asLiveData(Dispatchers.Default)

//    val users = UsersRepositoryImpl(AppDatabase.invoke(application), GsonCommon.gson).usersFlow().asLiveData(Dispatchers.Default)
//    val addresses = UsersRepositoryImpl(AppDatabase.invoke(application), GsonCommon.gson).addressesFlow("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==" /*TODO*/).asLiveData(Dispatchers.Default)

//    val apps: LiveData<List<InstalledAppInfo>> = liveData {
//        This is the coroutine!
//        val apps = loadApps()
//        val appList = processApps(apps)
//        emit(appList)
//    }

//    private suspend fun loadApps():List<ResolveInfo> = withContext(Dispatchers.IO) {
//        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
//        packageManager.queryIntentActivities(intent, 0)
//    }

    init {



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

    fun events(date: LocalDate): LiveData<List<Event>> {
        val selectedCalendarIds = listOf<String>(
            //"EbnnK81_v-QVK1qxxV4xT1O3amvVcnD4pvW3mRuHnj1591KY3oFwQILTptr1_ZiWx_WKmBQhZXp9fWux83dM5w==",
            calendarId!!) // TODO
        return calendarsRepository.eventsFlow(selectedCalendarIds, date, date, timeZoneId.id).asLiveData(Dispatchers.Default)
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
