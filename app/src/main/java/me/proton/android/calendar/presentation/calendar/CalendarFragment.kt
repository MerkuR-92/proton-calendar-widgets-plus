package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.ICalUtils
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.android.ext.android.inject
import java.time.LocalDate
import java.util.*

class CalendarFragment : Fragment() {

    private val calendarViewModel: CalendarViewModel by inject()


    // TODO only for testing now vvvvvv
    private val loginUserUseCase: LoginUserUseCase by inject()
    private val bootstrapUseCase: BootstrapCalendarsUseCase by inject()
    // TODO ^^^^^
    private val fetchEventsUseCase: FetchEventsUseCase by inject()
    private val calendarsRepository: CalendarsRepository by inject()


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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
//        requireActivity().menuInflater.inflate(R.menu.fragment_calendar, menu)
        super.onCreateOptionsMenu(menu, inflater)
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
                GlobalScope.launch {

                    //loginUserUseCase.execute("adamtst", "123".toByteArray())
                    //bootstrapUseCase.execute("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ==")


                    val valueStore = valueStoreProvider.provideValueStore("TODO LOGIN")// TODO

                    val defaultCalendarId = calendarsRepository.getDefaultCalendarId(valueStore.getString("USERID")!!)

                    // TODO GET RID OF THIS CODE AND MOVE TO WORKER
                    val result = if (valueStore.getString("USERID") != null && defaultCalendarId != null) {
                        fetchEventsUseCase.execute(valueStore.getString("USERID")!!, listOf(
                            //"EbnnK81_v-QVK1qxxV4xT1O3amvVcnD4pvW3mRuHnj1591KY3oFwQILTptr1_ZiWx_WKmBQhZXp9fWux83dM5w==",
                            defaultCalendarId
                        ), LocalDate.now().minusDays(14), LocalDate.now().plusDays(14), TimeZone.getDefault().id /*TODO get it from settings*/)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Please login again", Toast.LENGTH_LONG).show()
                        }
                        UseCase.Result.Error("error fetching events in Main Activity")
                    }

                    withContext(Dispatchers.Main) {
                        if (result == UseCase.Result.Success) {
                            Toast.makeText(requireContext(), "events fetched", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "error fetching events", Toast.LENGTH_LONG).show()
                        }
                    }

                }


//                LoginUserUseCase(TimberLogger, UsersApiImpl(GsonCommon.gson, TimberLogger, context?.applicationContext!!),
//                    AddressesApiImpl(GsonCommon.gson, TimberLogger, context?.applicationContext!!),
//                    ValueStoreProviderImpl(SharedPreferencesProvider(context?.applicationContext!!)), CalendarsRepositoryImpl(
//                        AppDatabase(context?.applicationContext!!)), UsersRepositoryImpl(AppDatabase.invoke(context?.applicationContext!!), GsonCommon.gson)).execute() // password and username, right? don't persist it in worker
//

//                calendarViewModel.TEST_CREATE_EVENT_TODO()
                // TODO this is hijacked by MainActivity anyway
                true
            }
            R.id.action_create_event -> {

                requireActivity().findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toEventCreate(LocalDate.now(), ICalUtils.generateEventStartTime())) /*TODO take it from click on calendar*/

//                TimberLogger.d("blabla")
//                findNavController().navigate(Navigation.Deeplink.toEventCreate("IXFh2TE4LI11sd0GYf94r7fddHNMdZvicfoWMACCjPTS-oNjpBjeclhKlIs6N48-GB5w-zM6uqX_9HFgEnzhYQ=="))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
