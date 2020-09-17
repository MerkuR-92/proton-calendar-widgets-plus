package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.pager_mini_calendar.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.visibleOrInvisible
import org.koin.core.KoinComponent
import java.time.LocalDate


class ItemMiniCalendarFragment(
    val calendarViewModel: CalendarViewModel,
    val position: Int,
    val date: LocalDate
) : Fragment(), KoinComponent {


//    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.item_mini_calendar_fragment, container, false)
        //rootView.findViewById<TextView>(R.id.text_date_header).text = "${date.format(showDayOfWeek = true)}"

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TimberLogger.d("mini calendar onViewCreated: $date")

        rv_mini_calendar.apply {
//            setHasFixedSize(true) // TODO
            layoutManager = GridLayoutManager(
                this@ItemMiniCalendarFragment.context,
                MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW /*TODO*/
            )
            adapter = MiniCalendarItemAdapter(
                calendarViewModel.timeZoneId.id,
                date,
                calendarViewModel.startWeekOn
            ) {
                calendarViewModel.handleDaySelected(it)
            }

//            calendarViewModel.handleMiniCalendarDaySelected(calendarViewModel.selectedDate ?: date)

            (this.adapter as MiniCalendarItemAdapter).initialise()
        }

        calendarViewModel.selectedDate.observe(viewLifecycleOwner) {
            (rv_mini_calendar.adapter as MiniCalendarItemAdapter).markDayAsSelected(it)
        }


        lifecycleScope.launch(Dispatchers.Default) {


            calendarViewModel.eventsFlow(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth())).collect { events ->

                TimberLogger.d("sss events in flow ${date.month}: ${events.size}")


                TimberLogger.d("zzz observed events arrived in flow FOR MINI CALENDAR ${date.month}: ${events.size}")

                val indicators = calendarViewModel.calculateCalendarIndicators(events)
                (rv_mini_calendar.adapter as MiniCalendarItemAdapter).submitCalendarIndicators(date.month, indicators)

            }
        }

        calendarViewModel.eventsLiveData(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth())).observe(viewLifecycleOwner) { events ->
//
            TimberLogger.d("sss events in livedata ${date.month}: ${events.size}")
//
        }

        try {
//            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//            val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

//            TimberLogger.d("binding live data for events from calendar $calendarId")
//            calendarViewModel.eventsLiveData(date).observe(viewLifecycleOwner, Observer {
//                TimberLogger.d("observed events arrived: $it")
//                (recyclerView.adapter as? EventAdapter)?.submitList(it)
//            })



//            lifecycleScope.launch(Dispatchers.Default) {
//
//                calendarViewModel.prefetchEvents(date, date, calendarViewModel.timeZoneId.id)
//                TimberLogger.d("requesting prefetch for date: $date timezone: ${calendarViewModel.timeZoneId.id}")
//
//                calendarViewModel.eventsFlow(date).collect {
//                    TimberLogger.d("observed events arrived in flow, item agenda fragment: ${it.size}")
//                    withContext(Dispatchers.Main) {
//                        (rv_agenda.adapter as? EventAdapter)?.submitList(listOf(fakeHeaderEvent).plus(it))
//                    }
//                }
//            }

        } catch (e: Exception) {}

    }

}
