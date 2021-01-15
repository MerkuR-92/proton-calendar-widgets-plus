package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.proton.gopenpgp.constants.Constants
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.getWeekStartDayOfWeek
import me.proton.android.calendar.common.visibleOrGone
import me.proton.android.calendar.domain.Logger
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.w3c.dom.Text
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoField


class ItemMiniCalendarFragment() : Fragment(), KoinComponent {
    private var position: Int? = null
    private var date: LocalDate? = null

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

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
        return inflater.inflate(R.layout.item_mini_calendar_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            zoneId ?: return@observe
            val weekStart = calendarViewModel.weekStart.value ?: return@observe
            setupItemMiniCalendarContent(zoneId.id, getWeekStartDayOfWeek(weekStart))
        }

        calendarViewModel.weekStart.observe(viewLifecycleOwner) { weekStart ->
            weekStart ?: return@observe
            val zoneId = calendarViewModel.timeZoneId.value ?: return@observe
            setupItemMiniCalendarContent(zoneId.id, getWeekStartDayOfWeek(weekStart))
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            view.findViewById<LinearLayout>(R.id.ll_weekdays).visibleOrGone(FeatureFlag.SETTINGS_WEEK_NUMBERS && displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek) {
        val immutableDate = date ?: return
        logger.d("mini calendar onViewCreated: $immutableDate")

        rv_mini_calendar.apply {
            layoutManager = GridLayoutManager(
                this@ItemMiniCalendarFragment.context,
                MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW /*TODO*/
            )
            adapter = MiniCalendarItemAdapter(
                timeZoneId,
                immutableDate,
                startWeekOn,
                calendarViewModel,
                viewLifecycleOwner
            ) {
                calendarViewModel.handleDaySelected(it)
            }

            (rv_mini_calendar.adapter as MiniCalendarItemAdapter).initialise()
        }

        // setup week numbers
        view?.findViewById<LinearLayout>(R.id.ll_weekdays)?.run {
            val fullWeeksInMonth = MiniCalendarItemAdapter.calculateFullWeeksInMonth(immutableDate, startWeekOn)
            for (i in 0 until fullWeeksInMonth) {
                val weekdayView = LayoutInflater.from(this.context).inflate(
                    R.layout.item_mini_calendar_weekday,
                    this,
                    false
                )
                (weekdayView as TextView).text = "${immutableDate.plusWeeks(i.toLong()).get(ChronoField.ALIGNED_WEEK_OF_YEAR)}"
                addView(weekdayView)
            }
        }

        calendarViewModel.lifeCycleScope.launch {

            val fromDate = immutableDate.withDayOfMonth(1)
            val toDate = immutableDate.withDayOfMonth(immutableDate.lengthOfMonth())

            calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId)
        }
    }
}
