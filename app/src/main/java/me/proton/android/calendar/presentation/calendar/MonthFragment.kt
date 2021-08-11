package me.proton.android.calendar.presentation.calendar

import android.app.NotificationManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.view.*
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
import kotlinx.android.synthetic.main.toolbar_action_primary.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.animateGuidelineHeightChange
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatMonth
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.presentation.BaseFragment
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.ItemMiniCalendarFragment.Companion.calculateAdapterHeight
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields
import java.util.*

class MonthFragment : BaseFragment() {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val accountViewModel: AccountViewModel by sharedViewModel()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    private lateinit var miniCalendarPagerAdapter: MiniCalendarPagerAdapter
    private lateinit var agendaPagerAdapter: AgendaPagerAdapter
    private lateinit var dayPagerAdapter: DayPagerAdapter

    private val navigationArguments: MonthFragmentArgs by navArgs()

    private lateinit var toolbarTitle: TextView

    private val mainViewModel: MainViewModel by viewModel()

    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_month

    private lateinit var buttonCreate: View
    private lateinit var buttonToday: View

    private val headerDaysMediator = MediatorLiveData<Pair<DayOfWeek, String>>()
    private var startWeekOn: DayOfWeek? = null
    private var timeZoneId: String? = null

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_primary, fragment_toolbar_content, false)
        with(buttonCreate) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(
                ContextCompat.getDrawable(
                    this.context,
                    R.drawable.ic_plus
                )
            )
        }
        buttonToday = layoutInflater.inflate(R.layout.toolbar_action_secondary, fragment_toolbar_content, false)
        with(buttonToday) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(
                ContextCompat.getDrawable(
                    this.context,
                    R.drawable.ic_today
                )
            )
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
            val hasActiveCalendars = !calendarViewModel.activeUserCalendars.value.isNullOrEmpty()
            if (hasActiveCalendars) {
                // Each item in the adapter is one day
                val currentDate =
                    calendarViewModel.initialToday.plusDays((agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
                requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                    .navigate(
                        Navigation.Deeplink.toEventCreate(
                            currentDate,
                            ICalUtilsImpl.generateEventStartTime(timeZoneId)
                        )
                    )
            } else {
                requireActivity().displaySnackBar(resources.getString(R.string.snack_create_event_no_active_personal_calendar))
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

    private lateinit var miniCalendarPageChangeCallback: ViewPager2.OnPageChangeCallback

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

    /**
     * Forces Mini Calendar Pager to have desired size for currently selected month,
     * independent from other months present in Pager.
     *
     * It is used only to make sure, the current month opened when you start the app
     * has correct height. Upon pager scrolling we call another function to animate change.
     */
    private lateinit var miniCalendarPagerLayoutListener: ViewTreeObserver.OnGlobalLayoutListener

    private var initialHeightAdjusted = false
    private fun setMiniCalendarPagerLayoutListener(startWeekOn: DayOfWeek) {
        miniCalendarPagerLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {

                // TODO Null check here because this listener is triggered once even after view has been destroyed
                if (miniCalendarPager == null) return

                // Adjust mini calendar height when month fragment is created
                if (!initialHeightAdjusted) {
                    initialHeightAdjusted = true
                    if (calendarViewModel.monthView.value == true) mini_calendar_chevron.rotation = 180f
                    updateMiniCalendarHeight(this, startWeekOn, isMonthView = false, animateChange = false)
                }
            }
        }
    }

    private fun updateMiniCalendarHeight(
        miniCalendarPagerLayoutListener: ViewTreeObserver.OnGlobalLayoutListener,
        startWeekOn: DayOfWeek,
        isMonthView: Boolean,
        animateChange: Boolean
    ) {

        val firstDayOfMonth = calendarViewModel.selectedDate.value!!.withDayOfMonth(1)

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

        miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)

        if (animateChange) {
            viewPagerTopGuideline.animateGuidelineHeightChange(
                desiredHeight,
                calendarViewModel.currentPosDesiredMonthHeight
            ) {
                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
            }
            viewPagerSliderGuideline.animateGuidelineHeightChange(
                desiredHeight - requireContext().resources.getDimensionPixelSize(
                    R.dimen.calendar_slider_height
                ), calendarViewModel.currentPosDesiredMonthHeight
            ) { }
        } else {
            val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams).apply {
                guideBegin = desiredHeight
            }
            viewPagerTopGuideline.layoutParams = layoutParams

            val sliderLayoutParams = (viewPagerSliderGuideline.layoutParams as ConstraintLayout.LayoutParams).apply {
                guideBegin =
                    desiredHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height)
            }
            viewPagerSliderGuideline.layoutParams = sliderLayoutParams

            miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }
    }

    private val agendaPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val currentDate =
                calendarViewModel.initialToday.plusDays((agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
            calendarViewModel.handleDaySelected(currentDate)
        }
    }

    override fun onResume() {
        super.onResume()

        // Make sure currently selected month always has desired height, even if adjacent pages make
        // entire ViewPager to have different height
        if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(
            miniCalendarPagerLayoutListener
        )
    }

    override fun onStart() {
        super.onStart()

        calendarViewModel.showAutoDetectPrimaryTimezone = true
        calendarViewModel.initialAutoDetectPrimaryTimezoneValue = null
        calendarViewModel.autoDetectPrimaryTimezone.observe(viewLifecycleOwner) { autoDetectPrimaryTimezone ->
            autoDetectPrimaryTimezone ?: return@observe
            calendarViewModel.handleAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone, requireContext())
        }
    }

    override fun onPause() {
        super.onPause()

        if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(
            miniCalendarPagerLayoutListener
        )
    }

    private fun adjustMiniCalendarView(firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek) {

        if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(
            miniCalendarPagerLayoutListener
        )

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

        viewPagerTopGuideline.animateGuidelineHeightChange(desiredHeight, null) {
            if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(
                miniCalendarPagerLayoutListener
            )
        }
        viewPagerSliderGuideline.animateGuidelineHeightChange(
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

        val monthStartingPosition = miniCalendarPagerAdapter.startingPosition

        miniCalendarPager.apply {
            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(monthStartingPosition, false)
        }

        agendaPagerAdapter = AgendaPagerAdapter(requireActivity(), calendarViewModel.initialToday)
        dayPagerAdapter = DayPagerAdapter(requireActivity(), calendarViewModel.initialToday)

        calendarViewModel.loading.observe(viewLifecycleOwner) { loading ->
            fragment_progress_bar?.visibleOrInvisible(loading)
        }

        // Switch between Agenda and Day views
        calendarViewModel.viewMode.observe(viewLifecycleOwner) { viewMode ->
            if (viewMode == ViewMode.AGENDA) {
                agendaPager.apply {
                    val currentItem = this.currentItem // Save currently selected item position
                    adapter = agendaPagerAdapter
                    setCurrentItem(if (currentItem > 0) currentItem else agendaPagerAdapter.startingPosition, false)
                    offscreenPageLimit = 1
                }
                calendarViewModel.setCalendarPagers(miniCalendarPager, agendaPager)
            } else {
                calendarViewModel.jumpToCurrentTime.value = true // Open Day view on current time
                agendaPager.apply {
                    val currentItem = this.currentItem // Save currently selected item position
                    adapter = dayPagerAdapter
                    setCurrentItem(if (currentItem > 0) currentItem else dayPagerAdapter.startingPosition, false)
                    offscreenPageLimit = 1
                }
                calendarViewModel.setCalendarPagers(miniCalendarPager, agendaPager)
            }
            (agendaPager.getChildAt(0) as RecyclerView).layoutManager?.isItemPrefetchEnabled = false
            (agendaPager.getChildAt(0) as RecyclerView).setItemViewCacheSize(0) // Make sure we only keep 3 childs in cache
        }

        agendaPager.registerOnPageChangeCallback(agendaPageChangeCallback)

        // Init view pagers in VM
        calendarViewModel.setCalendarPagers(miniCalendarPager, agendaPager)

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

            val firstDayOfMonth = selectedDate.withDayOfMonth(1)
            val weekStart = calendarViewModel.weekStart.value ?: return@observe
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

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            setToolbarListeners(zoneId)
        }

        calendarViewModel.weekStart.observe(viewLifecycleOwner) { weekStart ->
            if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(
                miniCalendarPagerLayoutListener
            )
            if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(
                miniCalendarPageChangeCallback
            )

            val startWeekOn = getWeekStartDayOfWeek(weekStart)
            setMiniCalendarPagerLayoutListener(startWeekOn)
            setMiniCalendarPageChangeCallback(startWeekOn)

            fragment_toolbar_title_layout.setOnSingleClickListener {
                if (calendarViewModel.monthView.value == false) {
                    simulateExpandWithScroll(startWeekOn)
                } else {
                    simulateCollapseWithScroll(startWeekOn)
                }
            }

            // Calculate current month's desired height for both mini calendar mode (month / week)
            val firstDayOfMonth = calendarViewModel.selectedDate.value!!.withDayOfMonth(1)
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )

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
                            AndroidUtils.rotateArrowUpward(mini_calendar_chevron)
                            updateMiniCalendarHeight(
                                miniCalendarPagerLayoutListener, startWeekOn,
                                isMonthView = true,
                                animateChange = true
                            )
                            timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                        } else {
                            // Animate mini calendar to go back to expanded state
                            viewPagerTopGuideline.animateGuidelineHeightChange(
                                calendarViewModel.currentPosDesiredMonthHeight,
                                calendarViewModel.currentPosDesiredMonthHeight
                            ) {
                                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(
                                    miniCalendarPagerLayoutListener
                                )
                            }
                            viewPagerSliderGuideline.animateGuidelineHeightChange(
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
                            AndroidUtils.rotateArrowDownward(mini_calendar_chevron)
                            updateMiniCalendarHeight(
                                miniCalendarPagerLayoutListener, startWeekOn,
                                isMonthView = false,
                                animateChange = true
                            )
                            timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                        } else {
                            // Animate mini calendar to go back to collapsed state
                            viewPagerTopGuideline.animateGuidelineHeightChange(
                                resources.getDimensionPixelSize(R.dimen.calendar_slider_height),
                                calendarViewModel.currentPosDesiredMonthHeight
                            ) {
                                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(
                                    miniCalendarPagerLayoutListener
                                )
                            }
                            viewPagerSliderGuideline.animateGuidelineHeightChange(
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
            miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }

        // Setup header with week's days
        headerDaysMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && startWeekOn != null) {
                headerDaysMediator.value = Pair(startWeekOn!!, timeZoneId!!)
            }
        }
        headerDaysMediator.addSource(calendarViewModel.weekStart) { value ->
            startWeekOn = value?.let { getWeekStartDayOfWeek(it) }

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
            fragmentMonthLayout.allowScrolling = !monthView
        }

        calendarViewModel.activeUserCalendars.observe(viewLifecycleOwner) { activeCalendars ->
            val hasActiveCalendars = !activeCalendars.isNullOrEmpty()
            if (hasActiveCalendars) {
                buttonCreate.imageButton.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_inverted))
                buttonCreate.imageButton.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_primary_oval)
            } else {
                buttonCreate.imageButton.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_disabled))
                buttonCreate.imageButton.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_primary_disabled_oval)
            }
        }
    }

    private fun setHeaderDaysContent(startWeekOn: DayOfWeek, timeZoneId: String) {
        // Setup week days header
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
        if (highlight) textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_norm))
        else textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
        headerLayout.addView(weekDayHeaderView)
    }

    private fun setToolbarMonthYearTitle(localDate: LocalDate, position: Int) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        if (LocalDate.now().year == localDate.year) toolbarTitle.text = "$month"
        else toolbarTitle.text = "$month $year"
        mini_calendar_chevron.visibleOrGone(true)
    }

    private fun simulateExpandWithScroll(startWeekOn: DayOfWeek) {
        calendarViewModel.lifeCycleScope.launch {
            val desiredHeight = calendarViewModel.currentPosDesiredMonthHeight

            // Set mini calendar to expanded state
            calendarViewModel.monthView.value = true
            updateMiniCalendarHeight(
                miniCalendarPagerLayoutListener, startWeekOn,
                isMonthView = true,
                animateChange = true
            )
            timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
            AndroidUtils.rotateArrowUpward(mini_calendar_chevron)

            // Animate the guidelines to desired height
            viewPagerTopGuideline.animateGuidelineHeightChange(
                desiredHeight,
                object : AndroidUtils.AnimateGuidelineListener {
                    override fun onHeightChange(animatedValue: Int) {
                        val viewPagerSliderGuidelineLayoutParams =
                            (viewPagerSliderGuideline.layoutParams as ConstraintLayout.LayoutParams)
                        if (viewPagerSliderGuidelineLayoutParams.guideBegin == 0) {
                            // Update slider guide begin in case it was skipped somehow
                            viewPagerSliderGuidelineLayoutParams.guideBegin =
                                calendarViewModel.currentPosDesiredMonthHeight - requireContext().resources.getDimensionPixelSize(
                                    R.dimen.calendar_slider_height
                                )
                            viewPagerSliderGuideline.layoutParams = viewPagerSliderGuidelineLayoutParams
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

            AndroidUtils.rotateArrowDownward(mini_calendar_chevron)

            // Animate mini calendar collapse
            viewPagerTopGuideline.animateGuidelineHeightChange(
                desiredHeight,
                object : AndroidUtils.AnimateGuidelineListener {
                    override fun onHeightChange(animatedValue: Int) {
                    }

                    override fun onAnimationEnd() {
                        // Set mini calendar to collapsed state
                        calendarViewModel.monthView.value = false
                        updateMiniCalendarHeight(
                            miniCalendarPagerLayoutListener, startWeekOn,
                            isMonthView = false,
                            animateChange = true
                        )
                        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                    }
                })

        }
    }
}
