package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl
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
    private var fullWeeksInMonth = 0

    private var initialMiniCalendarLayoutParams: ConstraintLayout.LayoutParams? = null
    private var initialWeekNumbersLayoutParams: ConstraintLayout.LayoutParams? = null

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

    interface MiniCalendarPositionListener {
        fun scrollUp(scrollValue: Float, sliderTop: Int): Int?
        fun scrollDown(scrollValue: Float, sliderTop: Int): Int?
        fun resetPosition()
        fun doesViewExist(): Boolean
    }

    fun getMiniCalendarPositionListener(): MiniCalendarPositionListener {
        return object: MiniCalendarPositionListener {
            override fun scrollUp(scrollValue: Float, sliderTop: Int): Int? {
                if (rv_mini_calendar == null) return null
                if (initialMiniCalendarLayoutParams == null) initialMiniCalendarLayoutParams = rv_mini_calendar.layoutParams as ConstraintLayout.LayoutParams
                if (initialWeekNumbersLayoutParams == null) initialWeekNumbersLayoutParams = ll_weeknumbers.layoutParams as ConstraintLayout.LayoutParams

                val selectedDate = calendarViewModel.selectedDate.value ?: return rv_mini_calendar.top
                val weekStart = weekStart ?: return rv_mini_calendar.top

                val selectedDateWeekNumber = selectedDate.weekNumber(weekStart)
                val firstDayWeekNumber = selectedDate.withDayOfMonth(1).weekNumber(weekStart)
                val lastDayWeekNumber = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth()).weekNumber(weekStart)

                val selectedWeekRow = selectedDateWeekNumber - firstDayWeekNumber + 1
                val selectedWeekBottom = (selectedWeekRow * requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_height)) +
                        (selectedWeekRow - 1) * requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing) +
                        selectedWeekRow * 2 * requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_day_spacing) +
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_bottom_spacing) +
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height)

                val selectedWeekTop = selectedWeekBottom -
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_height) -
                        (selectedWeekRow - 1) * requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing) -
                        selectedWeekRow * 2 * requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_day_spacing) -
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_bottom_spacing)

                val selectedItemView = rv_mini_calendar.getChildAt((rv_mini_calendar.adapter as MiniCalendarItemAdapter).selectedIndex)

                val selectedItemViewTop = selectedItemView.top + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val headerItemBottom = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val miniCalendarCurrentTop = (rv_mini_calendar.top * -1) + headerItemBottom
                if (selectedItemView.top != 0 && selectedItemViewTop > miniCalendarCurrentTop) {
//                    logger.e("Test test drags selectedItemViewTop $selectedItemViewTop miniCalendarCurrentTop $miniCalendarCurrentTop result ${miniCalendarCurrentTop - scrollValue.toInt()}")
//                    logger.e("Test test drags selectedItemViewTop $selectedItemViewTop headerItemBottom $headerItemBottom miniCalendarCurrentTop $miniCalendarCurrentTop")
//                    logger.e("Test test drags sliderTop $sliderTop selectedItemViewTop $selectedItemViewTop  selectedWeekTop $selectedWeekTop selectedWeekBottom $selectedWeekBottom rv_mini_calendar.top ${rv_mini_calendar.top} rv_mini_calendar.height ${rv_mini_calendar.height} rv_mini_calendar.y ${rv_mini_calendar.y} rv_mini_calendar.bottom ${rv_mini_calendar.bottom}")
                    rv_mini_calendar.top -= scrollValue.toInt()
                    ll_weeknumbers.top -= scrollValue.toInt()
                    if (selectedItemViewTop < (rv_mini_calendar.top * -1) + headerItemBottom) {
                        rv_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                        ll_weeknumbers.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                    }
                } else {
//                    logger.e("Test test hides selectedItemViewTop $selectedItemViewTop miniCalendarCurrentTop $miniCalendarCurrentTop result ${miniCalendarCurrentTop - scrollValue.toInt()}")
//                    logger.e("Test test hides selectedItemViewTop $selectedItemViewTop headerItemBottom $headerItemBottom miniCalendarCurrentTop $miniCalendarCurrentTop")
//                    logger.e("Test test hides sliderTop $sliderTop selectedItemViewTop $selectedItemViewTop  selectedWeekTop $selectedWeekTop selectedWeekBottom $selectedWeekBottom rv_mini_calendar.top ${rv_mini_calendar.top} rv_mini_calendar.height ${rv_mini_calendar.height} rv_mini_calendar.y ${rv_mini_calendar.y} rv_mini_calendar.bottom ${rv_mini_calendar.bottom}")
//                    logger.e("Test test new top value ${(selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)}")
                    rv_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                    ll_weeknumbers.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                }


                return rv_mini_calendar.top
            }

            override fun scrollDown(scrollValue: Float, sliderTop: Int): Int? {
                if (rv_mini_calendar == null) return null
                if (initialMiniCalendarLayoutParams == null) initialMiniCalendarLayoutParams = rv_mini_calendar.layoutParams as ConstraintLayout.LayoutParams
                if (initialWeekNumbersLayoutParams == null) initialWeekNumbersLayoutParams = ll_weeknumbers.layoutParams as ConstraintLayout.LayoutParams

                if (rv_mini_calendar.top < 0) {
                    if (rv_mini_calendar.top + scrollValue.toInt() > 0) rv_mini_calendar.top = 0
                    else rv_mini_calendar.top += scrollValue.toInt()
                    if (ll_weeknumbers.top + scrollValue.toInt() > 0) ll_weeknumbers.top = 0
                    else ll_weeknumbers.top += scrollValue.toInt()
                }
                return rv_mini_calendar.top
            }

            override fun resetPosition() {
                if (rv_mini_calendar == null) return
                initialMiniCalendarLayoutParams?.let { rv_mini_calendar.layoutParams = it }
                initialWeekNumbersLayoutParams?.let { ll_weeknumbers.layoutParams = it }
            }

            override fun doesViewExist(): Boolean {
                return rv_mini_calendar != null
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

    private var currentMonthView: Boolean? = null
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
            view.findViewById<LinearLayout>(R.id.ll_weeknumbers).visibleOrGone(displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek, monthView: Boolean) {
        val immutableDate = date ?: return // TODO Use Position here to calculate date depending on monthView value
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return
        val firstDay =
            if (monthView) {
                immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())
            } else {
                val firstDayOfTheWeekNumber = immutableDate.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

                val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
                val firstDayOfTheWeek = immutableDate.with(temporalField, 1)
                val offset = immutablePosition - immutableStartingPosition
                firstDayOfTheWeek.plusWeeks(offset.toLong())
            }

        logger.d("mini calendar onViewCreated: $firstDay")

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
        }

        calendarViewModel.lifeCycleScope.launch {
            if (currentMonthView == false && monthView && calendarViewModel.selectedDate.value?.month != firstDay.month) {
                delay(300) // TODO This messes up the side swipe a bit if we swipe multiple months fast
            }

            // Check if view still exists after delay
            if (rv_mini_calendar == null) return@launch

            val delay = currentMonthView == null && monthView && calendarViewModel.selectedDate.value?.month != firstDay.month

            currentMonthView = monthView
            (rv_mini_calendar.adapter as MiniCalendarItemAdapter).initialise(timeZoneId, delay)
        }

        // setup week numbers
        view?.findViewById<LinearLayout>(R.id.ll_weeknumbers)?.run {
            this.removeAllViews()
            fullWeeksInMonth = MiniCalendarItemAdapter.calculateFullWeeksInMonth(firstDay, startWeekOn)
            for (i in 0 until fullWeeksInMonth) {
                val weekdayView = LayoutInflater.from(this.context).inflate(
                    R.layout.item_mini_calendar_weekday,
                    this,
                    false
                )
                val textView = weekdayView as TextView
                textView.text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
                if (i == 0) {
                    val layoutParams = textView.layoutParams as LinearLayout.LayoutParams
                    layoutParams.topMargin = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_top_first)
                    textView.layoutParams = layoutParams
                }
                addView(weekdayView)
            }
        }

        calendarViewModel.lifeCycleScope.launch {

            if (monthView) {
                val firstDayOfTheMonth = firstDay.withDayOfMonth(1)
                val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val lastDayOfTheMonth = firstDay.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
                val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

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
