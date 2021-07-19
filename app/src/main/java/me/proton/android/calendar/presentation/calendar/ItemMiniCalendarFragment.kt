package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.item_mini_calendar_header.view.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.DateTimeUtilsImpl
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.FeatureFlag.WEEK_COMPONENT
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.FragmentArguments.STARTING_POSITION_ARG
import me.proton.android.calendar.domain.Logger
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.ceil


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
    private val miniCalendarNoWeekModeMediator = MediatorLiveData<Pair<String, DayOfWeek>>()
    private var fullWeeksInMonth = 0

    private var selectedMiniCalendarItem: Int? = null
    private var selectedMiniCalendarWeekItem: Int? = null

    private var currentWeekFirstDay: LocalDate? = null
    private var indicators: Map<LocalDate, List<String>> = hashMapOf()

    private var initialMiniCalendarLayoutParams: ConstraintLayout.LayoutParams? = null
    private var initialWeekNumbersLayoutParams: ConstraintLayout.LayoutParams? = null

    private var currentMonthView: Boolean? = null
    private var currentMiniCalendarWeekList: List<MiniCalendarItem>? = null
    private var currentMiniCalendarMonthList: List<MiniCalendarItem>? = null

    private var adapter: MiniCalendarPagerAdapter? = null

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

        /**
         * No need to measure the adapter view, we can calculate the height because item dimensions
         * are constant.
         */
        fun calculateAdapterHeight(
            context: Context,
            firstDayOfMonth: LocalDate,
            startWeekOn: DayOfWeek,
            isMonthView: Boolean
        ): Int {
            if (!WEEK_COMPONENT && !isMonthView) return context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)

            val fullWeeksInMonth = if (isMonthView) calculateFullWeeksInMonth(firstDayOfMonth, startWeekOn) else 1

            return context.resources.getDimensionPixelSize(R.dimen.calendar_item_header_height) +
                    fullWeeksInMonth * context.resources.getDimensionPixelSize(R.dimen.calendar_item_height) +
                    (if (fullWeeksInMonth == 1) context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing) * 2
                    else (fullWeeksInMonth) * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)) +
                    fullWeeksInMonth * 2 * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_spacing) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_bottom_spacing) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)
        }

        fun calculateFullWeeksInMonth(firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek): Int {
            val firstDayOfTheWeekNumber = firstDayOfMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val dayCellsToShow = firstDayOfTheWeekOffset + firstDayOfMonth.lengthOfMonth()

            return ceil(dayCellsToShow / DAYS_IN_A_WEEK.toDouble()).toInt()
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
                if (gl_mini_calendar == null || !WEEK_COMPONENT) return null
                if (initialMiniCalendarLayoutParams == null) initialMiniCalendarLayoutParams = gl_mini_calendar.layoutParams as ConstraintLayout.LayoutParams
                if (initialWeekNumbersLayoutParams == null) initialWeekNumbersLayoutParams = ll_weeknumbers.layoutParams as ConstraintLayout.LayoutParams

                val selectedMiniCalendarItem = selectedMiniCalendarItem ?: return 0
                val selectedItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem) ?: return 0

                val selectedItemViewTop = selectedItemView.top + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val headerItemBottom = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val miniCalendarCurrentTop = (gl_mini_calendar.top * -1) + headerItemBottom

                if (selectedItemView.top != 0 && selectedItemViewTop > miniCalendarCurrentTop) {
                    // Move the mini calendar along with the scroll, to behave like it was anchored to the selected week
                    gl_mini_calendar.top -= scrollValue.toInt()
                    ll_weeknumbers.top -= scrollValue.toInt()
                    if (selectedItemViewTop < (gl_mini_calendar.top * -1) + headerItemBottom) {
                        gl_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                        ll_weeknumbers.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                    }
                } else {
                    // Just let the mini calendar be hidden by the bottom of the view
                    gl_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                    ll_weeknumbers.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                }

                return gl_mini_calendar.top
            }

            override fun scrollDown(scrollValue: Float, sliderTop: Int): Int? {
                if (gl_mini_calendar == null || !WEEK_COMPONENT) return null
                if (initialMiniCalendarLayoutParams == null) initialMiniCalendarLayoutParams = gl_mini_calendar.layoutParams as ConstraintLayout.LayoutParams
                if (initialWeekNumbersLayoutParams == null) initialWeekNumbersLayoutParams = ll_weeknumbers.layoutParams as ConstraintLayout.LayoutParams

                val headerItemBottom = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val miniCalendarCurrentTop = (gl_mini_calendar.top * -1) + headerItemBottom
                val selectedMiniCalendarItem = selectedMiniCalendarItem ?: return 0
                val selectedItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem) ?: return 0
                val selectedItemViewTop = selectedItemView.top + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)

                if (monthView == false) {

                    if (gl_mini_calendar.visibility == View.INVISIBLE && selectedItemViewTop != miniCalendarCurrentTop) {
                        gl_mini_calendar.top = (selectedItemViewTop * -1) + headerItemBottom
                        ll_weeknumbers.top = (selectedItemViewTop * -1) + headerItemBottom
                    } else if (gl_mini_calendar.top < 0) {
                        gl_mini_calendar.top += scrollValue.toInt()
                        ll_weeknumbers.top += scrollValue.toInt()
                        if (gl_mini_calendar.top > 0) {
                            gl_mini_calendar.top = 0
                            ll_weeknumbers.top = 0
                        }
                        gl_mini_calendar.visibleOrInvisible(true)
                        ll_mini_calendar_week.visibleOrInvisible(false)
                        ll_weeknumber_weekview.visibleOrInvisible(false)
                    } else if (gl_mini_calendar.top == 0 && selectedItemViewTop == headerItemBottom) {
                        gl_mini_calendar.visibleOrInvisible(true)
                        ll_mini_calendar_week.visibleOrInvisible(false)
                        ll_weeknumber_weekview.visibleOrInvisible(false)
                    }

                    if (gl_mini_calendar.top < 0) {
                        if (gl_mini_calendar.top + scrollValue.toInt() > 0) gl_mini_calendar.top = 0
                        if (ll_weeknumbers.top + scrollValue.toInt() > 0) ll_weeknumbers.top = 0
                    }
                } else {
                    if (gl_mini_calendar.top < 0) {
                        if (gl_mini_calendar.top + scrollValue.toInt() > 0) gl_mini_calendar.top = 0
                        else gl_mini_calendar.top += scrollValue.toInt()
                        if (ll_weeknumbers.top + scrollValue.toInt() > 0) ll_weeknumbers.top = 0
                        else ll_weeknumbers.top += scrollValue.toInt()
                    }
                }

                return gl_mini_calendar.top
            }

            override fun resetPosition() {
                if (gl_mini_calendar == null) return
                initialMiniCalendarLayoutParams?.let { gl_mini_calendar.layoutParams = it }
                initialWeekNumbersLayoutParams?.let { ll_weeknumbers.layoutParams = it }

                gl_mini_calendar.visibleOrInvisible(monthView == true)
                ll_mini_calendar_week.visibleOrInvisible(WEEK_COMPONENT && monthView == false)
                ll_weeknumber_weekview.visibleOrInvisible(WEEK_COMPONENT && monthView == false && calendarViewModel.displayWeekNumber.value == true)
            }

            override fun doesViewExist(): Boolean {
                return gl_mini_calendar != null
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

        if (!WEEK_COMPONENT) {
            monthView = true
            ll_mini_calendar_week.visibleOrGone(false)
            ll_weeknumber_weekview.visibleOrGone(false)

            miniCalendarNoWeekModeMediator.addSource(calendarViewModel.timeZoneId) { value ->
                timeZoneId = value?.id

                if (timeZoneId != null && weekStart != null) {
                    miniCalendarNoWeekModeMediator.value = Pair(timeZoneId!!, weekStart!!)
                }
            }
            miniCalendarNoWeekModeMediator.addSource(calendarViewModel.weekStart) { value ->
                weekStart = value?.let { getWeekStartDayOfWeek(it) }

                if (timeZoneId != null && weekStart != null) {
                    miniCalendarNoWeekModeMediator.value = Pair(timeZoneId!!, weekStart!!)
                }
            }

            miniCalendarNoWeekModeMediator.observe(viewLifecycleOwner) {
                it?.let {
                    setupItemMiniCalendarContent(it.first, it.second, true)
                }
            }
        } else {
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
                it?.let {
                    setupItemMiniCalendarContent(it.first, it.second, it.third)
                }
            }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            view.findViewById<LinearLayout>(R.id.ll_weeknumbers).visibleOrGone(displayWeekNumber)
            view.findViewById<LinearLayout>(R.id.ll_weeknumber_weekview).visibleOrGone(WEEK_COMPONENT && displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek, monthView: Boolean, forceRefresh: Boolean = false) {
        if (!forceRefresh && this.isResumed && gl_mini_calendar.childCount > 0 && currentMonthView != null && currentMonthView != monthView) {
            // Skip setupItemMiniCalendarContent for current item if monthView changes because it already has the expected content
            currentMonthView = monthView
            return
        }

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return

        val weekStartingPosition = calendarViewModel.weekViewStartingPositionAndDate.value?.first
        val weekStartingDate = calendarViewModel.weekViewStartingPositionAndDate.value?.second

        val monthStartingPosition = calendarViewModel.monthViewStartingPositionAndDate.value?.first ?: immutableStartingPosition
        val monthStartingDate = calendarViewModel.monthViewStartingPositionAndDate.value?.second ?: immutableDate

        val firstDayMonthView = monthStartingDate.plusMonths((immutablePosition - monthStartingPosition).toLong())

        val firstDayOfTheWeekNumber = monthStartingDate.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
        val firstDayOfTheWeek = monthStartingDate.with(temporalField, 1)
        val offset = immutablePosition - if (!monthView) weekStartingPosition ?: return else monthStartingPosition
        val firstDayWeekView = if (!monthView) weekStartingDate?.plusWeeks(offset.toLong()) ?: return else firstDayOfTheWeek.plusWeeks(offset.toLong())

        currentWeekFirstDay = firstDayWeekView

        val firstDay =
            if (monthView) firstDayMonthView
            else {
                if (weekStartingPosition != null && weekStartingPosition > immutablePosition) {
                    firstDayWeekView.plusDays(6).withDayOfMonth(1)
                } else if (weekStartingPosition != null && weekStartingPosition == immutablePosition) {
                    val selectedDate = calendarViewModel.selectedDate.value ?: firstDayWeekView
                    selectedDate.withDayOfMonth(1)
                } else {
                    firstDayWeekView.withDayOfMonth(1)
                }
            }

        calendarViewModel.lifeCycleScope.launch {
            if (!this@ItemMiniCalendarFragment.isResumed) {
                delay(300) // TODO Still needed ?
            }

            // Check if view still exists after delay
            if (gl_mini_calendar == null) return@launch

            currentMonthView = monthView

            if (monthView) {
                val firstDayOfTheMonth = firstDayMonthView.withDayOfMonth(1)
                val firstDayOfTheMonthWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheMonthWeekOffset = if (firstDayOfTheMonthWeekNumber < 0) firstDayOfTheMonthWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheMonthWeekNumber
                val lastDayOfTheMonth = firstDayMonthView.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
                val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

                val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheMonthWeekOffset.toLong())
                val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(lastDayOfMonthOffset.toLong())

                calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId)
            } else {
                val toDate = firstDayWeekView.plusDays(6)

                calendarViewModel.fetchEvents(firstDayWeekView, toDate, timeZoneId)
            }

            initialiseMiniCalendarContent(if (forceRefresh) firstDayMonthView else firstDay, firstDayWeekView, startWeekOn, timeZoneId, monthView, forceRefresh)
        }
    }

    private fun initialiseMiniCalendarContent(forDate: LocalDate, firstDayWeekView: LocalDate, startWeekOn: DayOfWeek, timeZoneId: String, monthView: Boolean, forceRefresh: Boolean) {
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val lastDayOfTheMonth = forDate.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
        val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(lastDayOfMonthOffset.toLong())

        val firstMiniCalendarDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())

        setupWeekNumbers(firstDayOfTheMonth, startWeekOn)

        val previousMonthDayItems = (0 until firstDayOfTheWeekOffset).map {
            val date = firstDayOfTheMonth.minusDays(it.toLong() + 1)
            MiniCalendarItem(date, false, true, emptyList())
        }.reversed()

        val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
            val date = firstDayOfTheMonth.plusDays(it.toLong())
            MiniCalendarItem(date, false, true, emptyList())
        }

        val upcomingMonthDayItems = (0 until lastDayOfMonthOffset).map {
            val date = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(it.toLong() + 1)
            MiniCalendarItem(date, false, true, emptyList())
        }

        // submit month skeleton with only days and weekday names
        val skeletonList = AndroidUtils.concatenate(previousMonthDayItems, dayItems, upcomingMonthDayItems)
        setMiniCalendarSkeletonList(skeletonList, forDate, firstDayOfTheMonth, firstMiniCalendarDay, firstDayWeekView, startWeekOn, monthView)

        // Force refresh is used when selecting another month when in week view mode. In this case we want to update the month content but keep week content as is
        if (!forceRefresh) {
            val weekSkeletonList = (0 until 7).map {
                val date = firstDayWeekView.plusDays(it.toLong())
                MiniCalendarItem(date, false, true, emptyList())
            }

            setMiniCalendarWeekSkeletonList(
                weekSkeletonList,
                firstMiniCalendarDay,
                firstDayWeekView,
                firstDayOfTheMonth,
                startWeekOn,
                monthView
            )

            gl_mini_calendar.visibleOrInvisible(monthView)
            ll_mini_calendar_week.visibleOrInvisible(WEEK_COMPONENT && !monthView)
            ll_weeknumber_weekview.visibleOrInvisible(WEEK_COMPONENT && !monthView && calendarViewModel.displayWeekNumber.value == true)
        }

        calendarViewModel.lifeCycleScope.launch {

            calendarViewModel.calendarIndicators(
                fromDate,
                toDate,
                timeZoneId
            ).observe(viewLifecycleOwner) { indicators ->
                this@ItemMiniCalendarFragment.indicators = indicators
                applyMiniCalendarIndicators(indicators, firstMiniCalendarDay)
                applyMiniCalendarWeekIndicators(indicators, currentWeekFirstDay ?: firstDayWeekView)
            }

            calendarViewModel.selectedDate.observe(viewLifecycleOwner) { selectedDate ->
                val immutableWeekStart = weekStart ?: return@observe

                val firstDayOfTheWeekNumber = selectedDate.dayOfWeek.value - immutableWeekStart.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val temporalField = WeekFields.of(immutableWeekStart, 7 - firstDayOfTheWeekOffset).dayOfWeek()
                val weekStartingPosition = calendarViewModel.weekViewStartingPositionAndDate.value?.first
                val weekStartingDate = calendarViewModel.weekViewStartingPositionAndDate.value?.second
                val immutablePosition = position
                val firstDayOfTheWeek = if (!(this@ItemMiniCalendarFragment.monthView ?: monthView) && weekStartingPosition != null && weekStartingDate != null && immutablePosition != null) {
                    logger.e("Test test handleSelectedDate position $position resumed ${this@ItemMiniCalendarFragment.isResumed} calculate first day with pos firstDayOfTheWeek = ${weekStartingDate.with(temporalField, 1).plusWeeks(immutablePosition - weekStartingPosition.toLong())}")
                    weekStartingDate.with(temporalField, 1).plusWeeks(immutablePosition - weekStartingPosition.toLong())
                } else selectedDate.with(temporalField, 1)

                val selectedMiniCalendarWeekItem = selectedMiniCalendarWeekItem
                val currentSelectedDate =
                    if (currentWeekFirstDay == firstDayOfTheWeek && selectedMiniCalendarWeekItem != null) firstDayOfTheWeek.plusDays(selectedMiniCalendarWeekItem.toLong())
                    else null

                if (WEEK_COMPONENT && selectedDate.weekNumber(immutableWeekStart) != currentWeekFirstDay?.weekNumber(immutableWeekStart)) {

                    currentWeekFirstDay = firstDayOfTheWeek
                    val weekSkeletonList = (0 until 7).map {
                        val date = firstDayOfTheWeek.plusDays(it.toLong())
                        MiniCalendarItem(date, false, true, emptyList())
                    }

                    setMiniCalendarWeekSkeletonList(weekSkeletonList, firstMiniCalendarDay, firstDayOfTheWeek, firstDayOfTheMonth, startWeekOn, this@ItemMiniCalendarFragment.monthView ?: false)
                    applyMiniCalendarWeekIndicators(indicators, firstDayOfTheWeek)
                } else {

                    applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayOfTheWeek, firstDayOfTheMonth)

                    if (WEEK_COMPONENT && selectedDate.month != currentSelectedDate?.month && this@ItemMiniCalendarFragment.isResumed && !(this@ItemMiniCalendarFragment.monthView ?: monthView)) {
                        calendarViewModel.monthViewStartingPositionAndDate.value = Pair(immutablePosition ?: return@observe, selectedDate.withDayOfMonth(1))
                        setupItemMiniCalendarContent(timeZoneId, startWeekOn, this@ItemMiniCalendarFragment.monthView ?: monthView, forceRefresh = true)
                    }
                }
            }
        }
    }

    private fun setMiniCalendarSkeletonList(skeletonList: List<MiniCalendarItem>, forDate: LocalDate, firstDay: LocalDate, firstMiniCalendarDay: LocalDate, firstDayWeekView: LocalDate, startWeekOn: DayOfWeek, monthView: Boolean) {
        if (currentMiniCalendarMonthList == skeletonList) return
        currentMiniCalendarMonthList = skeletonList
        view?.findViewById<GridLayout>(R.id.gl_mini_calendar)?.run {
            this.removeAllViews()
            selectedMiniCalendarItem = -1
            fullWeeksInMonth = calculateFullWeeksInMonth(firstDay, startWeekOn)
            this.rowCount = fullWeeksInMonth
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            isRowOrderPreserved = false
            this.addMiniCalendarItemView(skeletonList, forDate, true)
        }

        val selectedDate = calendarViewModel.selectedDate.value
        if (selectedDate != null) {
            applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayWeekView, firstDay)
        }
    }

    private fun setMiniCalendarWeekSkeletonList(skeletonList: List<MiniCalendarItem>, firstMiniCalendarDay: LocalDate, firstDayWeekView: LocalDate, firstDayOfTheMonth: LocalDate, startWeekOn: DayOfWeek, monthView: Boolean) {
        if (!WEEK_COMPONENT) return
        if (currentMiniCalendarWeekList == skeletonList) return
        currentMiniCalendarWeekList = skeletonList
        view?.findViewById<LinearLayout>(R.id.ll_mini_calendar_week)?.run {
            this.removeAllViews()
            selectedMiniCalendarWeekItem = -1
            this.addMiniCalendarItemView(skeletonList, firstDayWeekView, false)
        }

        view?.findViewById<LinearLayout>(R.id.ll_weeknumber_weekview)?.run {
            this.removeAllViews()
            val weekdayView = LayoutInflater.from(this.context).inflate(
                R.layout.item_mini_calendar_weekday,
                this,
                false
            )
            val textView = weekdayView as TextView
            textView.text = "${firstDayWeekView.weekNumber(startWeekOn)}"
            val layoutParams = textView.layoutParams as LinearLayout.LayoutParams
            layoutParams.topMargin =
                requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_top_first)
            textView.layoutParams = layoutParams
            addView(weekdayView)
        }

        val selectedDate = calendarViewModel.selectedDate.value
        if (selectedDate != null) {
            applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayWeekView, firstDayOfTheMonth)
        }
    }

    private fun ViewGroup.addMiniCalendarItemView(skeletonList: List<MiniCalendarItem>, forDate: LocalDate, monthView: Boolean) {
        skeletonList.forEach { item ->
            val miniCalendarItemView = LayoutInflater.from(this.context).inflate(
                R.layout.item_mini_calendar,
                this,
                false
            )

            if (this.childCount > 7) {
                val itemLayoutParams: GridLayout.LayoutParams =
                    miniCalendarItemView.layoutParams as GridLayout.LayoutParams
                itemLayoutParams.topMargin =
                    miniCalendarItemView.context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)
            }

            when {
                item.isSelected -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(miniCalendarItemView.context, R.style.Text_DefaultSmall_Strong_Inverted)
                    miniCalendarItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                }
                item.date == LocalDate.now(ZoneId.of(timeZoneId)) -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(miniCalendarItemView.context, R.style.Text_DefaultSmall_Strong)
                    miniCalendarItemView.itemMiniCalendarText.setTextColor(ContextCompat.getColor(miniCalendarItemView.context, R.color.brand_norm))
                    miniCalendarItemView.selected_background.setBackgroundResource(0)
                }
                monthView && item.date.month != forDate.month -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(miniCalendarItemView.context, R.style.Text_DefaultSmall_Strong)
                    miniCalendarItemView.itemMiniCalendarText.setTextColor(ContextCompat.getColor(miniCalendarItemView.context, R.color.text_hint))
                    miniCalendarItemView.selected_background.setBackgroundResource(0)
                }
                else -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(miniCalendarItemView.context, R.style.Text_DefaultSmall_Strong)
                    miniCalendarItemView.selected_background.setBackgroundResource(0)
                }
            }

            miniCalendarItemView.itemMiniCalendarText.text = "${item.date.dayOfMonth}"

            miniCalendarItemView.ll_calendar_dots.visibleOrInvisible(true)
            miniCalendarItemView.ll_calendar_dots.apply {
                this.children.forEachIndexed { index, view ->
                    if (item.indicatorColors.size - 1 >= index) {
                        (view as ImageView).drawable.setTint(Color.parseColor(item.indicatorColors[index]))
                        view.visibleOrGone(true)
                    } else {
                        view.visibleOrGone(false)
                    }
                }
            }

            miniCalendarItemView.setOnClickListener {
                calendarViewModel.handleDaySelected(item.date)
            }

            this.addView(miniCalendarItemView)
        }
    }

    private fun applyMiniCalendarIndicators(indicators: Map<LocalDate, List<String>>, firstMiniCalendarDay: LocalDate) {
        indicators.forEach { (date, indicatorColors) ->
            val miniCalendarIndex = ChronoUnit.DAYS.between(firstMiniCalendarDay, date).toInt()
            val itemView = gl_mini_calendar.getChildAt(miniCalendarIndex)
            itemView?.let {
                itemView.ll_calendar_dots.visibleOrInvisible(true)
                itemView.ll_calendar_dots.apply {
                    children.forEachIndexed { index, view ->
                        if (indicatorColors.size - 1 >= index) {
                            (view as ImageView).drawable.setTint(Color.parseColor(indicatorColors[index]))
                            view.visibleOrGone(true)
                        } else {
                            view.visibleOrGone(false)
                        }
                    }
                }
            }
        }
    }

    private fun applyMiniCalendarWeekIndicators(indicators: Map<LocalDate, List<String>>, firstDayWeekView: LocalDate) {
        if (!WEEK_COMPONENT) return
        indicators.forEach { (date, indicatorColors) ->
            val miniCalendarWeekIndex = ChronoUnit.DAYS.between(firstDayWeekView, date).toInt()
            val weekItemView = ll_mini_calendar_week.getChildAt(miniCalendarWeekIndex)
            weekItemView?.let {
                weekItemView.ll_calendar_dots.visibleOrInvisible(true)
                weekItemView.ll_calendar_dots.apply {
                    children.forEachIndexed { index, view ->
                        if (indicatorColors.size - 1 >= index) {
                            (view as ImageView).drawable.setTint(Color.parseColor(indicatorColors[index]))
                            view.visibleOrGone(true)
                        } else {
                            view.visibleOrGone(false)
                        }
                    }
                }
            }
        }
    }

    private fun applySelectedDate(selectedDate: LocalDate, firstMiniCalendarDay: LocalDate, firstDayWeekView: LocalDate, firstDayOfTheMonth: LocalDate) {
        if (gl_mini_calendar.childCount <= 0) return

        val newSelectedMiniCalendarItem = ChronoUnit.DAYS.between(firstMiniCalendarDay, selectedDate).toInt()
        if (selectedMiniCalendarItem != newSelectedMiniCalendarItem && firstDayOfTheMonth.month == selectedDate.month) {

            val selectedMiniCalendarItem = selectedMiniCalendarItem
            if (selectedMiniCalendarItem != null) {

                val miniCalendarItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem)
                miniCalendarItemView?.let {
                    if (firstMiniCalendarDay.plusDays(selectedMiniCalendarItem.toLong()) == LocalDate.now()) {
                        // Apply today's style
                        miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                            miniCalendarItemView.context,
                            R.style.Text_DefaultSmall_Strong
                        )
                        miniCalendarItemView.itemMiniCalendarText.setTextColor(ContextCompat.getColor(miniCalendarItemView.context, R.color.brand_norm))
                        miniCalendarItemView.selected_background.setBackgroundResource(0)
                    } else if (selectedDate.month != firstMiniCalendarDay.plusDays(selectedMiniCalendarItem.toLong()).month && firstDayOfTheMonth.month != firstMiniCalendarDay.plusDays(selectedMiniCalendarItem.toLong()).month && newSelectedMiniCalendarItem >= 0 && newSelectedMiniCalendarItem < gl_mini_calendar.childCount) {
                        // Apply previous / upcoming month items style
                        miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                            miniCalendarItemView.context,
                            R.style.Text_DefaultSmall_Strong
                        )
                        miniCalendarItemView.itemMiniCalendarText.setTextColor(ContextCompat.getColor(miniCalendarItemView.context, R.color.text_hint))
                        miniCalendarItemView.selected_background.setBackgroundResource(0)
                    } else {
                        // Apply default items style
                        miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                            miniCalendarItemView.context,
                            R.style.Text_DefaultSmall_Strong
                        )
                        miniCalendarItemView.selected_background.setBackgroundResource(0)
                    }
                }
            }

            this.selectedMiniCalendarItem = newSelectedMiniCalendarItem
            if (selectedDate.month == firstDayOfTheMonth.month) {
                // Only display selected date style for month currently displayed
                val miniCalendarItemView = gl_mini_calendar.getChildAt(newSelectedMiniCalendarItem)
                miniCalendarItemView?.let {
                    // Apply selected date item style
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarItemView.context,
                        R.style.Text_DefaultSmall_Strong_Inverted
                    )
                    miniCalendarItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                }
            }
        }

        val newSelectedMiniCalendarWeekItem = ChronoUnit.DAYS.between(firstDayWeekView, selectedDate).toInt()
        if (WEEK_COMPONENT && selectedMiniCalendarWeekItem != newSelectedMiniCalendarWeekItem) {

            val selectedMiniCalendarWeekItem = selectedMiniCalendarWeekItem
            if (selectedMiniCalendarWeekItem != null) {

                val miniCalendarWeekItemView = ll_mini_calendar_week.getChildAt(selectedMiniCalendarWeekItem)
                miniCalendarWeekItemView?.let {
                    if (firstDayWeekView.plusDays(selectedMiniCalendarWeekItem.toLong()) == LocalDate.now()) {
                        // Apply today's style
                        miniCalendarWeekItemView.itemMiniCalendarText.setTextAppearance(
                            miniCalendarWeekItemView.context,
                            R.style.Text_DefaultSmall_Strong
                        )
                        miniCalendarWeekItemView.itemMiniCalendarText.setTextColor(ContextCompat.getColor(miniCalendarWeekItemView.context, R.color.brand_norm))
                        miniCalendarWeekItemView.selected_background.setBackgroundResource(0)
                    } else {
                        // Apply default items style
                        miniCalendarWeekItemView.itemMiniCalendarText.setTextAppearance(
                            miniCalendarWeekItemView.context,
                            R.style.Text_DefaultSmall_Strong
                        )
                        miniCalendarWeekItemView.selected_background.setBackgroundResource(0)
                    }
                }
            }

            this.selectedMiniCalendarWeekItem = newSelectedMiniCalendarWeekItem
            val miniCalendarWeekItemView = ll_mini_calendar_week.getChildAt(newSelectedMiniCalendarWeekItem)
            miniCalendarWeekItemView?.let {
                // Apply selected date item style
                miniCalendarWeekItemView.itemMiniCalendarText.setTextAppearance(
                    miniCalendarWeekItemView.context,
                    R.style.Text_DefaultSmall_Strong_Inverted
                )
                miniCalendarWeekItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
            }
        }
    }

    private fun setupWeekNumbers(firstDay: LocalDate, startWeekOn: DayOfWeek) {
        // Setup week numbers
        view?.findViewById<LinearLayout>(R.id.ll_weeknumbers)?.run {
            this.removeAllViews()
            fullWeeksInMonth = calculateFullWeeksInMonth(firstDay, startWeekOn)
            for (i in 0 until fullWeeksInMonth) {
                val weekdayView = LayoutInflater.from(this.context).inflate(
                    R.layout.item_mini_calendar_weekday,
                    this,
                    false
                )
                val textView = weekdayView as TextView
                textView.text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
                if (i == 0) {
                    // Update top margin for first row
                    val layoutParams = textView.layoutParams as LinearLayout.LayoutParams
                    layoutParams.topMargin = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_top_first)
                    textView.layoutParams = layoutParams
                }
                addView(weekdayView)
            }
        }
    }

    fun setAdapter(adapter: MiniCalendarPagerAdapter) {
        this.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.miniCalendarPositionListeners?.remove(position)
    }
}
