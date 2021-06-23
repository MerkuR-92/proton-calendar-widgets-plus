package me.proton.android.calendar.presentation.calendar

import android.app.NotificationManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Operation
import kotlinx.android.synthetic.main.event_attendees_view.*
import kotlinx.android.synthetic.main.fragment_base.*
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.android.synthetic.main.item_form_section.view.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.view.*
import kotlinx.android.synthetic.main.toolbar_action_primary.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.animateHeightChange
import me.proton.android.calendar.common.AndroidUtils.collapse
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.expand
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.rotateArrowDownward
import me.proton.android.calendar.common.AndroidUtils.rotateArrowUpward
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatMonth
import me.proton.android.calendar.common.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.ShowNotificationUseCase
import me.proton.android.calendar.presentation.BaseFragment
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.android.calendar.presentation.account.AccountViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
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

    private var fromPosition: Int = 0

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_primary, fragment_toolbar_content, false)
        with (buttonCreate) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_plus))
        }
        buttonToday = layoutInflater.inflate(R.layout.toolbar_action_secondary, fragment_toolbar_content, false)
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
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
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
                // each item in the adapter is one day
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

        with (buttonToday) {
            val textField = (findViewById<TextView>(R.id.toolbarActionSecondaryText))
            textField.text = LocalDate.now(timeZoneId).dayOfMonth.toString()
            textField.visibility = View.VISIBLE
        }
        buttonToday.setOnSingleClickListener {
            val todayDate = LocalDate.now(timeZoneId)
            calendarViewModel.handleDaySelected(todayDate)
            calendarViewModel.jumpToCurrentTime.value = true
        }
    }

    private lateinit var miniCalendarPageChangeCallback: ViewPager2.OnPageChangeCallback

    private fun setMiniCalendarPageChangeCallback(startWeekOn: DayOfWeek) {
        miniCalendarPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                val firstDay = if (calendarViewModel.monthView.value == true) {
                    miniCalendarPagerAdapter.firstDayOfMonth.plusMonths((position - miniCalendarPagerAdapter.startingPosition).toLong())
                } else {
                    val firstDayOfTheWeekNumber = miniCalendarPagerAdapter.firstDayOfMonth.dayOfWeek.value - startWeekOn.value
                    val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

                    val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
                    val dayOfTheWeek =
                        if (fromPosition - position <= 0) 1
                        else 7
                    val firstDayOfTheWeek = miniCalendarPagerAdapter.firstDayOfMonth.with(temporalField, dayOfTheWeek.toLong())
                    firstDayOfTheWeek.plusWeeks((position - miniCalendarPagerAdapter.startingPosition).toLong())
                }
                fromPosition = position

                calendarViewModel.handleDaySelected(firstDay, fromMonthPagerCallback = true)

                setToolbarMonthYearTitle(firstDay)

                adjustMiniCalendarView(position, startWeekOn)
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
        miniCalendarPagerLayoutListener = object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {

                // TODO Null check here because this listener is triggered once even after view has been destroyed
                if (miniCalendarPager == null) return

                if (!initialHeightAdjusted) {
                    initialHeightAdjusted = true
                    if (calendarViewModel.monthView.value == true) mini_calendar_chevron.rotation = 180f
                    updateMiniCalendarHeight(this, startWeekOn, true, false)
                }

                fragment_toolbar_title_layout.setOnSingleClickListener {
                    if (calendarViewModel.monthView.value == false) {
                        calendarViewModel.monthView.value = true
                        rotateArrowDownward(mini_calendar_chevron)
                        updateMiniCalendarHeight(this, startWeekOn, true, true)
                    } else {
                        calendarViewModel.monthView.value = false
                        rotateArrowUpward(mini_calendar_chevron)
                        updateMiniCalendarHeight(this, startWeekOn, false, true)
                    }
                }
            }
        }
    }

    private fun updateMiniCalendarHeight(miniCalendarPagerLayoutListener: ViewTreeObserver.OnGlobalLayoutListener, startWeekOn: DayOfWeek, isMonthView: Boolean, animateChange: Boolean) {

        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(miniCalendarPageChangeCallback)
        if (isMonthView) {
            val selectedDate = calendarViewModel.selectedDate.value ?: return
            val position =
                if (selectedDate.year != miniCalendarPagerAdapter.firstDayOfMonth.year) {
                    miniCalendarPagerAdapter.startingPosition + Period.between(miniCalendarPagerAdapter.firstDayOfMonth, selectedDate).months
                } else {
                    miniCalendarPagerAdapter.startingPosition +
                            (calendarViewModel.selectedDate.value!!.monthValue - miniCalendarPagerAdapter.firstDayOfMonth.monthValue)
                }
            fromPosition = position
            miniCalendarPager.setCurrentItem(position, false)
        } else {
            val selectedDate = calendarViewModel.selectedDate.value ?: return
            val firstDayOfTheWeekNumber = selectedDate.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
            val firstDayOfTheWeek = selectedDate.with(temporalField, 1)
            val position =
                if (firstDayOfTheWeek.year < miniCalendarPagerAdapter.firstDayOfMonth.year) {
                    miniCalendarPagerAdapter.startingPosition + (selectedDate.weekNumber(startWeekOn) - (firstDayOfTheWeek.withDayOfYear(firstDayOfTheWeek.lengthOfYear()).weekNumber(startWeekOn) + miniCalendarPagerAdapter.firstDayOfMonth.withDayOfMonth(1).weekNumber(startWeekOn)))
                } else if (firstDayOfTheWeek.year > miniCalendarPagerAdapter.firstDayOfMonth.year) {
                    miniCalendarPagerAdapter.startingPosition + (selectedDate.weekNumber(startWeekOn) + (miniCalendarPagerAdapter.firstDayOfMonth.withDayOfYear(firstDayOfTheWeek.lengthOfYear()).weekNumber(startWeekOn) - miniCalendarPagerAdapter.firstDayOfMonth.withDayOfMonth(1).weekNumber(startWeekOn)))
                } else {
                    miniCalendarPagerAdapter.startingPosition + (selectedDate.weekNumber(startWeekOn) - miniCalendarPagerAdapter.firstDayOfMonth.withDayOfMonth(1).weekNumber(startWeekOn))
                }
            fromPosition = position
            miniCalendarPager.setCurrentItem(position, false)
        }
        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)

        val firstDayOfMonth = calendarViewModel.selectedDate.value!!.withDayOfMonth(1)
        val desiredHeight = MiniCalendarItemAdapter.calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )

        if (miniCalendarPager.height != desiredHeight && miniCalendarPager.isVisible) {
            miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)

            if (animateChange) {
                miniCalendarPager.animateHeightChange(desiredHeight) {
                    miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
                }
            } else {
                val layoutParams = miniCalendarPager.layoutParams.apply {
                    height = desiredHeight
                }
                miniCalendarPager.layoutParams = layoutParams
                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
            }
        }
    }

    interface OnFlingMiniCalendarListener {
        fun expandOnFling()
        fun collapseOnFling()
    }

    private val agendaPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val currentDate = calendarViewModel.initialToday.plusDays((agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
            calendarViewModel.handleDaySelected(currentDate)
        }
    }

    override fun onResume() {
        super.onResume()

        // make sure currently selected month always has desired height, even if adjacent pages make
        //  entire ViewPager to have different height
        if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
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

        if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
    }

    private fun adjustMiniCalendarView(position: Int, startWeekOn: DayOfWeek) {

        if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)

        // ViewPager will adjust its height to the largest item it contains and display empty space for
        //  smaller items, like months with fewer week lines. That's why we need to resize it every time we
        //  display a month
        val firstDayOfMonth = miniCalendarPagerAdapter.firstDayOfMonth.plusMonths((position - miniCalendarPagerAdapter.startingPosition).toLong())
        val desiredHeight = MiniCalendarItemAdapter.calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            calendarViewModel.monthView.value ?: true
        )
        miniCalendarPager.animateHeightChange(desiredHeight) {
            if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(miniCalendarPageChangeCallback)
        agendaPager.unregisterOnPageChangeCallback(agendaPageChangeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        miniCalendarPagerAdapter = MiniCalendarPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))

        fromPosition = miniCalendarPagerAdapter.startingPosition

        miniCalendarPager.apply{
            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(miniCalendarPagerAdapter.startingPosition, false)
        }

        agendaPagerAdapter = AgendaPagerAdapter(requireActivity(), calendarViewModel.initialToday) {
            if (calendarViewModel.monthView.value == true) {
                calendarViewModel.monthView.value = false
                val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return@AgendaPagerAdapter)
                rotateArrowUpward(mini_calendar_chevron)
                updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
            }
        }
        dayPagerAdapter = DayPagerAdapter(requireActivity(), calendarViewModel.initialToday) {
            if (calendarViewModel.monthView.value == true) {
                calendarViewModel.monthView.value = false
                val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return@DayPagerAdapter)
                rotateArrowUpward(mini_calendar_chevron)
                updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
            }
        }

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
                calendarViewModel.jumpToCurrentTime.value = true
                agendaPager.apply {
                    val currentItem = this.currentItem // Save currently selected item position
                    adapter = dayPagerAdapter
                    setCurrentItem(if (currentItem > 0) currentItem else dayPagerAdapter.startingPosition, false)
                    offscreenPageLimit = 1
                }
                calendarViewModel.setCalendarPagers(miniCalendarPager, agendaPager)
            }
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

        calendarViewModel.selectedDate.observe(viewLifecycleOwner) {
            setToolbarMonthYearTitle(it)
        }

        lifecycleScope.launch {
            calendarViewModel.fetchingState.collect {
                when (it) {
                    CalendarsRepository.FetchingState.NotNeeded -> setProgressBarVisibility(false)
                    CalendarsRepository.FetchingState.Fetching -> setProgressBarVisibility(true)
                    CalendarsRepository.FetchingState.Finished -> setProgressBarVisibility(false)
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
                        setProgressBarVisibility(true)
                    } else {
                        setProgressBarVisibility(false)
                    }
                }
            }

            val notificationManager: NotificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while(true) {

                val activeNotifications = notificationManager.activeNotifications
                val isBackgroundSyncRunning = activeNotifications.any { it.id == ShowNotificationUseCase.NOTIFICATION_ID_SYNC_SERVICE }

                if (userId != null && !isBackgroundSyncRunning) {
                    mainViewModel.syncServerEvents(userId).observe(viewLifecycleOwner) {
                        if (it is Operation.State.IN_PROGRESS) {
                            setProgressBarVisibility(true)
                        } else {
                            setProgressBarVisibility(false)
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
            if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
            if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(miniCalendarPageChangeCallback)

            val startWeekOn = getWeekStartDayOfWeek(weekStart)
            setMiniCalendarPagerLayoutListener(startWeekOn)
            setMiniCalendarPageChangeCallback(startWeekOn)

            miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)
            miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }

        calendarViewModel.activeUserCalendars.observe(viewLifecycleOwner) { activeCalendars ->
            val hasActiveCalendars = !activeCalendars.isNullOrEmpty()
            if (hasActiveCalendars) {
                buttonCreate.imageButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_inverted))
                buttonCreate.imageButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_primary_oval)
            } else {
                buttonCreate.imageButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_disabled))
                buttonCreate.imageButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_primary_disabled_oval)
            }
        }
    }

    private fun setToolbarMonthYearTitle(localDate: LocalDate) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        toolbarTitle.text = "$month $year"
        mini_calendar_chevron.visibleOrGone(true)
    }
}
