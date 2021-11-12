package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.item_mini_calendar_header.view.*
import kotlinx.coroutines.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AndroidUtils
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
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
import kotlin.math.ceil


class ItemMiniCalendarFragment() : Fragment(), KoinComponent {
    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null
    private val miniCalendarMediator =
        MediatorLiveData<Pair<String, DayOfWeek>>() // Month view value is not needed if week component is disabled

    private var fullWeeksInMonth = 0

    private var selectedMiniCalendarItem: Int? = null

    private var indicators: Map<LocalDate, List<String>> = hashMapOf()

    private var currentMiniCalendarMonthList: List<MiniCalendarItem>? = null

    private val fetchingEventsScope = CoroutineScope(Dispatchers.IO)

    companion object {
        fun newInstance(position: Int, startingPosition: Int, date: LocalDate): ItemMiniCalendarFragment {
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
            if (!isMonthView) return context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)

            val fullWeeksInMonth = calculateFullWeeksInMonth(firstDayOfMonth, startWeekOn)

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
            val firstDayOfTheWeekOffset =
                if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val dayCellsToShow = firstDayOfTheWeekOffset + firstDayOfMonth.lengthOfMonth()

            return ceil(dayCellsToShow / DAYS_IN_A_WEEK.toDouble()).toInt()
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

    override fun onDestroyView() {
        super.onDestroyView()

        if (fetchingEventsScope.isActive) fetchingEventsScope.cancel()
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

            if (timeZoneId != null && weekStart != null) {
                miniCalendarMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }
        miniCalendarMediator.addSource(calendarViewModel.weekStart) { value ->
            weekStart = value?.let { getWeekStartDayOfWeek(it) }

            if (timeZoneId != null && weekStart != null) {
                miniCalendarMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }

        miniCalendarMediator.observe(viewLifecycleOwner) {
            it?.let {
                setupItemMiniCalendarContent(it.first, it.second)
            }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            view.findViewById<LinearLayout>(R.id.ll_weeknumbers).visibleOrGone(displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek) {

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return

        val firstDayMonthView = immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())

        calendarViewModel.lifeCycleScope.launch {

            // Check if view still exists after delay in case of fast swipe
            if (gl_mini_calendar == null) return@launch

            val firstDayOfTheMonth = firstDayMonthView.withDayOfMonth(1)
            val firstDayOfTheMonthWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheMonthWeekOffset =
                if (firstDayOfTheMonthWeekNumber < 0) firstDayOfTheMonthWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheMonthWeekNumber
            val lastDayOfTheMonth = firstDayMonthView.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

            val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheMonthWeekOffset.toLong())
            val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
                .plusDays(lastDayOfMonthOffset.toLong())

            calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId, coroutineScope = fetchingEventsScope)

            initialiseMiniCalendarContent(
                firstDayMonthView,
                startWeekOn,
                timeZoneId
            )
        }
    }

    private fun initialiseMiniCalendarContent(forDate: LocalDate, startWeekOn: DayOfWeek, timeZoneId: String) {
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset =
            if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val lastDayOfTheMonth = forDate.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
        val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            .plusDays(lastDayOfMonthOffset.toLong())

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

        // Submit month skeleton with only days and weekday names
        val skeletonList = AndroidUtils.concatenate(previousMonthDayItems, dayItems, upcomingMonthDayItems)
        setMiniCalendarSkeletonList(skeletonList, forDate, firstDayOfTheMonth, firstMiniCalendarDay, startWeekOn)

        calendarViewModel.lifeCycleScope.launch {

            calendarViewModel.calendarIndicators(
                fromDate,
                toDate,
                timeZoneId
            ).observe(viewLifecycleOwner) { indicators ->
                this@ItemMiniCalendarFragment.indicators = indicators
                applyMiniCalendarIndicators(indicators, firstMiniCalendarDay)
            }

            calendarViewModel.selectedDate.observe(viewLifecycleOwner) { selectedDate ->
                applySelectedDate(selectedDate, firstMiniCalendarDay, firstDayOfTheMonth)
            }
        }
    }

    private fun setMiniCalendarSkeletonList(
        skeletonList: List<MiniCalendarItem>,
        forDate: LocalDate,
        firstDay: LocalDate,
        firstMiniCalendarDay: LocalDate,
        startWeekOn: DayOfWeek
    ) {
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
            this.addMiniCalendarItemView(skeletonList, forDate)
        }

        val selectedDate = calendarViewModel.selectedDate.value
        if (selectedDate != null) {
            applySelectedDate(selectedDate, firstMiniCalendarDay, firstDay)
        }
    }

