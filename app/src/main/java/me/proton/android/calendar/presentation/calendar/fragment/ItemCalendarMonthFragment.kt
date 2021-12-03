package me.proton.android.calendar.presentation.calendar.fragment

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.android.synthetic.main.item_calendar_month_fragment.*
import kotlinx.android.synthetic.main.item_month_view_event.view.*
import kotlinx.android.synthetic.main.item_month_view_grid.view.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sortForAgendaView
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.presentation.calendar.adapter.EventAdapter
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class ItemCalendarMonthFragment : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null
    private val monthViewMediator = MediatorLiveData<Pair<String, DayOfWeek>>()

    private lateinit var monthView: MonthView

    private lateinit var skeletonEventsLiveData: LiveData<CalendarsRepository.GetEventsResult<SkeletonEvent>>
    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<Event>>

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

        monthView = rootView.findViewById(R.id.month_fragment_month_view)

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return
        val firstDayMonthView = immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())

        month_fragment_loader.visibleOrGone(true)

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

        monthViewMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }
        monthViewMediator.addSource(calendarViewModel.weekStart) { value ->
            weekStart = value?.let { AndroidUtils.getWeekStartDayOfWeek(it) }

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }

        monthViewMediator.observe(viewLifecycleOwner) {
            it?.let {
                setupMonthViewGrid(firstDayMonthView, it.first, it.second)
            }
        }
    }

    private fun setupMonthViewGrid(forDate: LocalDate, timeZoneId: String, startWeekOn: DayOfWeek) {
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

        // Set the grid
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

        calendarViewModel.lifeCycleScope.launch {

            // Get and display skeleton events
            skeletonEventsLiveData = calendarViewModel.getSkeletonEvents(fromDate, toDate, timeZoneId)
            skeletonEventsLiveData.observe(viewLifecycleOwner) { skeletonEventsResult ->
                when (skeletonEventsResult) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        // TODO Loader for fetch state
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        displayMonthViewEvents(skeletonEventsResult.events, fromDate, timeZoneId, true)

                        month_fragment_loader.visibleOrGone(false)

                        getEvents(fromDate, toDate, timeZoneId)
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        logger.e("ItemCalendarMonthFragment getSkeletonEvents exception getting skeletonEventsLiveData", skeletonEventsResult.throwable)
                        // TODO Error somewhere
                        month_fragment_loader.visibleOrGone(false)
                    }
                }
            }
        }
    }

    private fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            logger.v("events flow: remove already existing observer for $fromDate to toDate")
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        eventsLiveData = calendarViewModel.getEvents(fromDate, toDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        // TODO
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        displayMonthViewEvents(it.events, fromDate, timeZoneId, false)
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        // TODO
                    }
                }
            }
        }
    }

    private fun displayMonthViewEvents(events: List<Event>, fromDate:LocalDate, timeZoneId: String, isSkeletonEvent: Boolean) {
        val monthGridMap = mutableMapOf<Int, ArrayList<Event>>()
        events.forEach { skeletonEvent ->
            val partTimeEndsOnMidnight = (!skeletonEvent.isAllDay() && skeletonEvent.getOccurrenceEnd(timeZoneId) .toLocalTime() == LocalTime.MIDNIGHT)
            var start = skeletonEvent.getOccurrenceStart(timeZoneId).toLocalDate()
            val end = skeletonEvent.getOccurrenceEnd(timeZoneId).toLocalDate()

            // Use !start.isAfter(end) to iterate inclusive
            while (!start.isAfter(end)) {
                val dayIndex = ChronoUnit.DAYS.between(fromDate, start).toInt()
                val current = monthGridMap[dayIndex]
                current?.add(skeletonEvent)
                monthGridMap[dayIndex] = current ?: arrayListOf(skeletonEvent)
                start = start.plusDays(1)

                // All day events end on next day 00:00 so we need to break loop to exclude end day
                if (start == end && (skeletonEvent.isAllDay() || partTimeEndsOnMidnight)) break
            }
        }
        logger.e("Test test monthGridMap ${monthGridMap.size}")


        // Reclaim all of the existing event views so we can reuse them if needed, this process
        // can be useful if your day view is hosted in a recycler view for example
        val recycled: List<View> = monthView.removeMonthViewEvents()
        var remaining = recycled.size

        val monthViewEventsMap = hashMapOf<Int, List<MonthView.MonthViewEvent>>()
        monthGridMap.forEach {
            // Sort the list and take first 3 events
            val sortedList = it.value.sortForAgendaView(timeZoneId).take(3)
            val monthViewEvents = arrayListOf<MonthView.MonthViewEvent>()
            sortedList.forEach { event ->
                val dayItemView =
                    if (remaining > 0) recycled[--remaining]
                    else LayoutInflater.from(requireContext()).inflate(
                        R.layout.item_month_view_event,
                        monthView,
                        false
                    )

                // TODO Apply style
                if (!isSkeletonEvent) {
                    val viewBackground: LayerDrawable = dayItemView.findViewById<View>(R.id.view_background).background as LayerDrawable
                    val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
                    val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)
                    viewMainSurface.setTint(Color.parseColor(event.calendar.color))
                    viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))

                    dayItemView.text_title.text = event.summary?.nullIfBlank() ?: resources.getString(R.string.default_event_summary)
                }

                val daySpanCount = event.calculateFullDayCounter(
                    event.getOccurrenceStart(timeZoneId).toLocalDate(),
                    timeZoneId
                ).second

                monthViewEvents.add(
                    MonthView.MonthViewEvent(
                        dayItemView,
                        daySpanCount
                    )
                )
            }
            monthViewEventsMap[it.key] = monthViewEvents
        }

        monthView.setMonthViewEvents(monthViewEventsMap)
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

    override fun onDestroyView() {
        super.onDestroyView()
        logger.e("Test test Month onDestroyView")
        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) {
            logger.e("Test test Month onDestroyView skeletonEventsLiveData removeObservers")
            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
        }
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers()) {
            logger.e("Test test Month onDestroyView eventsLiveData removeObservers")
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        monthView.removeMonthViewEvents()
    }
}
