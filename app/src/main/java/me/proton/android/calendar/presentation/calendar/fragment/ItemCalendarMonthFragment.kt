package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.databinding.ItemCalendarMonthFragmentBinding
import me.proton.android.calendar.databinding.ItemMonthViewGridBinding
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.COLUMNS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.MONTH_GRID_ITEMS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.ROWS_MAX
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

class ItemCalendarMonthFragment : Fragment(), KoinComponent {

    private var _binding: ItemCalendarMonthFragmentBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val logger: Logger by inject()

    private val fetchingEventsScope = CoroutineScope(Dispatchers.IO)

    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null

    private var skeletonList: List<LocalDate> = listOf()

    private var loading = true

    private lateinit var monthView: MonthView
    private var monthViewMaxEventCount = 0

    private lateinit var skeletonEventsLiveData: LiveData<CalendarsRepository.GetEventsResult<SkeletonEvent>>
    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<UiEvent>>

    private var events: List<UiEvent>? = null

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
    ): View {
        _binding = ItemCalendarMonthFragmentBinding.inflate(inflater, container, false)
        monthView = binding.monthFragmentMonthView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return
        val firstDayMonthView = immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->

            binding.monthFragmentWeekNumberLayout.visibleOrGone(displayWeekNumber)

            monthView.setShowWeekNumbers(displayWeekNumber)
        }

        val monthViewMediator = MediatorLiveData<Pair<String, DayOfWeek>>()
        monthViewMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }
        monthViewMediator.addSource(calendarViewModel.weekStart) { value ->
            val newWeekStart = value?.let { AndroidUtils.getWeekStartDayOfWeek(it) }

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

    private fun setWeekDaysHeader(weekStart: DayOfWeek, timeZoneId: String, firstDayMonthView: LocalDate) {
        val weekDays = DayOfWeek.values().toList()
        Collections.rotate(
            weekDays,
            CalendarSettings.DAYS_IN_A_WEEK - (weekStart.value - 1)
        )
        weekDays.forEachIndexed { index, dayOfWeek ->
            val textView = binding.monthFragmentWeekDaysLayout.getChildAt(index) as TextView
            val firstLetterDayOfWeek = dayOfWeek.format(firstLetter = true)
            textView.text = firstLetterDayOfWeek
            val currentDate = LocalDate.now(ZoneId.of(timeZoneId))
            if (dayOfWeek == currentDate.dayOfWeek && currentDate.month == firstDayMonthView.month && currentDate.year == firstDayMonthView.year) {
                textView.setTextColor(requireContext().getColorFromAttr(R.attr.proton_text_accent))
            } else {
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_weak))
            }
        }
    }

    private fun setupMonthViewGrid(forDate: LocalDate, timeZoneId: String, startWeekOn: DayOfWeek, position: Int) {
        // Set week days header
        setWeekDaysHeader(startWeekOn, timeZoneId, forDate)

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
        monthView.setTimeZoneId(timeZoneId)
        monthView.prepareMonthGrid(skeletonList, forDate.month)
        monthViewMaxEventCount = monthView.calculateMaxEventCount()

        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasObservers()) {
            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
        }
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        if (calendarViewModel.monthViewLoading.hasActiveObservers()) {
            calendarViewModel.monthViewLoading.removeObservers(viewLifecycleOwner)
        }

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
        binding.monthFragmentGridLayout.run {
            this.removeAllViews()

            // Set the row and column counts
            this.rowCount = ROWS_MAX
            this.columnCount = COLUMNS_MAX

            var skeletonListIndex = 0
            for (rowIndex in 0 until ROWS_MAX) {
                for (columnIndex in 0 until COLUMNS_MAX) {

                    val dayItemViewBinding = ItemMonthViewGridBinding.inflate(
                        LayoutInflater.from(this.context),
                        this,
                        false
                    )

                    // Set the view layout params so that the grid is split evenly
                    val row = GridLayout.spec(rowIndex, GridLayout.FILL, 1f)
                    val column = GridLayout.spec(columnIndex, GridLayout.FILL, 1f)
                    val layoutParams = GridLayout.LayoutParams(row, column)
                    dayItemViewBinding.root.layoutParams = layoutParams

                    if (skeletonListIndex <= skeletonList.lastIndex) {
                        // Make sure we don't go out of bound
                        val date = skeletonList[skeletonListIndex]

                        dayItemViewBinding.root.setOnSingleClickListener {
                            val selectedDateEvents = events?.filter {
                                it.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate() == date && it.spansSingleDay(true)
                            }
                            if (!selectedDateEvents.isNullOrEmpty()) {
                                // Save the time of the first event of the selected day in order for the day view to be
                                //  initialized on that position without waiting for the events loading to finish
                                calendarViewModel.firstEventOfTheDayTime =
                                    Collections.min(
                                        selectedDateEvents.map {
                                            it.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime()
                                        }
                                    )
                            } else {
                                calendarViewModel.firstEventOfTheDayTime = null
                            }

                            // Save the current month in order to know where to return if user presses back from day view
                            calendarViewModel.monthViewDate = date

                            // Set the selected date value and switch to day view
                            calendarViewModel.handleDaySelected(date)
                            calendarViewModel.viewMode.postValue(ViewMode.DAY)
                            mainViewModel.setLastViewMode(ViewMode.DAY)
                        }
                    }

                    this.addView(dayItemViewBinding.root)

                    skeletonListIndex++
                }
            }
        }
    }

    private fun getSkeletonEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, position: Int) {
        lifecycleScope.launch {
            calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId, coroutineScope = fetchingEventsScope)
        }

        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasActiveObservers()) {
            skeletonEventsLiveData.removeObservers(viewLifecycleOwner)
        }

        // Get and display skeleton events
        //skeletonEventsLiveData = calendarViewModel.getSkeletonEvents(fromDate, toDate, timeZoneId)
        // TODO skeleton events disabled for now, just load the UiEvents right away:
        getEvents(fromDate, toDate, timeZoneId, position)

        /*skeletonEventsLiveData.observe(viewLifecycleOwner) { skeletonEventsResult ->
            when (skeletonEventsResult) {
                CalendarsRepository.GetEventsResult.InProgress -> {
                    // Show progress bar
                    binding.monthFragmentLoader.visibleOrGone(true)
                }
                is CalendarsRepository.GetEventsResult.Success -> {

                    // Display the skeleton events in the month view
                    //displayMonthViewSkeletonEvents(skeletonEventsResult.events, fromDate, timeZoneId)

                    // Hide progress bar
                    binding.monthFragmentLoader.visibleOrGone(false)

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
                    binding.monthFragmentLoader.visibleOrGone(false)

                    // TODO Show the error somewhere ?
                }
            }
        }*/
    }

    private fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, position: Int) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        // Get and display decrypted events
        eventsLiveData = calendarViewModel.getUiEvents(fromDate, toDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->
            val useMonthViewLoader = !monthView.eventsDrawn
            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        if (this::skeletonEventsLiveData.isInitialized && skeletonEventsLiveData.hasActiveObservers()) {
                            // Progress is shown through the skeleton events // TODO not anymore because we disabled them, so display loader here:
                            if (useMonthViewLoader) binding.monthFragmentLoader.visibleOrGone(true)
                            else calendarViewModel.setLoading(true)
                        } else {
                            // Show progress bar
                            if (useMonthViewLoader) binding.monthFragmentLoader.visibleOrGone(true)
                            else calendarViewModel.setLoading(true)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        if (useMonthViewLoader) binding.monthFragmentLoader.visibleOrGone(false)
                        else calendarViewModel.setLoading(false)

                        val alphabeticallySortedEvents = it.events.sortedBy { event -> event?.summary }
                        events = alphabeticallySortedEvents

                        // Display the UI events in the month view
                        displayMonthViewUiEvents(alphabeticallySortedEvents, fromDate, timeZoneId)

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

                        if (useMonthViewLoader) binding.monthFragmentLoader.visibleOrGone(false)
                        else calendarViewModel.setLoading(false)

                        // TODO Show the error somewhere ?
                    }
                }
            }
        }
    }

    // TODO disabled skeletons for now
    /*private fun displayMonthViewSkeletonEvents(events: List<Event>, fromDate:LocalDate, timeZoneId: String) {
        val newMaxEventCount = monthView.calculateMaxEventCount()
        if (newMaxEventCount > monthViewMaxEventCount) monthViewMaxEventCount = newMaxEventCount

        lifecycleScope.launch {

            // Get the map of MonthViewEvent indexed by day
            val monthViewEventsMap = calendarViewModel.getMonthViewEventsMap(events, fromDate, monthViewMaxEventCount, timeZoneId, true)

            // Set the month view events so that they can be drawn
            monthView.setMonthViewEvents(monthViewEventsMap, calendarViewModel.displayWeekNumber.value ?: false, monthViewMaxEventCount)
        }
    }*/

    private fun displayMonthViewUiEvents(events: List<UiEvent>, fromDate:LocalDate, timeZoneId: String) {
        val newMaxEventCount = monthView.calculateMaxEventCount()
        if (newMaxEventCount > monthViewMaxEventCount) monthViewMaxEventCount = newMaxEventCount

        lifecycleScope.launch {

            // Get the map of MonthViewEvent indexed by day
            val monthViewEventsMap = calendarViewModel.getMonthViewEventsMap(
                events,
                fromDate,
                monthViewMaxEventCount,
                false
            )

            // Set the month view events so that they can be drawn
            monthView.setMonthViewEvents(monthViewEventsMap, calendarViewModel.displayWeekNumber.value ?: false, monthViewMaxEventCount)
        }
    }

    private fun setupWeekNumbers(firstDay: LocalDate, startWeekOn: DayOfWeek) {
        // Setup week numbers
        binding.monthFragmentWeekNumberLayout.run {
            for (i in 0 until 6) {
                val weekNumberLinearLayout = this.getChildAt(i) as LinearLayout
                val weekNumberTextView = weekNumberLinearLayout.getChildAt(0) as TextView
                weekNumberTextView.text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (fetchingEventsScope.isActive) fetchingEventsScope.cancel()
        if (loading) {
            position?.let { calendarViewModel.monthViewLoading.value = Pair(it, false) }
        }
        _binding = null
    }
}