    private fun ViewGroup.addMiniCalendarItemView(skeletonList: List<MiniCalendarItem>, forDate: LocalDate) {
        skeletonList.forEach { item ->
            val miniCalendarItemView = LayoutInflater.from(this.context).inflate(
                R.layout.item_mini_calendar,
                this,
                false
            )

            if (this.childCount > DAYS_IN_A_WEEK) {
                val itemLayoutParams: GridLayout.LayoutParams =
                    miniCalendarItemView.layoutParams as GridLayout.LayoutParams
                itemLayoutParams.topMargin =
                    miniCalendarItemView.context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)
            }

            when {
                item.isSelected -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarItemView.context,
                        R.style.Text_DefaultSmall_Strong_Inverted
                    )
                    miniCalendarItemView.selected_background.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                }
                item.date == LocalDate.now(ZoneId.of(timeZoneId)) -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarItemView.context,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarItemView.itemMiniCalendarText.setTextColor(
                        ContextCompat.getColor(
                            miniCalendarItemView.context,
                            R.color.brand_norm
                        )
                    )
                    miniCalendarItemView.selected_background.setBackgroundResource(0)
                }
                item.date.month != forDate.month -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarItemView.context,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarItemView.itemMiniCalendarText.setTextColor(
                        ContextCompat.getColor(
                            miniCalendarItemView.context,
                            R.color.text_hint
                        )
                    )
                    miniCalendarItemView.selected_background.setBackgroundResource(0)
                }
                else -> {
                    miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                        miniCalendarItemView.context,
                        R.style.Text_DefaultSmall_Strong
                    )
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
                lifecycleScope.launch { calendarViewModel.handleDaySelected(item.date) }
            }

            this.addView(miniCalendarItemView)
        }
    }

    private fun applyMiniCalendarIndicators(indicators: Map<LocalDate, List<String>>, firstMiniCalendarDay: LocalDate) {
        gl_mini_calendar.children.forEach {
            it.ll_calendar_dots.removeAllViews()
        }
        val processedViewIndexList = arrayListOf<Int>()
        indicators.forEach { (date, indicatorColors) ->
            val miniCalendarIndex = ChronoUnit.DAYS.between(firstMiniCalendarDay, date).toInt()
            val itemView = gl_mini_calendar.getChildAt(miniCalendarIndex)
            itemView?.let {
                itemView.ll_calendar_dots.visibleOrInvisible(true)
                processedViewIndexList.add(miniCalendarIndex)
                indicatorColors.forEach { indicatorColor ->
                    val miniCalendarDotView = LayoutInflater.from(this.context).inflate(
                        R.layout.mini_calendar_dot,
                        itemView.ll_calendar_dots,
                        false
                    )
                    (miniCalendarDotView as ImageView).drawable.setTint(Color.parseColor(indicatorColor))
                    itemView.ll_calendar_dots.addView(miniCalendarDotView)
                }
            }
        }
    }

    private fun applySelectedDate(
        selectedDate: LocalDate,
        firstMiniCalendarDay: LocalDate,
        firstDayOfTheMonth: LocalDate
    ) {
        if (gl_mini_calendar.childCount <= 0) return

        val newSelectedMiniCalendarItem = ChronoUnit.DAYS.between(firstMiniCalendarDay, selectedDate).toInt()

        // Apply styles to month mini calendar
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
                        miniCalendarItemView.itemMiniCalendarText.setTextColor(
                            ContextCompat.getColor(
                                miniCalendarItemView.context,
                                R.color.brand_norm
                            )
                        )
                        miniCalendarItemView.selected_background.setBackgroundResource(0)
                    } else if (selectedDate.month != firstMiniCalendarDay.plusDays(
                            selectedMiniCalendarItem.toLong()
                        ).month &&
                        firstDayOfTheMonth.month != firstMiniCalendarDay.plusDays(
                            selectedMiniCalendarItem.toLong()
                        ).month &&
                        newSelectedMiniCalendarItem >= 0 &&
                        newSelectedMiniCalendarItem < gl_mini_calendar.childCount
                    ) {
                        // Apply previous / upcoming month items style
                        miniCalendarItemView.itemMiniCalendarText.setTextAppearance(
                            miniCalendarItemView.context,
                            R.style.Text_DefaultSmall_Strong
                        )
                        miniCalendarItemView.itemMiniCalendarText.setTextColor(
                            ContextCompat.getColor(
                                miniCalendarItemView.context,
                                R.color.text_hint
                            )
                        )
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
                    layoutParams.topMargin =
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_top_first)
                    textView.layoutParams = layoutParams
                }
                addView(weekdayView)
            }
        }
    }
}
