package me.proton.android.calendar.presentation.calendar.fragment

import android.app.NotificationManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.view.*
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Operation
import kotlinx.android.synthetic.main.chip_group_day_of_week.*
import kotlinx.android.synthetic.main.event_attendees_view.*
import kotlinx.android.synthetic.main.fragment_base.*
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.android.synthetic.main.item_form_section.view.*
import kotlinx.android.synthetic.main.item_mini_calendar.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_header.view.*
import kotlinx.android.synthetic.main.toolbar_action_button.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.utils.AndroidUtils.animateGuidelineHeightChange
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatMonth
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.presentation.main.fragment.BaseFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.customView.MonthLayoutGestureListener
import me.proton.android.calendar.presentation.calendar.fragment.ItemMiniCalendarFragment.Companion.calculateAdapterHeight
import me.proton.android.calendar.presentation.calendar.pagerAdapter.AgendaPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.DayPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.MiniCalendarPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.MonthPagerAdapter
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.*

class MonthFragment : BaseFragment() {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val accountViewModel: AccountViewModel by sharedViewModel()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    private lateinit var miniCalendarPagerAdapter: MiniCalendarPagerAdapter
    private lateinit var agendaPagerAdapter: AgendaPagerAdapter
    private lateinit var dayPagerAdapter: DayPagerAdapter
    private lateinit var monthPagerAdapter: MonthPagerAdapter

    private val navigationArguments: MonthFragmentArgs by navArgs()

    private lateinit var toolbarTitle: TextView

    private val mainViewModel: MainViewModel by viewModel()

    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_month

    private lateinit var buttonCreate: View
    private lateinit var buttonToday: View

    private lateinit var miniCalendarPageChangeCallback: ViewPager2.OnPageChangeCallback

    private val headerDaysMediator = MediatorLiveData<Pair<DayOfWeek, String>>()
    private var startWeekOn: DayOfWeek? = null
    private var timeZoneId: String? = null

