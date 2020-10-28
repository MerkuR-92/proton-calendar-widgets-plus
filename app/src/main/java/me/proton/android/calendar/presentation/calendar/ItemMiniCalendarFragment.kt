package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.TimberLogger
import org.koin.core.KoinComponent
import java.time.LocalDate


class ItemMiniCalendarFragment(
    val calendarViewModel: CalendarViewModel,
    val position: Int,
    val date: LocalDate
) : Fragment(), KoinComponent {


//    private val navigationArguments: EventFormFragmentArgs by navArgs()


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

        TimberLogger.d("viewmodel timeZoneId (itemminicalendarfragment) = ${calendarViewModel.timeZoneId.id}")

        rv_mini_calendar.apply {
//            setHasFixedSize(true) // TODO
            layoutManager = GridLayoutManager(
                this@ItemMiniCalendarFragment.context,
                MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW /*TODO*/
            )
            adapter = MiniCalendarItemAdapter(
                calendarViewModel.timeZoneId.id,
                date,
                calendarViewModel.startWeekOn,
                calendarViewModel,
                viewLifecycleOwner
            ) {
                calendarViewModel.handleDaySelected(it)
            }

            (rv_mini_calendar.adapter as MiniCalendarItemAdapter).initialise()

        }

        calendarViewModel.selectedDate.observe(viewLifecycleOwner) {
            (rv_mini_calendar.adapter as MiniCalendarItemAdapter).markDayAsSelected(it)
        }

        calendarViewModel.lifeCycleScope.launch {

            val fromDate = date.withDayOfMonth(1)
            val toDate = date.withDayOfMonth(date.lengthOfMonth())

            TimberLogger.d("zzz requesting prefetch for date range ${fromDate} - ${toDate} in timezone: ${calendarViewModel.timeZoneId.id}")
            calendarViewModel.fetchEvents(fromDate, toDate, calendarViewModel.timeZoneId.id)

        }

    }

}
