package me.proton.android.calendar.presentation.calendar

import android.app.NotificationManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
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
import me.proton.android.calendar.common.AndroidUtils.animateHeightChange
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.AndroidUtils.rotateArrowDownward
import me.proton.android.calendar.common.AndroidUtils.rotateArrowUpward
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.calculateWeekNumberBetween
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
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.*
import kotlin.math.sqrt

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

    private val headerDaysMediator = MediatorLiveData<Pair<DayOfWeek, String>>()
    private var startWeekOn: DayOfWeek? = null
    private var timeZoneId: String? = null

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
                    val monthStartingPosition = calendarViewModel.monthViewStartingPositionAndDate.value?.first ?: miniCalendarPagerAdapter.startingPosition
                    val monthStartingDate = calendarViewModel.monthViewStartingPositionAndDate.value?.second ?: miniCalendarPagerAdapter.firstDayOfMonth
                    monthStartingDate.plusMonths((position - monthStartingPosition).toLong())
                } else {
//                    val firstDayOfTheWeekNumber = miniCalendarPagerAdapter.firstDayOfMonth.dayOfWeek.value - startWeekOn.value
//                    val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
//
//                    val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
//                    val dayOfTheWeek =
//                        if (fromPosition - position <= 0) 1
//                        else 7
//                    val firstDayOfTheWeek = miniCalendarPagerAdapter.firstDayOfMonth.with(temporalField, dayOfTheWeek.toLong())
//
//                    firstDayOfTheWeek.plusWeeks((position - miniCalendarPagerAdapter.startingPosition).toLong())
                    val weekStartingPosition = calendarViewModel.weekViewStartingPositionAndDate.value?.first ?: return
                    val weekStartingDate = calendarViewModel.weekViewStartingPositionAndDate.value?.second ?: return

                    if (fromPosition - position > 0) weekStartingDate.plusDays(6).plusWeeks((position - weekStartingPosition).toLong())
                    else weekStartingDate.plusWeeks((position - weekStartingPosition).toLong())
                }
                fromPosition = position

                calendarViewModel.handleDaySelected(firstDay, fromMonthPagerCallback = true)


                setToolbarMonthYearTitle(firstDay, miniCalendarPager.currentItem)

                adjustMiniCalendarView(position, startWeekOn)

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

