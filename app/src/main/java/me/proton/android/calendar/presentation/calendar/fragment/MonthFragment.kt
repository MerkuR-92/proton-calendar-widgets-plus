package me.proton.android.calendar.presentation.calendar.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Operation
import com.alamkanak.weekview.jsr310.firstVisibleDateAsLocalDate
import com.alamkanak.weekview.jsr310.scrollToDate
import com.alamkanak.weekview.jsr310.scrollToDateTime
import com.alamkanak.weekview.jsr310.setDateFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_base.fragment_progress_bar
import kotlinx.android.synthetic.main.fragment_base.fragment_toolbar_content
import kotlinx.android.synthetic.main.fragment_base.fragment_toolbar_title_layout
import kotlinx.android.synthetic.main.fragment_base.mini_calendar_chevron
import kotlinx.android.synthetic.main.fragment_month.agendaPager
import kotlinx.android.synthetic.main.fragment_month.fragmentMonthLayout
import kotlinx.android.synthetic.main.fragment_month.miniCalendarDaysHeaderLayout
import kotlinx.android.synthetic.main.fragment_month.miniCalendarLayout
import kotlinx.android.synthetic.main.fragment_month.miniCalendarPager
import kotlinx.android.synthetic.main.fragment_month.miniCalendarPagerLayout
import kotlinx.android.synthetic.main.fragment_month.mini_calendar_slider
import kotlinx.android.synthetic.main.fragment_month.viewPagerSliderGuideline
import kotlinx.android.synthetic.main.fragment_month.viewPagerTopGuideline
import kotlinx.android.synthetic.main.fragment_month.weekView
import kotlinx.android.synthetic.main.toolbar_action_button.view.imageButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.animateGuidelineHeightChange
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatMonth
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.SpotlightUtils.showLastSpotlightDialog
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.WeekViewCalendarEntity
import me.proton.android.calendar.domain.model.getActualEventId
import me.proton.android.calendar.domain.model.toWeekViewCalendarEntityEvent
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.adapter.WeekViewAdapter
import me.proton.android.calendar.presentation.calendar.customView.MonthLayoutGestureListener
import me.proton.android.calendar.presentation.calendar.fragment.ItemMiniCalendarFragment.Companion.calculateAdapterHeight
import me.proton.android.calendar.presentation.calendar.pagerAdapter.AgendaPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.MiniCalendarPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.MonthPagerAdapter
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class MonthFragment : BaseFragment() {

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    @Inject
    lateinit var handleAlarmsUseCase: HandleAlarmsUseCase
    @Inject
    lateinit var logger: Logger

    private lateinit var miniCalendarPagerAdapter: MiniCalendarPagerAdapter
    private lateinit var agendaPagerAdapter: AgendaPagerAdapter
    private lateinit var monthPagerAdapter: MonthPagerAdapter

    private val navigationArguments: MonthFragmentArgs by navArgs()

    private lateinit var toolbarTitle: TextView

    private val mainViewModel: MainViewModel by viewModels()

    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_month

    private lateinit var buttonCreate: View
    private lateinit var buttonToday: View

    private lateinit var miniCalendarPageChangeCallback: ViewPager2.OnPageChangeCallback

    private var startWeekOn: DayOfWeek? = null
    private var timeZoneId: String? = null

    private var currentViewMode: ViewMode? = null

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_button, fragment_toolbar_content, false)
        with (buttonCreate) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_proton_plus))
        }
        buttonToday = layoutInflater.inflate(R.layout.toolbar_action_button, fragment_toolbar_content, false)
        with (buttonToday) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_calendar_today_indicator))
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.fragment_toolbar_content)) {
            addView(
                buttonToday, resources.getDimensionPixelSize(
                    R.dimen.action_clickable_size
                ), resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )

            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                buttonCreate, layoutParams
            )
        }

        toolbarTitle = toolbar.findViewById(R.id.fragment_toolbar_title)
    }

    private fun setToolbarListeners(timeZoneId: ZoneId) {
        buttonCreate.setOnSingleClickListener {
            lifecycleScope.launch {
                val hasWritableActiveCalendars = calendarViewModel.getActiveUserCalendars()?.let { activeUserCalendars ->
                    activeUserCalendars.any { it.allowEditEvents }
                } ?: false
                if (hasWritableActiveCalendars) {
                    // Each item in the adapter is one day
                    calendarViewModel.selectedDate.value?.let { currentDate ->

                        val date = if (currentViewMode == ViewMode.MONTH) {
                            if (currentDate.month == LocalDate.now().month && currentDate.year == LocalDate.now().year) LocalDate.now()
                            else currentDate.withDayOfMonth(1)
                        } else currentDate

                        requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                            .navigate(
                                Navigation.Deeplink.toEventCreate(
                                    date,
                                    ICalUtilsImpl.generateEventStartTime(timeZoneId)
                                )
                            )
                    }
                } else {
                    requireActivity().displaySnackBar(resources.getString(R.string.snack_create_event_no_active_personal_calendar))
                }
            }
        }

        with(buttonToday) {
            // Display current day number
            val textField = (findViewById<TextView>(R.id.toolbarActionSecondaryText))
            textField.text = LocalDate.now(timeZoneId).dayOfMonth.toString()
            textField.visibility = View.VISIBLE
        }
        buttonToday.setOnSingleClickListener {
            val todayDate = LocalDate.now(timeZoneId)
            calendarViewModel.handleDaySelected(todayDate)
            // Align day view to current time
            calendarViewModel.jumpToCurrentTime.value = true

            weekView.scrollToDateTime(dateTime = LocalDateTime.now(timeZoneId))
        }
    }

    private fun setMiniCalendarPageChangeCallback(startWeekOn: DayOfWeek) {
        miniCalendarPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                val monthStartingPosition = miniCalendarPagerAdapter.startingPosition
                val monthStartingDate = miniCalendarPagerAdapter.firstDayOfMonth
                val firstDay = monthStartingDate.plusMonths((position - monthStartingPosition).toLong())

                calendarViewModel.handleDaySelected(firstDay, fromMonthPagerCallback = true)

                setToolbarMonthYearTitle(firstDay, miniCalendarPager.currentItem)

                adjustMiniCalendarView(firstDay.withDayOfMonth(1), startWeekOn)

                timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
            }
        }
    }

    private val agendaPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // Agenda view pager is hidden when displaying other views
            if (currentViewMode == ViewMode.AGENDA) {
                val currentDate =
                    calendarViewModel.initialToday.plusDays((agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
                calendarViewModel.handleDaySelected(currentDate)
            }
        }
    }

    private fun updateMiniCalendarHeight(
        startWeekOn: DayOfWeek,
        isMonthView: Boolean,
        animateChange: Boolean
    ) {

        val firstDayOfMonth = calendarViewModel.selectedDate.value?.withDayOfMonth(1) ?: return // We will retry this once calendarViewModel.selectedDate has been set

        // Calculate current month's desired height for both mini calendar mode (month / week)
        val desiredHeight = calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )
        if (isMonthView) {
            calendarViewModel.currentPosDesiredMonthHeight = desiredHeight
        } else {
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        if (animateChange) {
            viewPagerTopGuideline?.animateGuidelineHeightChange(
                desiredHeight,
                calendarViewModel.currentPosDesiredMonthHeight
            ) {
            }
            viewPagerSliderGuideline?.animateGuidelineHeightChange(
                desiredHeight - requireContext().resources.getDimensionPixelSize(
                    R.dimen.calendar_slider_height
                ), calendarViewModel.currentPosDesiredMonthHeight
            ) { }
        } else {
            (viewPagerTopGuideline?.layoutParams as? ConstraintLayout.LayoutParams)?.let { layoutParams ->
                layoutParams.guideBegin = desiredHeight
                viewPagerTopGuideline?.layoutParams = layoutParams
            }

            (viewPagerSliderGuideline?.layoutParams as? ConstraintLayout.LayoutParams)?.let { sliderLayoutParams ->
                sliderLayoutParams.guideBegin = desiredHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height)
                viewPagerSliderGuideline?.layoutParams = sliderLayoutParams
            }
        }
    }

    override fun onStart() {
        super.onStart()

        calendarViewModel.showAutoDetectPrimaryTimezone = true
        calendarViewModel.initialAutoDetectPrimaryTimezoneValue = null
        calendarViewModel.autoDetectPrimaryTimezone.observe(viewLifecycleOwner) { autoDetectPrimaryTimezone ->
            autoDetectPrimaryTimezone ?: return@observe
            lifecycleScope.launch {
                calendarViewModel.handleAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone, requireContext())
            }
        }
    }

    private fun adjustMiniCalendarView(firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek) {

        // Mini calendar is hidden when displaying Month view
        if (currentViewMode == ViewMode.MONTH) return

        // ViewPager will adjust its height to the largest item it contains and display empty space for
        // smaller items, like months with fewer week lines. That's why we need to resize it every time we
        // display a month

        val isMonthView = calendarViewModel.monthView.value ?: true

        // Calculate current month's desired height for both mini calendar mode (month / week)
        val desiredHeight = calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )
        if (isMonthView) {
            calendarViewModel.currentPosDesiredMonthHeight = desiredHeight
        } else {
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        viewPagerTopGuideline?.animateGuidelineHeightChange(desiredHeight, null) {
        }
        viewPagerSliderGuideline?.animateGuidelineHeightChange(
            desiredHeight - requireContext().resources.getDimensionPixelSize(
                R.dimen.calendar_slider_height
            ), null
        ) { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(
            miniCalendarPageChangeCallback
        )
        agendaPager.unregisterOnPageChangeCallback(agendaPageChangeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        miniCalendarPagerAdapter =
            MiniCalendarPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))
        monthPagerAdapter =
            MonthPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))

        val monthStartingPosition = miniCalendarPagerAdapter.startingPosition

        miniCalendarPager.apply {
            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(monthStartingPosition, false)
        }

        agendaPagerAdapter = AgendaPagerAdapter(requireActivity(), calendarViewModel.initialToday)

        calendarViewModel.viewMode.value?.let { viewMode ->
            when (viewMode) {
                ViewMode.DAY -> weekView.numberOfVisibleDays =  1
                ViewMode.THREE_DAY -> weekView.numberOfVisibleDays =  3
                ViewMode.WEEK -> weekView.numberOfVisibleDays =  7
                else -> {}
            }
            fragmentMonthLayout.viewMode = viewMode
            initAgendaPager(viewMode)
        }

        calendarViewModel.loading.observe(viewLifecycleOwner) { loading ->
            fragment_progress_bar?.visibleOrInvisible(loading)
        }

        // Switch between Agenda and Day views
        calendarViewModel.viewMode.observe(viewLifecycleOwner) { viewMode ->
            when (viewMode) {
                ViewMode.DAY -> weekView.numberOfVisibleDays =  1
                ViewMode.THREE_DAY -> weekView.numberOfVisibleDays =  3
                ViewMode.WEEK -> weekView.numberOfVisibleDays =  7
                else -> {}
            }
            fragmentMonthLayout.viewMode = viewMode
            initAgendaPager(viewMode)
        }

        agendaPager.registerOnPageChangeCallback(agendaPageChangeCallback)

        // Init selected date
        val navArgsDate = navigationArguments.date
        val navigationDate = if (navArgsDate == null) {
            null
        } else {
            try {
                LocalDate.parse(
                    navArgsDate,
                    DateTimeFormatter.ISO_LOCAL_DATE
                )
            } catch (e: DateTimeParseException) {
                null
            }
        }
        calendarViewModel.handleInitialDaySelection(navigationDate ?: calendarViewModel.initialToday)

        calendarViewModel.selectedDate.observe(viewLifecycleOwner) { selectedDate ->
            setToolbarMonthYearTitle(selectedDate, miniCalendarPager.currentItem)

            lifecycleScope.launch {
                if (currentFromDate?.month != selectedDate.month) {
                    val fromDate = selectedDate.minusMonths(1).withDayOfMonth(1)
                    val toDate = selectedDate.plusMonths(1).withDayOfMonth(selectedDate.plusMonths(1).lengthOfMonth())
                    val timeZoneId = calendarViewModel.getTimeZoneId()?.id
                    getEvents(fromDate, toDate, timeZoneId ?: return@launch)
                    currentFromDate = selectedDate
                }
            }

            if (weekView.firstVisibleDateAsLocalDate != selectedDate) {
                weekView.scrollToDate(selectedDate)
            }

            lifecycleScope.launch {
                val firstDayOfMonth = selectedDate.withDayOfMonth(1)
                val weekStart = calendarViewModel.getWeekStart() ?: return@launch
                val startWeekOn = getWeekStartDayOfWeek(weekStart)
                if (calendarViewModel.currentPosDesiredMonthHeight != calculateAdapterHeight(
                        requireContext(),
                        firstDayOfMonth,
                        startWeekOn,
                        true
                    )
                ) {
                    adjustMiniCalendarView(firstDayOfMonth, startWeekOn)
                }
            }

            // Handle selected day change in miniCalendarPager (mini calendar / month views)
            if (miniCalendarPager.adapter != null) {
                val monthStartingDate = calendarViewModel.initialToday.withDayOfMonth(1)
                val offset = ChronoUnit.MONTHS.between(monthStartingDate, selectedDate.withDayOfMonth(1)).toInt()
                val miniCalendarIndex = monthStartingPosition + offset
                if (miniCalendarPager.currentItem != miniCalendarIndex) {
                    // smooth-scroll only when switching between adjacent months
                    miniCalendarPager.post {
                        val currentItem = miniCalendarPager?.currentItem
                        miniCalendarPager?.setCurrentItem(
                            miniCalendarIndex,
                            if (currentItem != null) Math.abs(currentItem - miniCalendarIndex) == 1 else false
                        )
                    }
                }
            }

            // Handle selected day change in agendaPager (agenda / day views)
            if (agendaPager.adapter != null) {
                val startingDate = agendaPagerAdapter.startingDate
                val startingPosition = agendaPagerAdapter.startingPosition
                val selectedDayOffset = ChronoUnit.DAYS.between(startingDate, selectedDate).toInt()
                val agendaIndex = startingPosition + selectedDayOffset
                if (agendaPager.currentItem != agendaIndex) {
                    agendaPager.post {
                        agendaPager?.setCurrentItem(agendaIndex, false)
                    }
                }
            }
        }

        lifecycleScope.launch {
            calendarViewModel.fetchingState.collect {
                when (it) {
                    CalendarsRepository.FetchingState.NotNeeded -> {
                        calendarViewModel.setLoading(false)
                    }
                    CalendarsRepository.FetchingState.Fetching -> {
                        calendarViewModel.setLoading(true)
                    }
                    CalendarsRepository.FetchingState.Finished -> {
                        calendarViewModel.setLoading(false)
                    }
                }
            }
        }

        lifecycleScope.launchWhenStarted {

            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {

                // TODO schedule this from some global periodic scheduler
                withContext(Dispatchers.Default) {
                    handleAlarmsUseCase.execute(userId)
                }

                // TODO schedule repeating worker job
                mainViewModel.syncAlarms(userId).observe(viewLifecycleOwner) {
                    if (it is Operation.State.IN_PROGRESS) {
                        calendarViewModel.setLoading(true)
                    } else {
                        calendarViewModel.setLoading(false)
                    }
                }

                mainViewModel.fetchUserSettings(userId = userId)
            }

        }

        val headerDaysMediator = MediatorLiveData<Pair<DayOfWeek, String>>()
        // Setup header with week's days
        headerDaysMediator.addSource(calendarViewModel.timeZoneId) { zoneId ->
            timeZoneId = zoneId?.id

            setToolbarListeners(zoneId)

            weekView.customTimeZone = TimeZone.getTimeZone(zoneId)

            if (timeZoneId != null && startWeekOn != null) {
                headerDaysMediator.value = Pair(startWeekOn!!, timeZoneId!!)
            }
        }
        headerDaysMediator.addSource(calendarViewModel.weekStart) { weekStart ->

            weekStart?.let {
                setupMonthLayoutGestures(weekStart)
            }

            if (startWeekOn == null && weekStart != null) {
                if (calendarViewModel.viewMode.value == ViewMode.AGENDA) {
                    calendarViewModel.monthView.value = true
                    simulateExpandWithScroll(getWeekStartDayOfWeek(weekStart))
                } else {
                    calendarViewModel.monthView.value = false
                    simulateCollapseWithScroll(getWeekStartDayOfWeek(weekStart))
                }
            }

            startWeekOn = weekStart?.let { getWeekStartDayOfWeek(it) }

            if (timeZoneId != null && startWeekOn != null) {
                headerDaysMediator.value = Pair(startWeekOn!!, timeZoneId!!)
            }
        }
        headerDaysMediator.observe(viewLifecycleOwner) {
            it?.let { setHeaderDaysContent(it.first, it.second) }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            val layoutParams = miniCalendarDaysHeaderLayout.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.marginStart = requireContext().resources.getDimensionPixelSize(R.dimen.mini_calendar_margin) +
                    if (displayWeekNumber) {
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_width) +
                                requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_start)
                    } else 0
            miniCalendarDaysHeaderLayout.layoutParams = layoutParams

            weekView.showWeekNumber = displayWeekNumber
        }

        calendarViewModel.monthView.observe(viewLifecycleOwner) { monthView ->
            fragmentMonthLayout.allowScrolling = !monthView ||
                    calendarViewModel.viewMode.value == ViewMode.AGENDA ||
                    calendarViewModel.viewMode.value == ViewMode.MONTH
        }

        calendarViewModel.activeUserCalendars.observe(viewLifecycleOwner) { activeCalendars ->
            val hasActiveCalendars = !activeCalendars.isNullOrEmpty()
            if (hasActiveCalendars) {
                buttonCreate.imageButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm))
                buttonCreate.imageButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_button_oval)
            } else {
                buttonCreate.imageButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_disabled))
                buttonCreate.imageButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_button_disabled_oval)
            }
        }

        requireActivity().showLastSpotlightDialog()

        weekViewAdapter = WeekViewAdapter(
            dragHandler = { _, _, _ ->
                // TODO DRAG
            },
            loadMoreHandler = { yearMonthList ->
                // TODO Needed ? Or use selectedDate only ?
            },
            rangeChangedHandler = { firstVisibleDate, lastVisibleDate ->
                calendarViewModel.handleDaySelected(firstVisibleDate)
            },
            viewClickHandler = { startTime ->
                openCreateEventForm(isAllDay = false, startTime)
            },
            eventClickHandler = { weekViewEventClicked ->
                onWeekViewEventClick(weekViewEventClicked)
            }
        )
        weekView.adapter = weekViewAdapter

        val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault())
        weekView.setDateFormatter { date: LocalDate ->
            val weekdayLabel = weekdayFormatter.format(date)
            val dateLabel = dateFormatter.format(date)
            weekdayLabel + "\n" + dateLabel
        }
    }

    private var currentFromDate: LocalDate? = null
    private lateinit var weekViewAdapter: WeekViewAdapter
    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<Event>>

    private fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        // Get and display decrypted events
        eventsLiveData = calendarViewModel.getEvents(fromDate, toDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {

                    }
                    is CalendarsRepository.GetEventsResult.Success -> {
                        val weekViewCalendarEntities = it.events.map { event ->
                            event.toWeekViewCalendarEntityEvent(timeZoneId, getString(R.string.default_event_summary))
                        }
                        weekViewAdapter.submitList(
                            weekViewCalendarEntities
                        )
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {

                    }
                }
            }
        }
    }

    private fun openCreateEventForm(isAllDay: Boolean, startTime: LocalDateTime) {
        lifecycleScope.launch {
            val hasActiveCalendars = !calendarViewModel.getActiveUserCalendars().isNullOrEmpty()
            if (hasActiveCalendars) {
                val truncatedStartTime =
                    if (!isAllDay) LocalTime.of(startTime.hour, if (startTime.minute >= 30) 30 else 0)
                    else null
                requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                    .navigate(
                        Navigation.Deeplink.toEventCreate(
                            startTime.toLocalDate(),
                            truncatedStartTime
                        )
                    )
            } else {
                requireActivity().displaySnackBar(resources.getString(R.string.snack_create_event_no_active_personal_calendar))
            }
        }
    }

    private fun onWeekViewEventClick(weekViewEvent: WeekViewCalendarEntity.Event) {
        if (weekViewEvent.decrypted) {
            findNavController().navigate(
                Navigation.Deeplink.toEventDetails(
                    weekViewEvent.getActualEventId(),
                    weekViewEvent.occurrenceNumber ?: 0
                )
            )
        } else {
            val confirmationMessage =
                if (weekViewEvent.isRecurring) R.string.event_decryption_error_dialog_confirmation_recurring
                else R.string.event_decryption_error_dialog_confirmation
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.event_decryption_error_dialog_title)
                .setMessage(R.string.event_decryption_error_dialog_message)
                .setPositiveButton(confirmationMessage) { _, _ ->
                    lifecycleScope.launch { // TODO
                        val deleteResult = withContext(Dispatchers.Default) {
                            calendarViewModel.handleDeleteEvent(
                                weekViewEvent.getActualEventId(),
                                weekViewEvent.calendarId,
                                EventEditDeleteOption.ALL_EVENTS
                            )
                        }
                        if (deleteResult is UseCase.Result.Success<*>) {
                            requireActivity().displaySnackBar(getString(R.string.snack_event_deleted))
                        } else {
                            var userErrorMessage: String? = null
                            if (deleteResult is UseCase.Result.Error) {
                                logger.e("Error deleting event: ${deleteResult.message}")
                                userErrorMessage = deleteResult.userErrorMessage
                            } else if (deleteResult is UseCase.Result.InvalidParams) {
                                logger.e("InvalidParams deleting event: ${deleteResult.message}")
                                userErrorMessage = deleteResult.userErrorMessage
                            }
                            requireActivity().displaySnackBar(
                                if (userErrorMessage.isNullOrEmpty()) getString(R.string.snack_event_deleted_error)
                                else userErrorMessage
                            )
                        }
                    }
                }
                .setNegativeButton(R.string.event_decryption_error_dialog_close) { _, _ -> }
                .show()
        }
    }

    private fun setupMonthLayoutGestures(weekStart: Int) {
        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(
            miniCalendarPageChangeCallback
        )

        val startWeekOn = getWeekStartDayOfWeek(weekStart)
        setMiniCalendarPageChangeCallback(startWeekOn)

        fragment_toolbar_title_layout.setOnSingleClickListener {
            if (calendarViewModel.monthView.value == false) {
                simulateExpandWithScroll(startWeekOn)
            } else {
                simulateCollapseWithScroll(startWeekOn)
            }
        }

        // Calculate current month's desired height for both mini calendar mode (month / week)
        calendarViewModel.selectedDate.value?.withDayOfMonth(1)?.let { firstDayOfMonth ->
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        // Set custom listener for mini calendar gestures
        val onTouchListener = MonthLayoutGestureListener(
            requireContext(),
            calendarViewModel,
            viewPagerTopGuideline,
            viewPagerSliderGuideline,
            weekView,
            object : MonthLayoutGestureListener.MonthLayoutOnFinishMoveListener {
                override fun fullyExpand(animationEndListener: () -> Unit) {
                    if (calendarViewModel.monthView.value == false) {
                        // Apply the changes for expanded state
                        calendarViewModel.monthView.value = true
                        if (currentViewMode != ViewMode.MONTH) mini_calendar_chevron?.let { AndroidUtils.rotateArrowUpward(it) }
                        updateMiniCalendarHeight(
                            startWeekOn,
                            isMonthView = true,
                            animateChange = true
                        )
                        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                    } else {
                        // Animate mini calendar to go back to expanded state
                        viewPagerTopGuideline?.animateGuidelineHeightChange(
                            calendarViewModel.currentPosDesiredMonthHeight,
                            calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                        viewPagerSliderGuideline?.animateGuidelineHeightChange(
                            calendarViewModel.currentPosDesiredMonthHeight - requireContext().resources.getDimensionPixelSize(
                                R.dimen.calendar_slider_height
                            ), calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                    }
                }

                override fun fullyCollapse(animationEndListener: () -> Unit) {
                    if (calendarViewModel.monthView.value == true) {
                        // Apply the changes for collapsed state
                        calendarViewModel.monthView.value = false
                        if (currentViewMode != ViewMode.MONTH) mini_calendar_chevron?.let { AndroidUtils.rotateArrowDownward(it) }
                        updateMiniCalendarHeight(
                            startWeekOn,
                            isMonthView = false,
                            animateChange = true
                        )
                        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                    } else {
                        // Animate mini calendar to go back to collapsed state
                        viewPagerTopGuideline?.animateGuidelineHeightChange(
                            resources.getDimensionPixelSize(R.dimen.calendar_slider_height),
                            calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                        viewPagerSliderGuideline?.animateGuidelineHeightChange(
                            resources.getDimensionPixelSize(R.dimen.calendar_slider_height) - requireContext().resources.getDimensionPixelSize(
                                R.dimen.calendar_slider_height
                            ), calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                    }
                }

                override fun simulateCollapse(animationEndListener: () -> Unit) {
                    simulateCollapseWithScroll(startWeekOn)
                }

                override fun simulateExpand(animationEndListener: () -> Unit) {
                    simulateExpandWithScroll(startWeekOn)
                }

            }
        )

        fragmentMonthLayout.setOnTouchListener(onTouchListener)
        fragmentMonthLayout.agendaPager = agendaPager
        fragmentMonthLayout.sliderView = mini_calendar_slider

        miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)
    }

    private fun initAgendaPager(viewMode: ViewMode) {
        // Skip initAgendaPager if it was already done for specified viewMode
        if (viewMode == currentViewMode) return
        val previousViewMode = currentViewMode
        currentViewMode = viewMode

        if (weekView.isVisible && (viewMode == ViewMode.DAY || viewMode == ViewMode.THREE_DAY || viewMode == ViewMode.WEEK)) return

        fragmentMonthLayout?.allowScrolling = viewMode == ViewMode.AGENDA

        if (viewMode == ViewMode.MONTH) {
            displayMonthCalendarPager()
        } else if (previousViewMode == null || previousViewMode == ViewMode.MONTH) {
            displayMiniCalendarPager()
        }

        when (viewMode) {
            ViewMode.MONTH -> {
                // Hide the agenda
                agendaPager?.visibleOrGone(false)
                weekView?.visibleOrGone(false)
                miniCalendarDaysHeaderLayout?.visibleOrGone(false)
                mini_calendar_slider?.visibleOrGone(false)
                mini_calendar_chevron.clearAnimation()
                mini_calendar_chevron.visibleOrGone(false)

                // Update constraints so that month view can occupy entire space
                updateMiniCalendarConstraints(viewMode)
            }
            ViewMode.AGENDA -> {
                // Update the pager to use agenda adapter
                lifecycleScope.launch {
                    val weekStart = calendarViewModel.getWeekStart()
                    if (weekStart != null) {
                        calendarViewModel.monthView.value = true
                        simulateExpandWithScroll(getWeekStartDayOfWeek(weekStart))
                    }
                }
                agendaPager?.apply {
                    val currentItem =
                        calendarViewModel.selectedDate.value?.let { selectedDate ->
                            val startingDate = agendaPagerAdapter.startingDate
                            val startingPosition = agendaPagerAdapter.startingPosition
                            val selectedDayOffset = ChronoUnit.DAYS.between(startingDate, selectedDate).toInt()
                            startingPosition + selectedDayOffset
                        } ?: agendaPagerAdapter.startingPosition

                    adapter = agendaPagerAdapter
                    val item = if (currentItem > 0) currentItem else agendaPagerAdapter.startingPosition
                    setCurrentItem(item, false)
                    offscreenPageLimit = 1
                }
                // TODO Try and see if this is still needed
                // (agendaPager?.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = false
                // (agendaPager?.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(0) // Make sure we only keep 3 childs in cache

                // Display the agenda
                agendaPager?.visibleOrGone(true)
                weekView?.visibleOrGone(false)
                miniCalendarDaysHeaderLayout?.visibleOrGone(true)
                mini_calendar_slider?.visibleOrGone(true)
                mini_calendar_chevron.clearAnimation()
                mini_calendar_chevron.visibleOrGone(true)

                // Update constraints so that the view is split between mini calendar and agenda view
                updateMiniCalendarConstraints(viewMode)
            }
            else -> {
                lifecycleScope.launch {
                    val weekStart = calendarViewModel.getWeekStart()
                    if (weekStart != null) {
                        calendarViewModel.monthView.value = false
                        simulateCollapseWithScroll(getWeekStartDayOfWeek(weekStart))
                    }

                    val timeZoneId = calendarViewModel.getTimeZoneId()
                    if (timeZoneId != null && calendarViewModel.selectedDate.value == LocalDate.now()) {
                        weekView.scrollToDateTime(dateTime = LocalDateTime.now(timeZoneId))
                        calendarViewModel.jumpToCurrentTime.value = true // Open Day view on current time
                    }
                }

                agendaPager?.visibleOrGone(false)
                weekView?.visibleOrGone(true)
                miniCalendarDaysHeaderLayout?.visibleOrGone(true)
                mini_calendar_slider?.visibleOrGone(true)
                mini_calendar_chevron.clearAnimation()
                mini_calendar_chevron.visibleOrGone(true)

                // Update constraints so that the view is split between mini calendar and agenda / day views
                updateMiniCalendarConstraints(viewMode)
            }
        }
    }

    private fun updateMiniCalendarConstraints(viewMode: ViewMode) {
        if (viewMode == ViewMode.MONTH) {
            (miniCalendarLayout?.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarLayoutLayoutParams ->
                miniCalendarLayoutLayoutParams.height = ViewPager.LayoutParams.MATCH_PARENT
                miniCalendarLayout.layoutParams = miniCalendarLayoutLayoutParams
            }
            (miniCalendarPagerLayout?.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutLayoutParams ->
                miniCalendarPagerLayoutLayoutParams.height = ViewPager.LayoutParams.MATCH_PARENT
                miniCalendarPagerLayout.layoutParams = miniCalendarPagerLayoutLayoutParams
            }
            (miniCalendarPager?.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutParams ->
                miniCalendarPagerLayoutParams.height = ViewPager.LayoutParams.MATCH_PARENT
                miniCalendarPager.layoutParams = miniCalendarPagerLayoutParams
            }
        } else {
            (miniCalendarLayout?.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarLayoutLayoutParams ->
                miniCalendarLayoutLayoutParams.height = ViewPager.LayoutParams.WRAP_CONTENT
                miniCalendarLayout.layoutParams = miniCalendarLayoutLayoutParams
            }
            (miniCalendarPagerLayout?.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutLayoutParams ->
                miniCalendarPagerLayoutLayoutParams.height = 0
                miniCalendarPagerLayout.layoutParams = miniCalendarPagerLayoutLayoutParams
            }
            (miniCalendarPager?.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutParams ->
                miniCalendarPagerLayoutParams.height = 0
                miniCalendarPager.layoutParams = miniCalendarPagerLayoutParams
            }
        }
    }

    private fun displayMonthCalendarPager() {
        // Update the pager to use month adapter
        miniCalendarPager?.apply {
            val currentItem =
                calendarViewModel.selectedDate.value?.let { selectedDate ->
                    val startingDate = monthPagerAdapter.firstDayOfMonth
                    val startingPosition = monthPagerAdapter.startingPosition
                    val selectedDayOffset = ChronoUnit.MONTHS.between(startingDate, selectedDate.withDayOfMonth(1)).toInt()
                    startingPosition + selectedDayOffset
                } ?: monthPagerAdapter.startingPosition

            adapter = monthPagerAdapter
            offscreenPageLimit = 2

            val item = if (currentItem > 0) currentItem else monthPagerAdapter.startingPosition
            setCurrentItem(item, false)
        }
        // TODO Try and see if this is still needed
        (miniCalendarPager?.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = true
        (miniCalendarPager?.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(5)
    }

    private fun displayMiniCalendarPager() {
        // Update the pager to use mini calendar adapter
        miniCalendarPager?.apply {
            val currentItem =
                calendarViewModel.selectedDate.value?.let { selectedDate ->
                    val startingDate = miniCalendarPagerAdapter.firstDayOfMonth
                    val startingPosition = miniCalendarPagerAdapter.startingPosition
                    val selectedDayOffset = ChronoUnit.MONTHS.between(startingDate, selectedDate.withDayOfMonth(1)).toInt()
                    startingPosition + selectedDayOffset
                } ?: miniCalendarPagerAdapter.startingPosition

            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1

            val item = if (currentItem > 0) currentItem else miniCalendarPagerAdapter.startingPosition
            setCurrentItem(item, false)
        }
        // TODO Try and see if this is still needed
        (miniCalendarPager?.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = false
        (miniCalendarPager?.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(0) // Make sure we only keep 3 childs in cache
    }

    private fun setHeaderDaysContent(startWeekOn: DayOfWeek, timeZoneId: String) {

        // Mini calendar is hidden when displaying Month view
        if (currentViewMode == ViewMode.MONTH) return

        // Setup mini calendar week days header
        miniCalendarDaysHeaderLayout?.run {

            val today = LocalDate.now(ZoneId.of(timeZoneId))
            val selectedDate = calendarViewModel.selectedDate.value
            var dayToHighlight: DayOfWeek? = null
            selectedDate?.let {
                // Calculate what day to highlight, depending on mini calendar mode
                val firstDayOfTheMonth = selectedDate.withDayOfMonth(1)
                val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset =
                    if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val miniCalendarFirstDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
                val lastDayOfTheMonth = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)
                val miniCalendarLastDay = lastDayOfTheMonth.plusDays(lastDayOfMonthOffset.toLong())
                dayToHighlight =
                    if ((calendarViewModel.monthView.value == true && (selectedDate.month == today.month || (today.isAfter(
                            miniCalendarFirstDay
                        ) && today.isBefore(miniCalendarLastDay)))) ||
                        (calendarViewModel.monthView.value == false && selectedDate.weekNumber(startWeekOn) == today.weekNumber(
                            startWeekOn
                        ))
                    ) today.dayOfWeek
                    else null
            }

            this.removeAllViews()

            val weekDays = DayOfWeek.values().toList()
            Collections.rotate(
                weekDays,
                DAYS_IN_A_WEEK - (startWeekOn.value - 1)
            )
            weekDays.forEach {
                setDayHeaderItem(this, it, dayToHighlight == it)
            }
        }
    }

    private fun setDayHeaderItem(headerLayout: LinearLayout, dayOfWeek: DayOfWeek, highlight: Boolean) {
        val weekDayHeaderView = LayoutInflater.from(this.context).inflate(
            R.layout.item_mini_calendar_header,
            headerLayout,
            false
        )
        val textView = weekDayHeaderView as TextView
        textView.text = dayOfWeek.format(firstLetter = true)
        if (highlight) textView.setTextColor(requireContext().getColorFromAttr(R.attr.proton_text_accent))
        else textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_weak))
        headerLayout.addView(weekDayHeaderView)
    }

    private fun setToolbarMonthYearTitle(localDate: LocalDate, position: Int) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        if (LocalDate.now().year == localDate.year) toolbarTitle.text = "$month"
        else toolbarTitle.text = "$month $year"
    }

    private fun simulateExpandWithScroll(startWeekOn: DayOfWeek) {
        val desiredHeight = calendarViewModel.currentPosDesiredMonthHeight

        // Set mini calendar to expanded state
        calendarViewModel.monthView.value = true
        updateMiniCalendarHeight(
            startWeekOn,
            isMonthView = true,
            animateChange = true
        )
        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
        if (currentViewMode != ViewMode.MONTH) mini_calendar_chevron?.let { AndroidUtils.rotateArrowUpward(it) }

        // Animate the guidelines to desired height
        viewPagerTopGuideline?.animateGuidelineHeightChange(
            desiredHeight,
            object : AndroidUtils.AnimateGuidelineListener {
                override fun onHeightChange(animatedValue: Int) {
                    val viewPagerSliderGuidelineLayoutParams =
                        (viewPagerSliderGuideline?.layoutParams as? ConstraintLayout.LayoutParams)
                    if (viewPagerSliderGuidelineLayoutParams?.guideBegin == 0) {
                        // Update slider guide begin in case it was skipped somehow
                        viewPagerSliderGuidelineLayoutParams.guideBegin =
                            calendarViewModel.currentPosDesiredMonthHeight - requireContext().resources.getDimensionPixelSize(
                                R.dimen.calendar_slider_height
                            )
                        viewPagerSliderGuideline?.layoutParams = viewPagerSliderGuidelineLayoutParams
                    }
                }

                override fun onAnimationEnd() {
                }
            })
    }

    private fun simulateCollapseWithScroll(startWeekOn: DayOfWeek) {
        val desiredHeight = resources.getDimensionPixelSize(R.dimen.calendar_slider_height)

        if (currentViewMode != ViewMode.MONTH) mini_calendar_chevron?.let { AndroidUtils.rotateArrowDownward(it) }

        // Animate mini calendar collapse
        viewPagerTopGuideline?.animateGuidelineHeightChange(
            desiredHeight,
            object : AndroidUtils.AnimateGuidelineListener {
                override fun onHeightChange(animatedValue: Int) {
                }

                override fun onAnimationEnd() {
                    // Set mini calendar to collapsed state
                    calendarViewModel.monthView.value = false
                    updateMiniCalendarHeight(
                        startWeekOn,
                        isMonthView = false,
                        animateChange = true
                    )
                    timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                }
            })

    }
}
