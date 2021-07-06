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
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.recyclerview.widget.GridLayoutManager
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
    private var fullWeeksInMonth = 0

    private var selectedMiniCalendarItem = -1
    private var selectedMiniCalendarWeekItem = -1

    private var currentWeekFirstDay: LocalDate? = null
    private var indicators: Map<LocalDate, List<String>> = hashMapOf()

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
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val dayCellsToShow = firstDayOfTheWeekOffset + firstDayOfMonth.lengthOfMonth()

            return ceil(dayCellsToShow / MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK.toDouble()).toInt()
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
                if (gl_mini_calendar == null) return null
                if (initialMiniCalendarLayoutParams == null) initialMiniCalendarLayoutParams = gl_mini_calendar.layoutParams as ConstraintLayout.LayoutParams
                if (initialWeekNumbersLayoutParams == null) initialWeekNumbersLayoutParams = ll_weeknumbers.layoutParams as ConstraintLayout.LayoutParams

                val selectedDate = calendarViewModel.selectedDate.value ?: return gl_mini_calendar.top
                val weekStart = weekStart ?: return gl_mini_calendar.top

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

                val selectedItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem) ?: return 0

                val selectedItemViewTop = selectedItemView.top + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val headerItemBottom = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val miniCalendarCurrentTop = (gl_mini_calendar.top * -1) + headerItemBottom
                if (selectedItemView.top != 0 && selectedItemViewTop > miniCalendarCurrentTop) {
                    logger.e("Test test drags selectedItemViewTop $selectedItemViewTop miniCalendarCurrentTop $miniCalendarCurrentTop result ${miniCalendarCurrentTop - scrollValue.toInt()}")
//                    logger.e("Test test drags selectedItemViewTop $selectedItemViewTop headerItemBottom $headerItemBottom miniCalendarCurrentTop $miniCalendarCurrentTop")
//                    logger.e("Test test drags sliderTop $sliderTop selectedItemViewTop $selectedItemViewTop  selectedWeekTop $selectedWeekTop selectedWeekBottom $selectedWeekBottom rv_mini_calendar.top ${rv_mini_calendar.top} rv_mini_calendar.height ${rv_mini_calendar.height} rv_mini_calendar.y ${rv_mini_calendar.y} rv_mini_calendar.bottom ${rv_mini_calendar.bottom}")
                    gl_mini_calendar.top -= scrollValue.toInt()
                    ll_weeknumbers.top -= scrollValue.toInt()
                    if (selectedItemViewTop < (gl_mini_calendar.top * -1) + headerItemBottom) {
                        gl_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                        ll_weeknumbers.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                    }
                } else {
                    logger.e("Test test hides selectedItemViewTop $selectedItemViewTop miniCalendarCurrentTop $miniCalendarCurrentTop result ${miniCalendarCurrentTop - scrollValue.toInt()}")
//                    logger.e("Test test hides selectedItemViewTop $selectedItemViewTop headerItemBottom $headerItemBottom miniCalendarCurrentTop $miniCalendarCurrentTop")
//                    logger.e("Test test hides sliderTop $sliderTop selectedItemViewTop $selectedItemViewTop  selectedWeekTop $selectedWeekTop selectedWeekBottom $selectedWeekBottom rv_mini_calendar.top ${rv_mini_calendar.top} rv_mini_calendar.height ${rv_mini_calendar.height} rv_mini_calendar.y ${rv_mini_calendar.y} rv_mini_calendar.bottom ${rv_mini_calendar.bottom}")
//                    logger.e("Test test new top value ${(selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)}")
                    gl_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                    ll_weeknumbers.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                }

                return gl_mini_calendar.top
            }

            override fun scrollDown(scrollValue: Float, sliderTop: Int): Int? {
                if (gl_mini_calendar == null) return null
                if (initialMiniCalendarLayoutParams == null) initialMiniCalendarLayoutParams = gl_mini_calendar.layoutParams as ConstraintLayout.LayoutParams
                if (initialWeekNumbersLayoutParams == null) initialWeekNumbersLayoutParams = ll_weeknumbers.layoutParams as ConstraintLayout.LayoutParams


                val headerItemBottom = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                val miniCalendarCurrentTop = (gl_mini_calendar.top * -1) + headerItemBottom
                val selectedItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem) ?: return 0
                val selectedItemViewTop = selectedItemView.top + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
                logger.e("Test test scrollDown before gl_mini_calendar.top ${gl_mini_calendar.top} selectedItemViewTop $selectedItemViewTop miniCalendarCurrentTop $miniCalendarCurrentTop")

                if (monthView == false) {

                    logger.e("Test test scrollDown before fix mini calendar gl_mini_calendar.top ${gl_mini_calendar.top} (selectedItemViewTop * -1) ${(selectedItemViewTop * -1)} gl_mini_calendar invisble ${gl_mini_calendar.visibility == View.INVISIBLE}")
                    if (gl_mini_calendar.visibility == View.INVISIBLE && selectedItemViewTop != miniCalendarCurrentTop) {
                        logger.e("Test test scrollDown before fix mini calendar y to ${(selectedItemViewTop * -1).toFloat() + headerItemBottom}")
//                        gl_mini_calendar.y = (selectedItemViewTop * -1).toFloat() + headerItemBottom
                        gl_mini_calendar.top = (selectedItemViewTop * -1) + headerItemBottom
                        ll_weeknumbers.top = (selectedItemViewTop * -1) + headerItemBottom
                    } else if (gl_mini_calendar.top < 0) {
                        logger.e("Test test scrollDown before fix mini calendar top to ${gl_mini_calendar.top + scrollValue.toInt()}")
                        gl_mini_calendar.top += scrollValue.toInt()
                        ll_weeknumbers.top += scrollValue.toInt()
                        if (gl_mini_calendar.top > 0) {
                            gl_mini_calendar.top = 0
                            ll_weeknumbers.top = 0
                        }

                        gl_mini_calendar.visibleOrInvisible(true)
                        ll_mini_calendar_week.visibleOrInvisible(false)
                    } else if (gl_mini_calendar.top == 0 && selectedItemViewTop == headerItemBottom) {
                        logger.e("Test test scrollDown before keep fix mini calendar y to value ${(selectedItemViewTop * -1) + headerItemBottom} gl_mini_calendar invisble ${gl_mini_calendar.visibility == View.INVISIBLE}")
                        gl_mini_calendar.visibleOrInvisible(true)
                        ll_mini_calendar_week.visibleOrInvisible(false)
//                        gl_mini_calendar.top = (selectedItemViewTop * -1) + headerItemBottom
//                        ll_weeknumbers.top = (selectedItemViewTop * -1) + headerItemBottom
//                        gl_mini_calendar.top = (selectedItemViewTop * -1) + requireContext().resources.getDimensionPixelSize(R.dimen.calendar_item_header_height)
//                        gl_mini_calendar.y = 0F
                    }

                    if (gl_mini_calendar.top < 0) {
                        if (gl_mini_calendar.top + scrollValue.toInt() > 0) gl_mini_calendar.top = 0
//                        else gl_mini_calendar.top += scrollValue.toInt()
                        if (ll_weeknumbers.top + scrollValue.toInt() > 0) ll_weeknumbers.top = 0
//                        else ll_weeknumbers.top += scrollValue.toInt()
                    }
                } else {
                    if (gl_mini_calendar.top < 0) {
                        if (gl_mini_calendar.top + scrollValue.toInt() > 0) gl_mini_calendar.top = 0
                        else gl_mini_calendar.top += scrollValue.toInt()
                        if (ll_weeknumbers.top + scrollValue.toInt() > 0) ll_weeknumbers.top = 0
                        else ll_weeknumbers.top += scrollValue.toInt()
                    }
                }