    private var currentViewMode: ViewMode? = null

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_button, fragment_toolbar_content, false)
        with (buttonCreate) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_plus))
        }
        buttonToday = layoutInflater.inflate(R.layout.toolbar_action_button, fragment_toolbar_content, false)
        with (buttonToday) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_today))
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
                val hasActiveCalendars = !calendarViewModel.getActiveUserCalendars().isNullOrEmpty()
                if (hasActiveCalendars) {
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
            // Agenda / Day view pager is hidden when displaying Month view
            if (currentViewMode != ViewMode.MONTH) {
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
        dayPagerAdapter = DayPagerAdapter(requireActivity(), calendarViewModel.initialToday)

        calendarViewModel.viewMode.value?.let { viewMode ->
            fragmentMonthLayout.viewMode = viewMode
            initAgendaPager(viewMode)
        }

        calendarViewModel.loading.observe(viewLifecycleOwner) { loading ->
            fragment_progress_bar?.visibleOrInvisible(loading)
        }

        // Switch between Agenda and Day views
        calendarViewModel.viewMode.observe(viewLifecycleOwner) { viewMode ->
            fragmentMonthLayout.viewMode = viewMode
            initAgendaPager(viewMode)
        }

        agendaPager.registerOnPageChangeCallback(agendaPageChangeCallback)

        // Init view pagers in VM
        calendarViewModel.setCalendarPagers(
            miniCalendarPager,
            agendaPager,
            calendarViewModel.viewMode.value?.let {
                if (it != ViewMode.MONTH) it else null
            }
        )

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
            }

            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (true) {

                val activeNotifications = notificationManager.activeNotifications
                val isBackgroundSyncRunning =
                    activeNotifications.any { it.id == ShowNotificationUseCase.NOTIFICATION_ID_SYNC_SERVICE }

                if (userId != null && !isBackgroundSyncRunning) {
                    mainViewModel.syncServerEvents(userId).observe(viewLifecycleOwner) {
                        if (it is Operation.State.IN_PROGRESS) {
                            calendarViewModel.setLoading(true)
                        } else {
                            calendarViewModel.setLoading(false)
                        }
                    }
                }

                delay(SYNC_EVENTS_IN_APP_REFRESH_PERIOD.toMillis())
            }
        }

        if (headerDaysMediator.hasObservers()) {
            headerDaysMediator.removeObservers(viewLifecycleOwner)
        }
        // Setup header with week's days
        headerDaysMediator.addSource(calendarViewModel.timeZoneId) { zoneId ->
            timeZoneId = zoneId?.id

            setToolbarListeners(zoneId)

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
            agendaPager,
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
        currentViewMode = viewMode

        fragmentMonthLayout?.allowScrolling = viewMode == ViewMode.AGENDA
        lifecycleScope.launch {

            if (viewMode == ViewMode.MONTH) {
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
            } else {
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

            val immutableWeekStart = calendarViewModel.getWeekStart()
            if (viewMode == ViewMode.AGENDA) {
                // Update the pager to use agenda adapter
                if (immutableWeekStart != null) {
                    calendarViewModel.monthView.value = true
                    simulateExpandWithScroll(getWeekStartDayOfWeek(immutableWeekStart))
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
            } else {
                // Update the pager to use day adapter
                if (immutableWeekStart != null) {
                    calendarViewModel.monthView.value = false
                    simulateCollapseWithScroll(getWeekStartDayOfWeek(immutableWeekStart))
                }
                if (calendarViewModel.selectedDate.value == LocalDate.now()) {
                    calendarViewModel.jumpToCurrentTime.value = true // Open Day view on current time
                }
                agendaPager?.apply {
                    val currentItem =
                        calendarViewModel.selectedDate.value?.let { selectedDate ->
                            val startingDate = dayPagerAdapter.startingDate
                            val startingPosition = dayPagerAdapter.startingPosition
                            val selectedDayOffset = ChronoUnit.DAYS.between(startingDate, selectedDate).toInt()
                            startingPosition + selectedDayOffset
                        } ?: dayPagerAdapter.startingPosition

                    adapter = dayPagerAdapter
                    val item = if (currentItem > 0) currentItem else dayPagerAdapter.startingPosition
                    setCurrentItem(item, false)
                    offscreenPageLimit = 1
                }
                // TODO Try and see if this is still needed
                // (agendaPager?.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = false
                // (agendaPager?.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(0) // Make sure we only keep 3 childs in cache
            }
            if (miniCalendarPager != null && agendaPager != null) {
                // Set the calendar pagers in VM
                calendarViewModel.setCalendarPagers(miniCalendarPager, agendaPager, if (viewMode != ViewMode.MONTH) viewMode else null)
            }
        }

        if (viewMode == ViewMode.MONTH) {
            // Hide the agenda
            agendaPager?.visibleOrGone(false)
            miniCalendarDaysHeaderLayout?.visibleOrGone(false)
            mini_calendar_slider?.visibleOrGone(false)
            mini_calendar_chevron.clearAnimation()
            mini_calendar_chevron.visibleOrGone(false)

            // Update constraints so that month view can occupy entire space
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
            // Display the agenda
            agendaPager?.visibleOrGone(true)
            miniCalendarDaysHeaderLayout?.visibleOrGone(true)
            mini_calendar_slider?.visibleOrGone(true)
            mini_calendar_chevron.clearAnimation()
            mini_calendar_chevron.visibleOrGone(true)

            // Update constraints so that the view is split between mini calendar and agenda / day views
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
        textView.text = dayOfWeek.format(firstLetter = true).uppercase()
        if (highlight) textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_norm))
        else textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
        headerLayout.addView(weekDayHeaderView)
    }

    private fun setToolbarMonthYearTitle(localDate: LocalDate, position: Int) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        if (LocalDate.now().year == localDate.year) toolbarTitle.text = "$month"
        else toolbarTitle.text = "$month $year"
    }

    private fun simulateExpandWithScroll(startWeekOn: DayOfWeek) {
        calendarViewModel.lifeCycleScope.launch {
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
    }

    private fun simulateCollapseWithScroll(startWeekOn: DayOfWeek) {
        calendarViewModel.lifeCycleScope.launch {
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
}
