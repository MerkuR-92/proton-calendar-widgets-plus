package me.proton.android.calendar.presentation.calendar

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
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
            setHasFixedSize(true) // TODO
            layoutManager = GridLayoutManager(
                this@ItemMiniCalendarFragment.context,
                MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW /*TODO*/
            )
            adapter = MiniCalendarItemAdapter(
                calendarViewModel.timeZoneId.id,
                date,
                calendarViewModel.startWeekOn
            ) {
                TimberLogger.d("calendar adapter clicked on: ${it}")
            }

            (rv_mini_calendar.adapter as? MiniCalendarItemAdapter)?.initialise()
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
