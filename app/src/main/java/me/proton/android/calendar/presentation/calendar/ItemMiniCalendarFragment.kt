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
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.TimberLogger
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.LocalDate


class ItemMiniCalendarFragment() : Fragment(), KoinComponent {
    private var position: Int? = null
    private var date: LocalDate? = null

    private val calendarViewModel: CalendarViewModel by sharedViewModel()

//    private val navigationArguments: EventFormFragmentArgs by navArgs()

    companion object {
        fun newInstance(position: Int, date: LocalDate) : ItemMiniCalendarFragment{
            return ItemMiniCalendarFragment().apply {
                arguments = Bundle().apply {
                    putInt(POSITION_ARG, position)
                    putSerializable(DATE_ARG, date)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(POSITION_ARG)
            date = it.getSerializable(DATE_ARG) as? LocalDate?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.item_mini_calendar_fragment, container, false)

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO To be tested but shouldn't happen
        val immutableDate = date ?: return

        TimberLogger.d("mini calendar onViewCreated: $immutableDate")

        TimberLogger.d("viewmodel timeZoneId (itemminicalendarfragment) = ${calendarViewModel.timeZoneId.id}")

        rv_mini_calendar.apply {
//            setHasFixedSize(true) // TODO
            layoutManager = GridLayoutManager(
                this@ItemMiniCalendarFragment.context,
                MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW /*TODO*/
            )
            adapter = MiniCalendarItemAdapter(
                calendarViewModel.timeZoneId.id,
                immutableDate,
                calendarViewModel.startWeekOn,
                calendarViewModel,
                viewLifecycleOwner
            ) {
                calendarViewModel.handleDaySelected(it)
            }

            (rv_mini_calendar.adapter as MiniCalendarItemAdapter).initialise()
        }

        calendarViewModel.lifeCycleScope.launch {

            val fromDate = immutableDate.withDayOfMonth(1)
            val toDate = immutableDate.withDayOfMonth(immutableDate.lengthOfMonth())

            TimberLogger.d("zzz requesting prefetch for date range ${fromDate} - ${toDate} in timezone: ${calendarViewModel.timeZoneId.id}")
            calendarViewModel.fetchEvents(fromDate, toDate, calendarViewModel.timeZoneId.id)

        }

    }

}