//        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(miniCalendarPageChangeCallback)
//        if (isMonthView) {
//            val selectedDate = calendarViewModel.selectedDate.value ?: return
//            val position = miniCalendarPagerAdapter.startingPosition + ChronoUnit.MONTHS.between(miniCalendarPagerAdapter.firstDayOfMonth, selectedDate).toInt()
//            fromPosition = position
//            miniCalendarPager.setCurrentItem(position, false)
//        } else {
//            val selectedDate = calendarViewModel.selectedDate.value ?: return
//            val weekStartingPosition = calendarViewModel.weekViewStartingPositionAndDate.value?.first ?: return
//            val weekStartingDate = calendarViewModel.weekViewStartingPositionAndDate.value?.second ?: return
//            val position = weekStartingPosition + calculateWeekNumberBetween(weekStartingDate, selectedDate, startWeekOn)
//            fromPosition = position
//            miniCalendarPager.setCurrentItem(position, false)
//        }
//        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)

        val firstDayOfMonth = calendarViewModel.selectedDate.value!!.withDayOfMonth(1)
        val desiredHeight = calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )
        if (isMonthView) {
            currentPosDesiredMonthHeight = desiredHeight
            currentPosDesiredWeekHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                false
            )
        } else {
            currentPosDesiredWeekHeight = desiredHeight
            currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        if (viewPagerTopGuideline.height != desiredHeight) {
            miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)

            if (animateChange) {
                viewPagerTopGuideline.animateHeightChange(desiredHeight, currentPosDesiredMonthHeight) {
                    miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
                }
                viewPagerSliderGuideline.animateHeightChange(desiredHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height), currentPosDesiredMonthHeight) { }
            } else {
                val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams).apply {
                    guideBegin = desiredHeight
                }
                viewPagerTopGuideline.layoutParams = layoutParams

                val sliderLayoutParams = (viewPagerSliderGuideline.layoutParams as ConstraintLayout.LayoutParams).apply {
                    guideBegin = desiredHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height)
                }
                viewPagerSliderGuideline.layoutParams = sliderLayoutParams

                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
            }
        }
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

        val monthStartingPosition = calendarViewModel.monthViewStartingPositionAndDate.value?.first ?: miniCalendarPagerAdapter.startingPosition
        val monthStartingDate = calendarViewModel.monthViewStartingPositionAndDate.value?.second ?: miniCalendarPagerAdapter.firstDayOfMonth
        val firstDayOfMonth = monthStartingDate.plusMonths((position - monthStartingPosition).toLong())
        val isMonthView = calendarViewModel.monthView.value ?: true
        val desiredHeight = calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )
        if (isMonthView) {
            currentPosDesiredMonthHeight = desiredHeight
            currentPosDesiredWeekHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                false
            )
        } else {
            currentPosDesiredWeekHeight = desiredHeight
            currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }
        TimberLogger.e("Test test adjustMiniCalendarView update guideBegin $desiredHeight")
        viewPagerTopGuideline.animateHeightChange(desiredHeight) {
            if (this::miniCalendarPagerLayoutListener.isInitialized) miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }
        viewPagerSliderGuideline.animateHeightChange(desiredHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height)) { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::miniCalendarPageChangeCallback.isInitialized) miniCalendarPager.unregisterOnPageChangeCallback(miniCalendarPageChangeCallback)
        agendaPager.unregisterOnPageChangeCallback(agendaPageChangeCallback)
    }

    interface CalendarOnScrollListener {
        fun onScroll(dy: Int)
        fun onScrollChange(scrollY: Int, oldScrollY: Int)
    }

    interface MonthLayoutOnFinishMoveListener {
        fun fullyExpand(animationEndListener: (() -> Unit))
        fun fullyCollapse(animationEndListener: (() -> Unit))
    }

    class MonthLayoutGestureListener(
        private val context: Context,
        private val calendarViewModel: CalendarViewModel,
        private val startWeekOn: DayOfWeek,
        private val viewPagerTopGuideline: View,
        private val viewPagerSliderGuideline: View,
        private val miniCalendarPager: ViewPager2,
        private val miniCalendarPagerAdapter: MiniCalendarPagerAdapter,
        private val agendaPager: ViewPager2,
        private val currentPosDesiredWeekHeight: Int,
        private val currentPosDesiredMonthHeight: Int,
        private val monthLayoutOnFinishMoveListener: MonthLayoutOnFinishMoveListener
    ): View.OnTouchListener {
        private var oldScrollY: Float? = null
        private var actionUpY: Float? = null
        private var actionDownY: Float? = null
        private var scrollUp: Boolean = false
        private var scrollDown: Boolean = false

        private val MAX_CLICK_DURATION = 1000L
        private val MAX_CLICK_DISTANCE = 15

        private var pressStartTime: Long = 0
        private var pressedX = 0f
        private var pressedY = 0f
        private var stayedWithinClickDistance = false


        private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x1 - x2
            val dy = y1 - y2
            val distanceInPx = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            return pxToDp(distanceInPx)
        }

        private fun pxToDp(px: Float): Float {
            return px / context.resources.displayMetrics.density
        }

        override fun onTouch(v: View?, event: MotionEvent): Boolean {
//            TimberLogger.e("Test test onTouch $event")

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {

                    if (stayedWithinClickDistance && distance(pressedX, pressedY, event.x, event.y) > MAX_CLICK_DISTANCE) {
                        stayedWithinClickDistance = false
                    }

                    val scrollY = event.y
                    if (oldScrollY != null && scrollY < oldScrollY!!) {
                        scrollUp = true
                        scrollDown = false
                        val scrollValue = (scrollY - oldScrollY!!) * -1
//                        TimberLogger.e("Test test scroll up $scrollValue scrollY $scrollY oldScrollY $oldScrollY miniCalendarPager.y ${miniCalendarPager.y}")
                        val viewPagerGuidelineLayoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
                        if (viewPagerGuidelineLayoutParams.guideBegin > currentPosDesiredWeekHeight) {
                            viewPagerGuidelineLayoutParams.guideBegin -= scrollValue.toInt()

//                            val miniCalendarPagerLayoutParams = (miniCalendarPager.layoutParams as ViewGroup.MarginLayoutParams)
//                            miniCalendarPagerLayoutParams.bottomMargin += scrollValue.toInt()
//                            miniCalendarPager.layoutParams = miniCalendarPagerLayoutParams

//                            miniCalendarPager.y -= scrollValue.toInt()

                            miniCalendarPagerAdapter.miniCalendarsScrollUp(miniCalendarPager.currentItem, scrollValue, viewPagerGuidelineLayoutParams.guideBegin)

                            if (viewPagerGuidelineLayoutParams.guideBegin < currentPosDesiredWeekHeight) viewPagerGuidelineLayoutParams.guideBegin = currentPosDesiredWeekHeight
                            viewPagerTopGuideline.layoutParams = viewPagerGuidelineLayoutParams

                        }
                    } else if (oldScrollY != null && scrollY > oldScrollY!!) {
                        scrollUp = false
                        scrollDown = true
                        val scrollValue = scrollY - oldScrollY!!
//                        TimberLogger.e("Test test scroll down $scrollValue scrollY $scrollY oldScrollY $oldScrollY miniCalendarPager.y ${miniCalendarPager.y}")
                        val viewPagerGuidelineLayoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
                        val viewPagerSliderGuidelineLayoutParams = (viewPagerSliderGuideline.layoutParams as ConstraintLayout.LayoutParams)
                        if (viewPagerGuidelineLayoutParams.guideBegin < currentPosDesiredMonthHeight) {
                            viewPagerGuidelineLayoutParams.guideBegin += scrollValue.toInt()

//                            val miniCalendarPagerLayoutParams = (miniCalendarPager.layoutParams as ViewGroup.MarginLayoutParams)
//                            miniCalendarPagerLayoutParams.bottomMargin -= scrollValue.toInt()
//                            miniCalendarPager.layoutParams = miniCalendarPagerLayoutParams

//                            miniCalendarPager.y += scrollValue.toInt()

                            miniCalendarPagerAdapter.miniCalendarsScrollDown(miniCalendarPager.currentItem, scrollValue, viewPagerGuidelineLayoutParams.guideBegin)

                            if (viewPagerGuidelineLayoutParams.guideBegin > currentPosDesiredMonthHeight) viewPagerGuidelineLayoutParams.guideBegin = currentPosDesiredMonthHeight
                            viewPagerTopGuideline.layoutParams = viewPagerGuidelineLayoutParams
                            if (viewPagerSliderGuidelineLayoutParams.guideBegin < currentPosDesiredMonthHeight) viewPagerSliderGuidelineLayoutParams.guideBegin = currentPosDesiredMonthHeight - context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)
                            viewPagerSliderGuideline.layoutParams = viewPagerSliderGuidelineLayoutParams
//                            TimberLogger.e("Test test move position currentPosDesiredMonthHeight $currentPosDesiredMonthHeight currentPosDesiredMonthHeight- ${currentPosDesiredMonthHeight - context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)}")
                        }
                    }
                    oldScrollY = scrollY
                    return true
                }
                MotionEvent.ACTION_DOWN -> {
                    scrollUp = false
                    scrollDown = false
                    actionDownY = event.y

                    pressStartTime = System.currentTimeMillis()
                    pressedX = event.x
                    pressedY = event.y
                    stayedWithinClickDistance = true

                    TimberLogger.e("Test test ACTION_DOWN $actionDownY")
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    actionUpY = event.y
                    oldScrollY = null


                    val pressDuration = System.currentTimeMillis() - pressStartTime
                    val delegateArea = Rect()
                    agendaPager.getHitRect(delegateArea)
                    val isWithinPager = delegateArea.contains(pressedX.toInt(), pressedY.toInt())
                    TimberLogger.e("Test test check click event isWithinPager $isWithinPager pressDuration $pressDuration stayedWithinClickDistance $stayedWithinClickDistance monthView ${calendarViewModel.monthView.value}")
                    if (isWithinPager && pressDuration < MAX_CLICK_DURATION && stayedWithinClickDistance && calendarViewModel.monthView.value == true) {
                        // Click event has occurred
                        TimberLogger.e("Test test click event fully collapse")
                        monthLayoutOnFinishMoveListener.fullyCollapse { }
                        miniCalendarPagerAdapter.resetMiniCalendarsPosition(miniCalendarPager.currentItem)
                        return true
                    }

                    val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
                    val distanceWithWeekTop = layoutParams.guideBegin - currentPosDesiredWeekHeight
                    val distanceWithMonthBottom = currentPosDesiredMonthHeight - layoutParams.guideBegin
                    if (distanceWithMonthBottom > distanceWithWeekTop && distanceWithWeekTop != 0 && distanceWithMonthBottom != 0) {
                        TimberLogger.e("Test test fully collapse distanceWithMonthBottom $distanceWithMonthBottom distanceWithWeekTop $distanceWithWeekTop")
                        monthLayoutOnFinishMoveListener.fullyCollapse { }
                        miniCalendarPagerAdapter.resetMiniCalendarsPosition(miniCalendarPager.currentItem)
                    } else if (distanceWithWeekTop != 0 && distanceWithMonthBottom != 0) {
                        TimberLogger.e("Test test fully expand distanceWithMonthBottom $distanceWithMonthBottom distanceWithWeekTop $distanceWithWeekTop")
                        monthLayoutOnFinishMoveListener.fullyExpand { }
                        miniCalendarPagerAdapter.resetMiniCalendarsPosition(miniCalendarPager.currentItem)
                    } else if (distanceWithWeekTop == 0 && scrollUp) {
                        TimberLogger.e("Test test already fully collapse but process distanceWithMonthBottom $distanceWithMonthBottom distanceWithWeekTop $distanceWithWeekTop")
                        monthLayoutOnFinishMoveListener.fullyCollapse { }
                        miniCalendarPagerAdapter.resetMiniCalendarsPosition(miniCalendarPager.currentItem)
                    } else if (distanceWithMonthBottom == 0 && scrollDown) {
                        TimberLogger.e("Test test already fully expand but process distanceWithMonthBottom $distanceWithMonthBottom distanceWithWeekTop $distanceWithWeekTop")
                        monthLayoutOnFinishMoveListener.fullyExpand { }
                        miniCalendarPagerAdapter.resetMiniCalendarsPosition(miniCalendarPager.currentItem)
                    }
                    TimberLogger.e("Test test ACTION_UP $actionUpY")
                    scrollUp = false
                    scrollDown = false
                    return true
                }
            }
            return false
        }
    }

    var currentPosDesiredMonthHeight = 0
    var currentPosDesiredWeekHeight = 0
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        miniCalendarPagerAdapter = MiniCalendarPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))

        val monthStartingPosition = calendarViewModel.monthViewStartingPositionAndDate.value?.first ?: miniCalendarPagerAdapter.startingPosition
        fromPosition = monthStartingPosition

        miniCalendarPager.apply{
            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(monthStartingPosition, false)
        }

