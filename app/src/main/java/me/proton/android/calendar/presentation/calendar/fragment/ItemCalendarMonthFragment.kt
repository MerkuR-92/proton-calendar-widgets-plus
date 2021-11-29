package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.item_calendar_month_fragment.*
import kotlinx.android.synthetic.main.item_month_view_grid.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.DayOfWeek
import java.time.LocalDate

class ItemCalendarMonthFragment : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    companion object {
        fun newInstance(position: Int, startingPosition: Int, date: LocalDate): ItemCalendarMonthFragment {
            return ItemCalendarMonthFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArguments.POSITION_ARG, position)
                    putInt(FragmentArguments.STARTING_POSITION_ARG, startingPosition)
                    putSerializable(FragmentArguments.DATE_ARG, date)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(FragmentArguments.POSITION_ARG)
            startingPosition = it.getInt(FragmentArguments.STARTING_POSITION_ARG)
            date = it.getSerializable(FragmentArguments.DATE_ARG) as? LocalDate?
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.item_calendar_month_fragment, container, false)


        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return
        val firstDayMonthView = immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())

        view.findViewById<TextView>(R.id.monthFragmentWeekDay1).text = "M"
        view.findViewById<TextView>(R.id.monthFragmentWeekDay2).text = "T"
        view.findViewById<TextView>(R.id.monthFragmentWeekDay3).text = "W"
        view.findViewById<TextView>(R.id.monthFragmentWeekDay4).text = "T"
        view.findViewById<TextView>(R.id.monthFragmentWeekDay5).text = "F"
        view.findViewById<TextView>(R.id.monthFragmentWeekDay6).text = "S"
        view.findViewById<TextView>(R.id.monthFragmentWeekDay7).text = "S"

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->

            monthFragmentWeekNumberLayout.visibleOrGone(displayWeekNumber)
        }

        calendarViewModel.weekStart.observe(viewLifecycleOwner) { weekStart ->
            weekStart ?: return@observe

            setupMonthViewGrid(firstDayMonthView, AndroidUtils.getWeekStartDayOfWeek(weekStart))
        }
    }

    private fun setupMonthViewGrid(forDate: LocalDate, startWeekOn: DayOfWeek) {
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset =
            if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val lastDayOfTheMonth = forDate.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())

        val firstMiniCalendarDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())

        setupWeekNumbers(firstDayOfTheMonth, startWeekOn)

        val previousMonthDayItems = (0 until firstDayOfTheWeekOffset).map {
            val date = firstDayOfTheMonth.minusDays(it.toLong() + 1)
            ItemMiniCalendarFragment.MiniCalendarItem(date, false, true, emptyList())
        }.reversed()

        val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
            val date = firstDayOfTheMonth.plusDays(it.toLong())
            ItemMiniCalendarFragment.MiniCalendarItem(date, false, true, emptyList())
        }

        val lastDayOfMonthOffset = 42 - (previousMonthDayItems.size + dayItems.size)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            .plusDays(lastDayOfMonthOffset.toLong())

        val upcomingMonthDayItems = (0 until lastDayOfMonthOffset).map {
            val date = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(it.toLong() + 1)
            ItemMiniCalendarFragment.MiniCalendarItem(date, false, true, emptyList())
        }

        // Submit month skeleton with only days and weekday names
        val skeletonList = AndroidUtils.concatenate(previousMonthDayItems, dayItems, upcomingMonthDayItems)

        view?.findViewById<GridLayout>(R.id.month_fragment_grid_layout)?.run {
            this.removeAllViews()
            var skeletonListIndex = 0
            for (rowIndex in 0 until 6) {
                for (columnIndex in 0 until 7) {

                    val row = GridLayout.spec(rowIndex, GridLayout.FILL, 1f)
                    val column = GridLayout.spec(columnIndex, GridLayout.FILL, 1f)

                    val dayItemView = LayoutInflater.from(this.context).inflate(
                        R.layout.item_month_view_grid,
                        this,
                        false
                    )

                    val layoutParams = GridLayout.LayoutParams(row, column)
                    layoutParams.topMargin = requireContext().dpToPixel(1)
                    if (rowIndex != 5) layoutParams.bottomMargin = requireContext().dpToPixel(1)
                    layoutParams.leftMargin = requireContext().dpToPixel(1)
                    layoutParams.rightMargin = requireContext().dpToPixel(1)
                    dayItemView.layoutParams = layoutParams

                    if (skeletonListIndex <= skeletonList.lastIndex) {
                        // Make sure we don't go out of bound
                        dayItemView.item_month_view_grid_date.text = skeletonList[skeletonListIndex].date.dayOfMonth.toString()
                        dayItemView.item_month_view_grid_date.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (skeletonList[skeletonListIndex].date.month != forDate.month) R.color.text_hint
                                else R.color.text_norm
                            )
                        )
                    }

                    this.addView(dayItemView)

                    skeletonListIndex++
                }
            }
        }
    }

    private fun setupWeekNumbers(firstDay: LocalDate, startWeekOn: DayOfWeek) {
        // Setup week numbers
        view?.findViewById<LinearLayout>(R.id.monthFragmentWeekNumberLayout)?.run {
            for (i in 0 until 6) {
                val weekNumberLinearLayout = this.getChildAt(i) as LinearLayout
                val weekNumberTextView = weekNumberLinearLayout.getChildAt(0) as TextView
                weekNumberTextView.text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
            }
        }
    }
}
