package me.proton.android.calendar.presentation.calendar.fragment

import android.content.res.ColorStateList
import android.graphics.Color
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import biweekly.parameter.ParticipationStatus
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.android.synthetic.main.item_calendar_month_fragment.*
import kotlinx.android.synthetic.main.item_month_view_grid.view.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sortForMonthView
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.util.kotlin.nullIfBlank
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList

class ItemCalendarMonthFragment : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val mainViewModel: MainViewModel by sharedViewModel()

    private val logger: Logger by inject()

    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null
    private val monthViewMediator = MediatorLiveData<Pair<String, DayOfWeek>>()

    private var loading = true

    private lateinit var monthView: MonthView

    private lateinit var skeletonEventsLiveData: LiveData<CalendarsRepository.GetEventsResult<SkeletonEvent>>
    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<Event>>

    private var events: List<Event>? = null

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

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->

            monthFragmentWeekNumberLayout.visibleOrGone(displayWeekNumber)

            monthView.setShowWeekNumbers(displayWeekNumber)
        }

        monthViewMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }
        monthViewMediator.addSource(calendarViewModel.weekStart) { value ->
            val newWeekStart = value?.let { AndroidUtils.getWeekStartDayOfWeek(it) }

            if (newWeekStart != null && newWeekStart != weekStart) {
                val weekDays = DayOfWeek.values().toList()
                Collections.rotate(
                    weekDays,
                    CalendarSettings.DAYS_IN_A_WEEK - (newWeekStart.value - 1)
                )
                var weekDaysViewIndex = 0
                weekDays.forEach { dayOfWeek ->
                    val textView = monthFragmentWeekDaysLayout.getChildAt(weekDaysViewIndex) as TextView
                    val firstLetterDayOfWeek = dayOfWeek.format(firstLetter = true)
                    textView.text = firstLetterDayOfWeek
                    if (dayOfWeek == LocalDate.now().dayOfWeek && LocalDate.now().month == firstDayMonthView.month && LocalDate.now().year == firstDayMonthView.year) {
                        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_norm))
                    } else {
                        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    }
                    weekDaysViewIndex++
                }
            }

            weekStart = newWeekStart

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }

        monthViewMediator.observe(viewLifecycleOwner) {
            it?.let {
                setupMonthViewGrid(firstDayMonthView, it.first, it.second, immutablePosition)
            }
        }
    }

    private fun setupMonthViewGrid(forDate: LocalDate, timeZoneId: String, startWeekOn: DayOfWeek, position: Int) {
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

        monthView.prepareMonthGrid(skeletonList.map { it.date }, forDate.month)

        // Set the clickable grid items
        view?.findViewById<GridLayout>(R.id.month_fragment_grid_layout)?.run {
            this.removeAllViews()
            var skeletonListIndex = 0
            for (rowIndex in 0 until 6) {
                for (columnIndex in 0 until 7) {

                    val dayItemView = LayoutInflater.from(this.context).inflate(
                        R.layout.item_month_view_grid,
                        this,
                        false
                    )

                    val row = GridLayout.spec(rowIndex, GridLayout.FILL, 1f)
                    val column = GridLayout.spec(columnIndex, GridLayout.FILL, 1f)

                    val layoutParams = GridLayout.LayoutParams(row, column)
                    dayItemView.layoutParams = layoutParams

                    if (skeletonListIndex <= skeletonList.lastIndex) {
                        // Make sure we don't go out of bound
                        val date = skeletonList[skeletonListIndex].date
                        dayItemView.setOnSingleClickListener {
                            val selectedDateEvents = events?.filter {
                                it.getOccurrenceStart(timeZoneId).toLocalDate() == date && it.spansSingleDay(true, timeZoneId)
                            }
                            if (selectedDateEvents != null && selectedDateEvents.isNotEmpty()) {
                                calendarViewModel.firstEventOfTheDayTime =
                                    Collections.min(
                                        selectedDateEvents.map {
                                            it.getOccurrenceStart(timeZoneId).toLocalTime()
                                        }
                                    )
                            }

                            calendarViewModel.monthViewDate = calendarViewModel.selectedDate.value

                            calendarViewModel.handleDaySelected(date)
                            calendarViewModel.viewMode.postValue(ViewMode.DAY)
                            mainViewModel.setViewMode(ViewMode.DAY)
                        }
                    }

                    this.addView(dayItemView)

                    skeletonListIndex++
                }
            }

        }

        if (this.isResumed) {
            calendarViewModel.monthViewLoading.value = Pair(position, true)
            getSkeletonEvents(fromDate, toDate, timeZoneId, position)
        } else {
            loading = false
            calendarViewModel.monthViewLoading.observe(viewLifecycleOwner) { monthViewLoading ->
                if ((this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) ||
                    (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers())) {
                    calendarViewModel.monthViewLoading.removeObservers(viewLifecycleOwner)
                    return@observe
                }
                if (!loading && (monthViewLoading.first == position || !monthViewLoading.second)) {
                    loading = true
                    calendarViewModel.monthViewLoading.value = Pair(position, true)
                    getSkeletonEvents(fromDate, toDate, timeZoneId, position)
                }
            }
        }
    }

    private fun getSkeletonEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, position: Int) {
        calendarViewModel.lifeCycleScope.launch {

            // Get and display skeleton events
            skeletonEventsLiveData = calendarViewModel.getSkeletonEvents(fromDate, toDate, timeZoneId)
            month_fragment_loader.visibleOrGone(true)
            skeletonEventsLiveData.observe(viewLifecycleOwner) { skeletonEventsResult ->
                when (skeletonEventsResult) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        // TODO Loader for fetch state
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        displayMonthViewEvents(skeletonEventsResult.events, fromDate, timeZoneId, true)

                        month_fragment_loader.visibleOrGone(false)

                        getEvents(fromDate, toDate, timeZoneId, position)
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        loading = false
                        calendarViewModel.monthViewLoading.value = Pair(position, false)
                        logger.e(
                            "ItemCalendarMonthFragment getSkeletonEvents exception getting skeletonEventsLiveData",
                            skeletonEventsResult.throwable
                        )
                        // TODO Error somewhere
                        month_fragment_loader.visibleOrGone(false)
                    }
                }
            }
        }
    }

    private fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, position: Int) {
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

                        events = it.events

                        displayMonthViewEvents(it.events, fromDate, timeZoneId, false)
                        loading = false
                        calendarViewModel.monthViewLoading.value = Pair(position, false)

                        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) {
                            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        loading = false
                        calendarViewModel.monthViewLoading.value = Pair(position, false)
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

        val monthViewEventsMap = hashMapOf<Int, List<MonthView.MonthViewEvent>>()

        monthGridMap.forEach {

            // Sort the list
            val filteredList = it.value.filterNot { event ->
                it.key != 0 &&
                        !event.spansSingleDay(timeZoneId = timeZoneId) &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(it.key.toLong()),
                            timeZoneId
                        ).first > 1
            }
            val sortedList: MutableList<Event> = filteredList.sortForMonthView(timeZoneId).toMutableList()
            it.value.clear()
            it.value.addAll(sortedList)
        }

        val rootMap: MutableMap<Int, Map<Int, Event>> = mutableMapOf()
        val maxEventCount = monthView.getMaxEventCount()
        for (key in 0 until 42) {
            val events = monthGridMap[key]

            val childMap = mutableMapOf<Int, Event>()
            if (key > 0) {
                val previousChildMap = rootMap[key - 1]
                previousChildMap?.forEach { index, event ->
                    if (!event.spansSingleDay(timeZoneId = timeZoneId) &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(key.toLong()),
                            timeZoneId
                        ).first > 1) {
                        childMap[index] = event
                    }
                }
            }
            var nextAvailableMapIndex = 0
            events?.forEachIndexed { index, event ->
                // TODO Maybe store MonthEventViews here directly ?
                while (childMap.containsKey(nextAvailableMapIndex)) nextAvailableMapIndex++
                // TODO EXTRACT MAX
                if (nextAvailableMapIndex >= maxEventCount + 3) return@forEachIndexed // TODO EXTRACT MAX
                // TODO EXTRACT MAX
                childMap[nextAvailableMapIndex] = event
                nextAvailableMapIndex++
            }

            rootMap[key] = childMap
        }

        lifecycleScope.launch {
            val userEmails = calendarViewModel.getUserEmails()
            rootMap.forEach { (dayIndex, childMap) ->

                val monthViewEvents = arrayListOf<MonthView.MonthViewEvent>()

                // Take first 4 events
                childMap.forEach { (indexInDay, event) ->

                    val fullDayCounter = event.calculateFullDayCounter(
                        fromDate.plusDays(dayIndex.toLong()),
                        timeZoneId
                    )

                    val participationStatus = if (userEmails != null) event.getParticipationStatus(userEmails) else null
                    monthViewEvents.add(
                        MonthView.MonthViewEvent(
                            null,
                            indexInDay,
                            fullDayCounter.second,
                            fullDayCounter.first,
                            if (isSkeletonEvent) ContextCompat.getColor(requireContext(), R.color.interaction_weak_norm)
                            else Color.parseColor(event.calendar.color),
                            event.isInThePast(timeZoneId),
                            !event.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
                            event.isCancelled() || participationStatus == ParticipationStatus.DECLINED,
                            event.decryptionStatus == Event.DecryptionStatus.FAILURE,
                            if (isSkeletonEvent) null
                            else event.summary?.nullIfBlank() ?: resources.getString(R.string.default_event_summary)
                        )
                    )
                }
                monthViewEventsMap[dayIndex] = monthViewEvents
            }

            monthView.setMonthViewEvents(monthViewEventsMap, calendarViewModel.displayWeekNumber.value ?: false)
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

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) {
            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
        }
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers()) {
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
    }

    override fun onResume() {
        super.onResume()
        position?.let {
            calendarViewModel.monthViewLoading.value = Pair(it, loading)
        }
    }
}