//                logger.e("Test test after scrollDown ${gl_mini_calendar.top}")
                logger.e("Test test scrollDown after gl_mini_calendar.top ${gl_mini_calendar.top} selectedItemViewTop $selectedItemViewTop miniCalendarCurrentTop $miniCalendarCurrentTop")
                return gl_mini_calendar.top
            }

            override fun resetPosition() {
                if (gl_mini_calendar == null) return
                initialMiniCalendarLayoutParams?.let { gl_mini_calendar.layoutParams = it }
                initialWeekNumbersLayoutParams?.let { ll_weeknumbers.layoutParams = it }

                gl_mini_calendar.visibleOrInvisible(monthView == true)
                ll_mini_calendar_week.visibleOrInvisible(monthView == false)
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
            it?.let {
                setupItemMiniCalendarContent(it.first, it.second, it.third)
            }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            view.findViewById<LinearLayout>(R.id.ll_weeknumbers).visibleOrGone(displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek, monthView: Boolean) {
        // Skip init for current item if monthView changes
        if (this.isResumed && gl_mini_calendar.childCount > 0 && currentMonthView != null && currentMonthView != monthView) {
            currentMonthView = monthView
            return
        }

        val immutableDate = date ?: return // TODO Use Position here to calculate date depending on monthView value
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return

        val weekStartingPosition = calendarViewModel.weekViewStartingPositionAndDate.value?.first
        val weekStartingDate = calendarViewModel.weekViewStartingPositionAndDate.value?.second

        val monthStartingPosition = calendarViewModel.monthViewStartingPositionAndDate.value?.first ?: immutableStartingPosition
        val monthStartingDate = calendarViewModel.monthViewStartingPositionAndDate.value?.second ?: immutableDate

        val firstDayMonthView = monthStartingDate.plusMonths((immutablePosition - monthStartingPosition).toLong())

        val firstDayOfTheWeekNumber = monthStartingDate.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
        val firstDayOfTheWeek = monthStartingDate.with(temporalField, 1)
        val offset = immutablePosition - if (!monthView) weekStartingPosition ?: return else monthStartingPosition
        val firstDayWeekView = if (!monthView) weekStartingDate?.plusWeeks(offset.toLong()) ?: return else firstDayOfTheWeek.plusWeeks(offset.toLong())

        currentWeekFirstDay = firstDayWeekView

        val firstDay = if (monthView) firstDayMonthView else firstDayWeekView

        logger.e("Test test mini calendar setupItemMiniCalendarContent: firstDay $firstDay firstDayMonthView $firstDayMonthView firstDayWeekView $firstDayWeekView")

        calendarViewModel.lifeCycleScope.launch {
            if (currentMonthView == false && monthView && calendarViewModel.selectedDate.value?.month != firstDay.month) {
                delay(300) // TODO This messes up the side swipe a bit if we swipe multiple months fast
            }

            // Check if view still exists after delay
            if (gl_mini_calendar == null) return@launch

            val delay = currentMonthView == null && monthView && calendarViewModel.selectedDate.value?.month != firstDay.month

            currentMonthView = monthView

            setupWeekNumbers(firstDay, startWeekOn)
            initialiseMiniCalendarContent(firstDay, firstDayWeekView, startWeekOn, timeZoneId, monthView, delay)
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

    private fun initialiseMiniCalendarContent(forDate: LocalDate, firstDayWeekView: LocalDate, startWeekOn: DayOfWeek, timeZoneId: String, monthView: Boolean, delay: Boolean) {
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val lastDayOfTheMonth = forDate.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
        val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(lastDayOfMonthOffset.toLong())

        val firstMiniCalendarDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
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

        logger.e("Test test skeletonList size ${skeletonList.size}")
        setMiniCalendarSkeletonList(skeletonList, forDate, firstDayOfTheMonth, firstMiniCalendarDay, firstDayWeekView, startWeekOn, monthView)

        val weekSkeletonList = (0 until 7).map {
            val date = firstDayWeekView.plusDays(it.toLong())
            MiniCalendarItem(date, false, true, emptyList())
        }

        setMiniCalendarWeekSkeletonList(weekSkeletonList, firstMiniCalendarDay, firstDayWeekView, monthView)

        gl_mini_calendar.visibleOrInvisible(monthView)
        ll_mini_calendar_week.visibleOrInvisible(!monthView)

        calendarViewModel.lifeCycleScope.launch {
            if (delay) delay(300) // TODO Still needed ?

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
//                if (!this@ItemMiniCalendarFragment.isResumed) {
//                    applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayWeekView)
//                    return@observe
//                }

                val immutableWeekStart = weekStart ?: return@observe

                val firstDayOfTheWeekNumber = selectedDate.dayOfWeek.value - immutableWeekStart.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val temporalField = WeekFields.of(immutableWeekStart, 7 - firstDayOfTheWeekOffset).dayOfWeek()
                val firstDayOfTheWeek = selectedDate.with(temporalField, 1)

                if (selectedDate.weekNumber(immutableWeekStart) != currentWeekFirstDay?.weekNumber(immutableWeekStart)) {
                    logger.e("Test test selectedDate position $position firstDayOfTheWeek $firstDayOfTheWeek weekSkeletonList $weekSkeletonList")

                    currentWeekFirstDay = firstDayOfTheWeek

                    val weekSkeletonList = (0 until 7).map {
                        val date = firstDayOfTheWeek.plusDays(it.toLong())
                        MiniCalendarItem(date, false, true, emptyList())
                    }

                    setMiniCalendarWeekSkeletonList(weekSkeletonList, firstMiniCalendarDay, firstDayOfTheWeek, this@ItemMiniCalendarFragment.monthView ?: false)
                    applyMiniCalendarWeekIndicators(indicators, firstDayOfTheWeek)

//                    selectedMiniCalendarWeekItem = ChronoUnit.DAYS.between(firstDayOfTheWeek, selectedDate).toInt()
//                    logger.e("Test test setup apply selected to week $selectedMiniCalendarWeekItem")
//                    logger.e("Test test selectedMiniCalendarWeekItem $selectedMiniCalendarWeekItem")
//                    val miniCalendarWeekItemView = ll_mini_calendar_week.getChildAt(selectedMiniCalendarWeekItem)
//                    miniCalendarWeekItemView?. let {
//                        miniCalendarWeekItemView.itemMiniCalendarText.setTextAppearance(
//                            miniCalendarWeekItemView.context,
//                            R.style.Text_DefaultSmall_Strong_Inverted
//                        )
//                        miniCalendarWeekItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
//                    }
                } else {
                    applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayOfTheWeek)
                }
            }
        }
    }

    private fun setMiniCalendarSkeletonList(skeletonList: List<MiniCalendarItem>, forDate: LocalDate, firstDay: LocalDate, firstMiniCalendarDay: LocalDate, firstDayWeekView: LocalDate, startWeekOn: DayOfWeek, monthView: Boolean) {

        view?.findViewById<GridLayout>(R.id.gl_mini_calendar)?.run {
            this.removeAllViews()
            selectedMiniCalendarItem = -1
            fullWeeksInMonth = calculateFullWeeksInMonth(firstDay, startWeekOn)
            this.rowCount = fullWeeksInMonth
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            isRowOrderPreserved = false
            this.addMiniCalendarItemView(skeletonList, forDate, true)
            logger.e("Test test setup finished gl_mini_calendar addMiniCalendarItemView")
        }

        val selectedDate = calendarViewModel.selectedDate.value
        if (selectedDate != null) {
            applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayWeekView)
        }
    }

    private fun setMiniCalendarWeekSkeletonList(skeletonList: List<MiniCalendarItem>, firstMiniCalendarDay: LocalDate, firstDayWeekView: LocalDate, monthView: Boolean) {
        view?.findViewById<LinearLayout>(R.id.ll_mini_calendar_week)?.run {
            this.removeAllViews()
            selectedMiniCalendarWeekItem = -1
            this.addMiniCalendarItemView(skeletonList, firstDayWeekView, false)
            logger.e("Test test setup finished ll_mini_calendar_week addMiniCalendarItemView")
        }

        val selectedDate = calendarViewModel.selectedDate.value
        if (selectedDate != null) {
            applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayWeekView)
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

            // TODO Delete
            if (this.childCount == 0) miniCalendarItemView.itemMiniCalendarText.text = "${this@ItemMiniCalendarFragment.position.toString().takeLast(3)}"

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

    private fun applySelectedDate(selectedDate: LocalDate, firstMiniCalendarDay: LocalDate, firstDayWeekView: LocalDate) {
        if (gl_mini_calendar.childCount <= 0) return
        logger.e("Test test setup applySelectedDate for selectedDate $selectedDate firstMiniCalendarDay $firstMiniCalendarDay firstDayWeekView $firstDayWeekView")

        val newSelectedMiniCalendarItem = ChronoUnit.DAYS.between(firstMiniCalendarDay, selectedDate).toInt()
        if (selectedMiniCalendarItem != newSelectedMiniCalendarItem) {
            logger.e("Test test previous selectedMiniCalendarItem $selectedMiniCalendarItem")
            if (selectedMiniCalendarItem != -1) {
                val miniCalendarItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem)
                miniCalendarItemView?.let {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarItemView.context,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarItemView.selected_background.setBackgroundResource(0)
                    // TODO set today style if it was previously selected
                }
            }

            selectedMiniCalendarItem = newSelectedMiniCalendarItem
            logger.e("Test test selectedMiniCalendarItem $selectedMiniCalendarItem")
            val miniCalendarItemView = gl_mini_calendar.getChildAt(selectedMiniCalendarItem)
            miniCalendarItemView?. let {
                miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                    miniCalendarItemView.context,
                    R.style.Text_DefaultSmall_Strong_Inverted
                )
                miniCalendarItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
            }
        }

        val newSelectedMiniCalendarWeekItem = ChronoUnit.DAYS.between(firstDayWeekView, selectedDate).toInt()
        if (selectedMiniCalendarWeekItem != newSelectedMiniCalendarWeekItem) {
            logger.e("Test test previous selectedMiniCalendarWeekItem $selectedMiniCalendarWeekItem")
            if (selectedMiniCalendarWeekItem != -1) {
                val miniCalendarWeekItemView = ll_mini_calendar_week.getChildAt(selectedMiniCalendarWeekItem)
                miniCalendarWeekItemView?.let {
                    miniCalendarWeekItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarWeekItemView.context,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarWeekItemView.selected_background.setBackgroundResource(0)
                    // TODO set today style if it was previously selected
                }
            }

            selectedMiniCalendarWeekItem = newSelectedMiniCalendarWeekItem
            logger.e("Test test selectedMiniCalendarWeekItem $selectedMiniCalendarWeekItem")
            val miniCalendarWeekItemView = ll_mini_calendar_week.getChildAt(selectedMiniCalendarWeekItem)
            miniCalendarWeekItemView?.let {
                miniCalendarWeekItemView.itemMiniCalendarText.setTextAppearance(
                    miniCalendarWeekItemView.context,
                    R.style.Text_DefaultSmall_Strong_Inverted
                )
                miniCalendarWeekItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
            }
        }
    }

    private fun setupWeekNumbers(firstDay: LocalDate, startWeekOn: DayOfWeek) {
        // setup week numbers
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
                    val layoutParams = textView.layoutParams as LinearLayout.LayoutParams
                    layoutParams.topMargin = requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_top_first)
                    textView.layoutParams = layoutParams
                }
                addView(weekdayView)
            }
        }
    }
}
