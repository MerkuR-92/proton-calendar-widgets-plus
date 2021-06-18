package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.FragmentArguments.STARTING_POSITION_ARG
import me.proton.android.calendar.domain.Logger
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.*
import java.time.temporal.WeekFields


class ItemMiniCalendarFragment() : Fragment(), KoinComponent {
    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null
    private var monthView: Boolean? = null
    private val miniCalendarMediator = MediatorLiveData<Triple<String, DayOfWeek, Boolean>>()

    private lateinit var onFlingMiniCalendarListener: MonthFragment.OnFlingMiniCalendarListener

    class MiniCalendarGestureListener(val view: View, private val onFlingMiniCalendarListener: MonthFragment.OnFlingMiniCalendarListener): GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            if (velocityY > 0) {
                onFlingMiniCalendarListener.expandOnFling()
            } else if (velocityY < 0) {
                onFlingMiniCalendarListener.collapseOnFling()
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }
    }

    fun setOnFlingMiniCalendarListener(onFlingMiniCalendarListener: MonthFragment.OnFlingMiniCalendarListener) {
        this.onFlingMiniCalendarListener = onFlingMiniCalendarListener
    }

    companion object {
        fun newInstance(position: Int, startingPosition: Int, date: LocalDate) : ItemMiniCalendarFragment{
            return ItemMiniCalendarFragment().apply {
                arguments = Bundle().apply {
                    putInt(POSITION_ARG, position)
                    putInt(STARTING_POSITION_ARG, startingPosition)
                    putSerializable(DATE_ARG, date)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(POSITION_ARG)
            startingPosition = it.getInt(STARTING_POSITION_ARG)
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

        miniCalendarMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && weekStart != null && monthView != null) {
                miniCalendarMediator.value = Triple(timeZoneId!!, weekStart!!, monthView!!)
            }
        }
        miniCalendarMediator.addSource(calendarViewModel.weekStart) { value ->
            weekStart = value?.let { getWeekStartDayOfWeek(it) }

            if (timeZoneId != null && weekStart != null && monthView != null) {
                miniCalendarMediator.value = Triple(timeZoneId!!, weekStart!!, monthView!!)
            }
        }
        miniCalendarMediator.addSource(calendarViewModel.monthView) { value ->
            monthView = value

            if (timeZoneId != null && weekStart != null && monthView != null) {
                miniCalendarMediator.value = Triple(timeZoneId!!, weekStart!!, monthView!!)
            }
        }

        miniCalendarMediator.observe(viewLifecycleOwner) {
            it?.let { setupItemMiniCalendarContent(it.first, it.second, it.third) }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            view.findViewById<LinearLayout>(R.id.ll_weekdays).visibleOrGone(displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek, monthView: Boolean) {
        val immutableDate = date ?: return // TODO Use Position here to calculate date depending on monthView value
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return
        val firstDay =
            if (monthView) {
                immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())
            }
            else {
                val firstDayOfTheWeekNumber = immutableDate.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

                val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
                val firstDayOfTheWeek = immutableDate.with(temporalField, 1)
                firstDayOfTheWeek.plusWeeks((immutablePosition - immutableStartingPosition).toLong())
            }

        logger.d("mini calendar onViewCreated: $firstDay")

        calendarViewModel.lifeCycleScope.launch {
            if (monthView && calendarViewModel.selectedDate.value?.month != firstDay.month) {
                delay(300)
            }

            // Check if view still exists after delay
            if (rv_mini_calendar == null) return@launch

            rv_mini_calendar.apply {
                layoutManager = GridLayoutManager(
                    this@ItemMiniCalendarFragment.context,
                    MiniCalendarItemAdapter.CalendarSettings.WEEKDAYS_TO_SHOW /*TODO*/
                )

                adapter = MiniCalendarItemAdapter(
                    timeZoneId,
                    firstDay,
                    startWeekOn,
                    monthView,
                    calendarViewModel,
                    viewLifecycleOwner
                ) {
                    calendarViewModel.handleDaySelected(it)
                }

                if (this@ItemMiniCalendarFragment::onFlingMiniCalendarListener.isInitialized) {
                    val miniCalendarGestureDetector =
                        GestureDetector(requireContext(), MiniCalendarGestureListener(this, onFlingMiniCalendarListener))
                    setOnTouchListener { v, event ->
                        miniCalendarGestureDetector.onTouchEvent(event)
                    }
                }

                (rv_mini_calendar.adapter as MiniCalendarItemAdapter).initialise(timeZoneId)
            }
        }

        // setup week numbers
        view?.findViewById<LinearLayout>(R.id.ll_weekdays)?.run {
            this.removeAllViews()
            val fullWeeksInMonth = MiniCalendarItemAdapter.calculateFullWeeksInMonth(firstDay, startWeekOn)
            for (i in 0 until fullWeeksInMonth) {
                val weekdayView = LayoutInflater.from(this.context).inflate(
                    R.layout.item_mini_calendar_weekday,
                    this,
                    false
                )
                (weekdayView as TextView).text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
                addView(weekdayView)
            }
        }

        calendarViewModel.lifeCycleScope.launch {

            if (monthView) {
                val firstDayOfTheMonth = firstDay.withDayOfMonth(1)
                val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val lastDayOfTheMonth = firstDay.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
                val lastDayOfMonthWeekValue = DayOfWeek.of(lastDayOfTheMonth.dayOfWeek.value).value
                val lastDayOfMonthOffset = 7 - (startWeekOn.value + lastDayOfMonthWeekValue - 1)

                val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
                val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(lastDayOfMonthOffset.toLong())

                calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId)
            } else {
                val toDate = firstDay.plusDays(6)

                calendarViewModel.fetchEvents(firstDay, toDate, timeZoneId)
            }
        }
    }
}