//        val gestureDetector = GestureDetector(requireContext(), CalendarGestureListener(miniCalendarPager))
//        mini_calendar_slider.setOnTouchListener { v, event ->
//            gestureDetector.onTouchEvent(event)
//            v.performClick()
//        }

        agendaPagerAdapter = AgendaPagerAdapter(requireActivity(), calendarViewModel.initialToday, object: CalendarOnScrollListener {
            override fun onScroll(dy: Int) {
                TimberLogger.e("Test test AgendaPagerAdapter onScroll dy $dy")

//                if (dy > 0) {
//                    val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
//                    if (layoutParams.guideBegin > currentPosDesiredWeekHeight) {
//                        layoutParams.guideBegin -= 40
//                        viewPagerTopGuideline.layoutParams = layoutParams
//                    } else if (calendarViewModel.monthView.value == true) {
//                        calendarViewModel.monthView.value = false
//                        val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return)
//                        rotateArrowUpward(mini_calendar_chevron)
//                        updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
//                    }
//                } else if (dy < 0) {
//                    val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
//                    if (layoutParams.guideBegin < currentPosDesiredMonthHeight) {
//                        layoutParams.guideBegin += 40
//                        viewPagerTopGuideline.layoutParams = layoutParams
//                    } else if (calendarViewModel.monthView.value == false) {
//                        calendarViewModel.monthView.value = true
//                        val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return)
//                        rotateArrowDownward(mini_calendar_chevron)
//                        updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, true, true)
//                    }
//                }

//                if (calendarViewModel.monthView.value == true) {
//                    calendarViewModel.monthView.value = false
//                    val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return@AgendaPagerAdapter)
//                    rotateArrowUpward(mini_calendar_chevron)
//                    updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
//                }
            }

            override fun onScrollChange(scrollY: Int, oldScrollY: Int) { }
        })
        dayPagerAdapter = DayPagerAdapter(requireActivity(), calendarViewModel.initialToday, object: CalendarOnScrollListener {
            override fun onScroll(dy: Int) { }

            override fun onScrollChange(scrollY: Int, oldScrollY: Int) {
                TimberLogger.e("Test test DayPagerAdapter onScrollChange scrollY $scrollY oldScrollY $oldScrollY ${scrollY - oldScrollY}")

//                if (scrollY > oldScrollY) {
//                    val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
//                    if (layoutParams.guideBegin > currentPosDesiredWeekHeight) {
//                        layoutParams.guideBegin -= 40
//                        viewPagerTopGuideline.layoutParams = layoutParams
//                    } else if (calendarViewModel.monthView.value == true) {
//                        calendarViewModel.monthView.value = false
//                        val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return)
//                        rotateArrowUpward(mini_calendar_chevron)
//                        updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
//                    }
//                } else if (scrollY < oldScrollY) {
//                    val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
//                    if (layoutParams.guideBegin < currentPosDesiredMonthHeight) {
//                        layoutParams.guideBegin += 40
//                        viewPagerTopGuideline.layoutParams = layoutParams
//                    } else if (calendarViewModel.monthView.value == false) {
//                        calendarViewModel.monthView.value = true
//                        val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return)
//                        rotateArrowDownward(mini_calendar_chevron)
//                        updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, true, true)
//                    }
//                }

//                if (calendarViewModel.monthView.value == true) {
//                    calendarViewModel.monthView.value = false
//                    val startWeekOn = getWeekStartDayOfWeek(calendarViewModel.weekStart.value ?: return@DayPagerAdapter)
//                    rotateArrowUpward(mini_calendar_chevron)
//                    updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
//                }
            }
        })

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
            (agendaPager.getChildAt(0) as RecyclerView).layoutManager?.isItemPrefetchEnabled = false
            (agendaPager.getChildAt(0) as RecyclerView).setItemViewCacheSize(0)
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
            setToolbarMonthYearTitle(it, miniCalendarPager.currentItem)
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

            val firstDayOfMonth = calendarViewModel.selectedDate.value!!.withDayOfMonth(1)
            currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
            currentPosDesiredWeekHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                false
            )
            val onTouchListener = MonthLayoutGestureListener(
                requireContext(),
                calendarViewModel,
                startWeekOn,
                viewPagerTopGuideline,
                viewPagerSliderGuideline,
                miniCalendarPager,
                miniCalendarPagerAdapter,
                agendaPager,
                currentPosDesiredWeekHeight,
                currentPosDesiredMonthHeight,
                object : MonthLayoutOnFinishMoveListener {
                    override fun fullyExpand(animationEndListener: () -> Unit) {
                        if (calendarViewModel.monthView.value == false) {
                            calendarViewModel.monthView.value = true
                            rotateArrowDownward(mini_calendar_chevron)
                            updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, true, true)
                            timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                        } else {
                            viewPagerTopGuideline.animateHeightChange(currentPosDesiredMonthHeight, currentPosDesiredMonthHeight) {
                                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
                            }
                            viewPagerSliderGuideline.animateHeightChange(currentPosDesiredMonthHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height), currentPosDesiredMonthHeight) {
                            }
                        }
                    }

                    override fun fullyCollapse(animationEndListener: () -> Unit) {
                        if (calendarViewModel.monthView.value == true) {
                            calendarViewModel.monthView.value = false
                            rotateArrowUpward(mini_calendar_chevron)
                            updateMiniCalendarHeight(miniCalendarPagerLayoutListener, startWeekOn, false, true)
                            timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                        } else {
                            viewPagerTopGuideline.animateHeightChange(currentPosDesiredWeekHeight, currentPosDesiredMonthHeight) {
                                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
                            }
                            viewPagerSliderGuideline.animateHeightChange(currentPosDesiredWeekHeight - requireContext().resources.getDimensionPixelSize(R.dimen.calendar_slider_height), currentPosDesiredMonthHeight) {
                            }
                        }
                    }

                }
            )

            fragmentMonthLayout.setOnTouchListener(onTouchListener)
            fragmentMonthLayout.agendaPager = agendaPager

            miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)
            miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }

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
            val selectedDate = calendarViewModel.selectedDate.value ?: return@observe
            val startWeekOn = startWeekOn ?: return@observe
            val firstDayOfTheWeekNumber = selectedDate.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
            val temporalField = WeekFields.of(startWeekOn, 7 - firstDayOfTheWeekOffset).dayOfWeek()
            val firstDayOfTheWeek = selectedDate.with(temporalField, 1)
            if (monthView == false) calendarViewModel.weekViewStartingPositionAndDate.value = Pair(miniCalendarPager.currentItem, firstDayOfTheWeek)
            else calendarViewModel.monthViewStartingPositionAndDate.value = Pair(miniCalendarPager.currentItem, selectedDate.withDayOfMonth(1))

            // TODO Delete this
            setToolbarMonthYearTitle(selectedDate, miniCalendarPager.currentItem)
        }

        calendarViewModel.activeCalendars.observe(viewLifecycleOwner) { activeCalendars ->
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

    private fun setHeaderDaysContent(startWeekOn: DayOfWeek, timeZoneId: String) {
        // setup week days header
        miniCalendarDaysHeaderLayout?.run {

            val today = LocalDate.now(ZoneId.of(timeZoneId))
            val selectedDate = calendarViewModel.selectedDate.value
            var dayToHighlight: DayOfWeek? = null
            selectedDate?.let {
                val firstDayOfTheMonth = selectedDate.withDayOfMonth(1)
                val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val miniCalendarFirstDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
                val lastDayOfTheMonth = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)
                val miniCalendarLastDay = lastDayOfTheMonth.plusDays(lastDayOfMonthOffset.toLong())
                dayToHighlight =
                    if ((calendarViewModel.monthView.value == true && (selectedDate.month == today.month || (today.isAfter(miniCalendarFirstDay) && today.isBefore(miniCalendarLastDay)))) ||
                        (calendarViewModel.monthView.value == false && selectedDate.weekNumber(startWeekOn) == today.weekNumber(startWeekOn))) today.dayOfWeek
                    else null
            }

            this.removeAllViews()

            val weekDays = DayOfWeek.values()
            // We do minus 1 to match java.time DayOfWeek ordinals
            val weekStart = if (startWeekOn.value == 0) WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1 else startWeekOn.value - 1
            val weekEnd = 7
            // Iterate from weekStart first
            weekDays
                .slice(weekStart until weekEnd) // until excludes weekEnd value
                .forEach { dayOfWeek ->
                    setDayHeaderItem(this, dayOfWeek, dayToHighlight == dayOfWeek)
                }
            if (weekStart != 0) {
                // If weekStart was not Monday, iterate from 0 to fill the rest of the days
                weekDays
                    .slice(0 until weekStart) // until excludes weekStart value
                    .forEach { dayOfWeek ->
                        setDayHeaderItem(this, dayOfWeek, dayToHighlight == dayOfWeek)
                    }
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

    // TODO Delete position parameter
    private fun setToolbarMonthYearTitle(localDate: LocalDate, position: Int) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        // TODO Delete position
        toolbarTitle.text = "$month $year ${position.toString().takeLast(3)}"
        mini_calendar_chevron.visibleOrGone(true)
    }
}
