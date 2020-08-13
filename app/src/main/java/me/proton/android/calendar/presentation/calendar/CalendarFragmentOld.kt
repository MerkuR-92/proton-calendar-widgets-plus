package me.proton.android.calendar.presentation.calendar

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.LoginUserUseCase
import kotlinx.android.synthetic.main.fragment_calendar.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CalendarFragmentOld : Fragment() {

    private val calendarViewModel: CalendarViewModel by inject()


    // TODO only for testing now vvvvvv
    private val loginUserUseCase: LoginUserUseCase by inject()
    private val bootstrapUseCase: BootstrapCalendarsUseCase by inject()
    // TODO ^^^^^
    private val fetchEventsUseCase: FetchEventsUseCase by inject()


private val valueStoreProvider: ValueStoreProvider by inject()



//    private val args: AgendaFragmentArgs by navArgs()

    // Lazy injected MySimplePresenter
//    val firstPresenter: MySimplePresenter by inject()




//    private lateinit var

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO
        // if needs login, navController.navigate(LOGIN/REGISTRATION)




//        view.findViewById<View>(R.id.button_home).setOnClickListener {
            GlobalScope.launch {

                //loginUserUseCase.execute("adamtst", "123".toByteArray())
                //bootstrapUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")

//                fetchEventsUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==",
//                    "m-dPNuHcP8N4xfv6iapVg2wHifktAD1A1pFDU95qo5f14Vaw8I9gEHq-3GACk6ef3O12C3piRviy_D43Wh7xxQ==")
            }
//
////            val action = HomeFragmentDirections
////                    .actionHomeFragmentToHomeSecondFragment("From HomeFragment")
////            NavHostFragment.findNavController(this@HomeFragment)
////                    .navigate(action)
//
//
//        }



//        view.findViewById<View>(R.id.button_login).setOnClickListener {
//
//            GlobalScope.launch {
//
//                loginUserUseCase.execute("adamtst", "123".toByteArray())
//                bootstrapUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")
//
//
//
//            }
//
//        }





//        viewModel = ViewModelProvider(this, CalendarViewModel.ViewModelFactory(// TODO fix this!!!!
//            FakeCalendarRepositoryRepository(activity?.applicationContext!!), activity?.application!!)).get(CalendarViewModel::class.java)








    }

    override fun onResume() {
        super.onResume()


        try {
            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
            val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

            TimberLogger.d("binding live data for events from calendar $calendarId")
            /*calendarViewModel.events(calendarId!!).observe(viewLifecycleOwner, Observer {
                (recyclerView.adapter as? EventAdapter)?.submitList(it)
            })*/

        } catch (e: Exception) {}


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_test -> {

                // TODO put this into worker? most likely!

//                GlobalScope.launch {
//
//                    loginUserUseCase.execute("adamtst", "123".toByteArray())
//                    bootstrapUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")
//
//                }

                TimberLogger.d("menu handled in fragment")


//                LoginUserUseCase(TimberLogger, UsersApiImpl(GsonCommon.gson, TimberLogger, context?.applicationContext!!),
//                    AddressesApiImpl(GsonCommon.gson, TimberLogger, context?.applicationContext!!),
//                    ValueStoreProviderImpl(SharedPreferencesProvider(context?.applicationContext!!)), CalendarsRepositoryImpl(
//                        AppDatabase(context?.applicationContext!!)), UsersRepositoryImpl(AppDatabase.invoke(context?.applicationContext!!), GsonCommon.gson)).execute() // password and username, right? don't persist it in worker
//

//                calendarViewModel.TEST_CREATE_EVENT_TODO()
                // TODO this is hijacked by MainActivity anyway
                true
            }
//            R.id.action_create_event -> {
//                TimberLogger.d("blabla")
//                findNavController().navigate(Navigation.Deeplink.toEventCreate("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="))
//                true
//            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
