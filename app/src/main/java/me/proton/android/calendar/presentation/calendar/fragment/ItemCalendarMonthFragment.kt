package me.proton.android.calendar.presentation.calendar.fragment

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
import biweekly.parameter.ParticipationStatus
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.android.synthetic.main.item_calendar_month_fragment.*
import kotlinx.android.synthetic.main.item_month_view_grid.view.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.utils.AndroidUtils
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
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.COLUMNS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.MINI_EVENTS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.MONTH_GRID_ITEMS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.ROWS_MAX
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

    private var skeletonList: List<LocalDate> = listOf()

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

    override fun onResume() {
        super.onResume()
        calendarViewModel.resumedMonthViewPosition.value = position
        position?.let { calendarViewModel.monthViewLoading.value = Pair(it, loading) }
        timeZoneId?.let {
            // Set the grid selectable items
            setMonthGridClickableItems(skeletonList, it)
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
        // Prepare the list of dates we'll display in the month view
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset =
            if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + CalendarSettings.DAYS_IN_A_WEEK
            else firstDayOfTheWeekNumber

        setupWeekNumbers(firstDayOfTheMonth, startWeekOn)

        val previousMonthDayItems = (0 until firstDayOfTheWeekOffset).map {
            firstDayOfTheMonth.minusDays(it.toLong() + 1)
        }.reversed()

        val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
            firstDayOfTheMonth.plusDays(it.toLong())
        }

        val lastDayOfMonthOffset = MONTH_GRID_ITEMS_MAX - (previousMonthDayItems.size + dayItems.size)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            .plusDays(lastDayOfMonthOffset.toLong())

        val upcomingMonthDayItems = (0 until lastDayOfMonthOffset).map {
            firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(it.toLong() + 1)
        }

        skeletonList = AndroidUtils.concatenate(previousMonthDayItems, dayItems, upcomingMonthDayItems)

        // Save the skeleton list in the month view so that it can be drawn along with the events
        monthView.prepareMonthGrid(skeletonList, forDate.month)

        // Check if we load the events now or if we need to wait
        if (this.isResumed) {
            // Set the grid selectable items
            setMonthGridClickableItems(skeletonList, timeZoneId)
            // We first load and display the events for the selected month
            calendarViewModel.monthViewLoading.value = Pair(position, true)
            getSkeletonEvents(fromDate!!, toDate!!, timeZoneId, position)
        } else {
            loading = false
            calendarViewModel.monthViewLoading.observe(viewLifecycleOwner) { monthViewLoading ->
                if ((this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) ||
                    (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers())) {
                    // Remove the monthViewLoading observers if we started loading the events for that fragment as it won't be needed anymore
                    calendarViewModel.monthViewLoading.removeObservers(viewLifecycleOwner)
                    return@observe
                }

                // Load the events for that fragment if no other fragment is currently doing the same process
                val resumedMonthViewPosition = calendarViewModel.resumedMonthViewPosition.value
                if ((this.isResumed || (resumedMonthViewPosition != null && (position == resumedMonthViewPosition + 1 || position == resumedMonthViewPosition - 1))) &&
                    !loading && (monthViewLoading.first == position || !monthViewLoading.second)) {
                    loading = true
                    calendarViewModel.monthViewLoading.value = Pair(position, true)
                    getSkeletonEvents(fromDate, toDate, timeZoneId, position)
                }
            }
        }
    }

    private fun setMonthGridClickableItems(skeletonList: List<LocalDate>, timeZoneId: String) {
        // Set the clickable grid items
        view?.findViewById<GridLayout>(R.id.month_fragment_grid_layout)?.run {
            this.removeAllViews()

            // Set the row and column counts
            this.rowCount = ROWS_MAX
            this.columnCount = COLUMNS_MAX

            var skeletonListIndex = 0
            for (rowIndex in 0 until ROWS_MAX) {
                for (columnIndex in 0 until COLUMNS_MAX) {

                    val dayItemView = LayoutInflater.from(this.context).inflate(
                        R.layout.item_month_view_grid,
                        this,
                        false
                    )

                    // Set the view layout params so that the grid is split evenly
                    val row = GridLayout.spec(rowIndex, GridLayout.FILL, 1f)
                    val column = GridLayout.spec(columnIndex, GridLayout.FILL, 1f)
                    val layoutParams = GridLayout.LayoutParams(row, column)
                    dayItemView.layoutParams = layoutParams

                    if (skeletonListIndex <= skeletonList.lastIndex) {
                        // Make sure we don't go out of bound
                        val date = skeletonList[skeletonListIndex]

                        dayItemView.setOnSingleClickListener {
                            val selectedDateEvents = events?.filter {
                                it.getOccurrenceStart(timeZoneId).toLocalDate() == date && it.spansSingleDay(true, timeZoneId)
                            }
                            if (selectedDateEvents != null && selectedDateEvents.isNotEmpty()) {
                                // Save the time of the first event of the selected day in order for the day view to be
                                //  initialized on that position without waiting for the events loading to finish
                                calendarViewModel.firstEventOfTheDayTime =
                                    Collections.min(
                                        selectedDateEvents.map {
                                            it.getOccurrenceStart(timeZoneId).toLocalTime()
                                        }
                                    )
                            }

                            // Save the current month in order to know where to return if user presses back from day view
                            calendarViewModel.monthViewDate = calendarViewModel.selectedDate.value

                            // Set the selected date value and switch to day view
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
    }

    private fun getSkeletonEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, position: Int) {
        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasActiveObservers()) {
            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
        }
        // Get and display skeleton events
        skeletonEventsLiveData = calendarViewModel.getSkeletonEvents(fromDate, toDate, timeZoneId)
        skeletonEventsLiveData.observe(viewLifecycleOwner) { skeletonEventsResult ->
            when (skeletonEventsResult) {
                CalendarsRepository.GetEventsResult.InProgress -> {
                    // Show progress bar
                    month_fragment_loader.visibleOrGone(true)
                }
                is CalendarsRepository.GetEventsResult.Success -> {

                    // Display the skeleton events in the month view
                    displayMonthViewEvents(skeletonEventsResult.events, fromDate, timeZoneId, true)

                    // Hide progress bar
                    month_fragment_loader.visibleOrGone(false)

                    // Load the decrypted events list
                    getEvents(fromDate, toDate, timeZoneId, position)
                }
                is CalendarsRepository.GetEventsResult.Exception -> {
                    loading = false
                    calendarViewModel.monthViewLoading.value = Pair(position, false)
                    logger.e(
                        "ItemCalendarMonthFragment getSkeletonEvents exception getting skeletonEventsLiveData",
                        skeletonEventsResult.throwable
                    )
                    // Hide progress bar
                    month_fragment_loader.visibleOrGone(false)

                    // TODO Show the error somewhere ?
                }
            }
        }
    }

    private fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, position: Int) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        // Get and display decrypted events
        eventsLiveData = calendarViewModel.getEvents(fromDate, toDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasActiveObservers()) {
                            // Progress is shown through the skeleton events
                        } else {
                            // Show progress bar
                            month_fragment_loader.visibleOrGone(true)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        val alphabeticallySortedEvents = it.events.sortedBy { event -> event?.summary }
                        events = alphabeticallySortedEvents

                        // Display the skeleton events in the month view
                        displayMonthViewEvents(alphabeticallySortedEvents, fromDate, timeZoneId, false)

                        loading = false
                        // Clear monthViewLoading value so that we can load the adjacent fragments content
                        calendarViewModel.monthViewLoading.value = Pair(position, false)

                        // Remove skeletonEventsLiveData observers as this won't be needed anymore
                        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) {
                            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        loading = false
                        calendarViewModel.monthViewLoading.value = Pair(position, false)

                        // TODO Show the error somewhere ?
                    }
                }
            }
        }
    }

    private fun displayMonthViewEvents(events: List<Event>, fromDate:LocalDate, timeZoneId: String, isSkeletonEvent: Boolean) {
        val monthGridMap = mutableMapOf<Int, ArrayList<Event>>()
        // Split the events for each day of the month
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
            // Filter out the events spanning multiple days if it is not the first day
            val filteredList = it.value.filterNot { event ->
                it.key != 0 &&
                        !event.spansSingleDay(timeZoneId = timeZoneId) &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(it.key.toLong()),
                            timeZoneId
                        ).first > 1
            }
            // Sort the list for the month view
            val sortedList: MutableList<Event> = filteredList.sortForMonthView(timeZoneId).toMutableList()
            it.value.clear()
            it.value.addAll(sortedList)
        }

        val rootMap: MutableMap<Int, Map<Int, Event>> = mutableMapOf()
        val maxEventCount = monthView.getMaxEventCount()
        for (key in 0 until MONTH_GRID_ITEMS_MAX) {
            val eventList = monthGridMap[key]

            val childMap = mutableMapOf<Int, Event>()
            if (key > 0) {
                // Insert the events spanning multiple days depending on the previous day list, in order to extend the multi day event on this day with the same index
                val previousChildMap = rootMap[key - 1]
                previousChildMap?.forEach { (index, event) ->
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
            // Fill the remaining indexes with the sorted events list
            eventList?.forEach { event ->
                while (childMap.containsKey(nextAvailableMapIndex)) nextAvailableMapIndex++
                if (nextAvailableMapIndex >= maxEventCount + MINI_EVENTS_MAX + 1) return@forEach
                childMap[nextAvailableMapIndex] = event
                nextAvailableMapIndex++
            }

            rootMap[key] = childMap
        }

        lifecycleScope.launch {
            val userEmails = calendarViewModel.getUserEmails()

            // Transform the child map of events to a list of MonthViewEvent
            rootMap.forEach { (dayIndex, childMap) ->

                val monthViewEvents = arrayListOf<MonthView.MonthViewEvent>()

                childMap.forEach { (indexInDay, event) ->

                    val fullDayCounter = event.calculateFullDayCounter(
                        fromDate.plusDays(dayIndex.toLong()),
                        timeZoneId
                    )
                    val participationStatus =
                        if (userEmails != null) event.getParticipationStatus(userEmails)
                        else null

                    monthViewEvents.add(
                        MonthView.MonthViewEvent(
                            indexInDay = indexInDay,
                            daySpanCount = fullDayCounter.second,
                            daySpanIndex = fullDayCounter.first,
                            calendarColor = if (isSkeletonEvent) ContextCompat.getColor(requireContext(), R.color.interaction_weak_norm)
                            else Color.parseColor(event.calendar.color),
                            pastEvent = event.isInThePast(timeZoneId),
                            isUnanswered = !event.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
                            strikeThroughTitle = event.isCancelled() || participationStatus == ParticipationStatus.DECLINED,
                            decryptionFailed = event.decryptionStatus == Event.DecryptionStatus.FAILURE,
                            eventTitle = if (isSkeletonEvent) null
                            else event.summary?.nullIfBlank() ?: resources.getString(R.string.default_event_summary)
                        )
                    )
                }
                monthViewEventsMap[dayIndex] = monthViewEvents
            }

            // Set the month view events so that they can be drawn
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
        // Remove all existing observers
        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) {
            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
        }
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers()) {
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        if (loading) {
            position?.let { calendarViewModel.monthViewLoading.value = Pair(it, false) }
        }
    }
}
