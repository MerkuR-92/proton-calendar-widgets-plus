package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapCalendarsUseCase
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.LoginUserUseCase
import kotlinx.android.synthetic.main.fragment_calendar.*
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import me.proton.android.calendar.BuildConfig
import org.koin.android.ext.android.inject
import java.time.LocalDate

class CalendarFragment : Fragment() {

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

    private inner class CalendarAgendaAdapter(activity: FragmentActivity, val startingDate: LocalDate) : FragmentStateAdapter(activity) {

        val startingPosition = itemCount / 2

        override fun getItemCount(): Int {
            return Int.MAX_VALUE
        }

        override fun createFragment(position: Int): Fragment {
            return ItemCalendarAgendaFragment(calendarViewModel, position, startingDate.plusDays((position - startingPosition).toLong())) // TODO .getInstance(DATE RANGE) or refresh current fragment?
        }

    }

    var pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            TimberLogger.d("page selected: $position")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        pager.apply{
            val agendaAdapter = CalendarAgendaAdapter(requireActivity(), LocalDate.now())
            adapter = agendaAdapter
            offscreenPageLimit = 1 // TODO
            setCurrentItem(agendaAdapter.startingPosition, false)
        }

        pager.registerOnPageChangeCallback(pageChangeCallback)


        /*recyclerView.apply {
            //            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@CalendarFragment.context)
            adapter = EventAdapter {
                TimberLogger.d("event clicked: $it")

                findNavController().navigate(Navigation.Deeplink.toEventDetails(it.id))

                //findNavController().navigate(CalendarFragmentDirections.actionNavCalendarToNavEventDetails()) // TODO use deeplinking?

        //                val modalBottomSheet = EventDetailsFragment()
                // TODO pass arguments with userId and eventId
        //                modalBottomSheet.show(parentFragmentManager, "TODO TAG")

                // com.google.android.material.R.id.design_bottom_sheet


            }
            recyclerView.addItemDecoration(
                DividerItemDecoration(
                    this@CalendarFragment.context,
                    LinearLayoutManager.VERTICAL
                )
            )

//        view.findViewById<TextView>(R.id.textview_home_second).text =
//                getString(R.string.hello_home_second, args.myArg)
//
//        view.findViewById<Button>(R.id.button_home_second).setOnClickListener {
//            findNavController().navigate(R.id.action_HomeSecondFragment_to_HomeFragment)
        }*/

//        viewModel = ViewModelProvider(this, CalendarViewModel.ViewModelFactory(// TODO fix this!!!!
//            FakeCalendarRepositoryRepository(activity?.applicationContext!!), activity?.application!!)).get(CalendarViewModel::class.java)








    }

//    override fun onResume() {
//        super.onResume()
//
//
//        try {
//            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
////            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//            val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")
//
//            TimberLogger.d("binding live data for events from calendar $calendarId")
//            calendarViewModel.events(startingDate, ).observe(viewLifecycleOwner, Observer {
//                (recyclerView.adapter as? EventAdapter)?.submitList(it)
//            })
//
//        } catch (e: Exception) {}
//
//
//    }

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
