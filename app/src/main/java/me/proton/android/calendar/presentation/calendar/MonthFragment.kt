package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Operation
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_month.*
import kotlinx.android.synthetic.main.pager_mini_calendar.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import me.proton.core.domain.entity.UserId
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import java.time.LocalDate

class MonthFragment : BaseDialogFragment() {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val handleAlarmsUseCase: HandleAlarmsUseCase by inject()

    private lateinit var miniCalendarPagerAdapter: MiniCalendarPagerAdapter
    private lateinit var agendaPagerAdapter: AgendaPagerAdapter

    private lateinit var toolbarTitle: TextView

    private val valueStoreProvider: ValueStoreProvider by inject()
    private val mainViewModel: MainViewModel by viewModel()

    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_month

    override val isTopLevel = true
    override val isScrollable = false

    override fun onToolbarCreated(toolbar: Toolbar) {
        val buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_primary, toolbar_content, false)
        with (buttonCreate) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_plus))
            setOnClickListener {
                // each item in the adapter is one day
                val currentDate = calendarViewModel.initialToday.plusDays((agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
                requireActivity().findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toEventCreate(currentDate, ICalUtils.generateEventStartTime()))
            }
        }
        val buttonToday = layoutInflater.inflate(R.layout.toolbar_action_secondary, toolbar_content, false)
        with (buttonToday) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_calendar_today))
            setOnClickListener {
                val todayDate = LocalDate.now(calendarViewModel.timeZoneId)
                calendarViewModel.handleDaySelected(todayDate)
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.toolbar_content)) {
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

        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)

    }

    val miniCalendarPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            val firstDayOfMonth = miniCalendarPagerAdapter.firstDayOfMonth.plusMonths((position - miniCalendarPagerAdapter.startingPosition).toLong())
            calendarViewModel.handleDaySelected(firstDayOfMonth)

            setToolbarMonthYearTitle(firstDayOfMonth)

            adjustMiniCalendarView(position)
        }
    }

    override fun onResume() {
        super.onResume()

        // make sure currently selected month always has desired height, even if adjacent pages make
        //  entire ViewPager to have different height
        miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
    }

    override fun onPause() {
        super.onPause()

        miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
    }

    private fun adjustMiniCalendarView(position: Int) {

        miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(miniCalendarPagerLayoutListener)

        // ViewPager will adjust its height to the largest item it contains and display empty space for
        //  smaller items, like months with fewer week lines. That's why we need to resize it every time we
        //  display a month
        val firstDayOfMonth = miniCalendarPagerAdapter.firstDayOfMonth.plusMonths((position - miniCalendarPagerAdapter.startingPosition).toLong())
        miniCalendarPager.animateHeightChange(
            MiniCalendarItemAdapter.calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                calendarViewModel.startWeekOn,
            )
        ) {
            miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(miniCalendarPagerLayoutListener)
        }
    }

    val agendaPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val currentDate = calendarViewModel.initialToday.plusDays((agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
            calendarViewModel.handleDaySelected(currentDate)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        miniCalendarPager.unregisterOnPageChangeCallback(miniCalendarPageChangeCallback)
        agendaPager.unregisterOnPageChangeCallback(agendaPageChangeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appbar.addView(layoutInflater.inflate(R.layout.pager_mini_calendar, appbar, false))

        miniCalendarPagerAdapter = MiniCalendarPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))
        miniCalendarPager.apply{
            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(miniCalendarPagerAdapter.startingPosition, false)
        }
        miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)

        agendaPagerAdapter = AgendaPagerAdapter(requireActivity(), calendarViewModel, calendarViewModel.initialToday)
        agendaPager.apply{
            adapter = agendaPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(agendaPagerAdapter.startingPosition, false)
        }
        agendaPager.registerOnPageChangeCallback(agendaPageChangeCallback)

        // Init view pagers in VM
        calendarViewModel.setCalendarPagers(miniCalendarPager, agendaPager)
        // Init selected date
        calendarViewModel.handleInitialDaySelection(calendarViewModel.initialToday)

        setToolbarMonthYearTitle(calendarViewModel.initialToday)

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

            val userId = valueStoreProvider.provideValueStore("TODO LOGIN").getString("USERID")?.let { UserId(it) }

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

            while(true) {
                delay(SYNC_EVENTS_IN_APP_REFRESH.toMillis())

                if (userId != null) {
                    mainViewModel.syncServerEvents(userId).observe(viewLifecycleOwner) {
                        if (it is Operation.State.IN_PROGRESS) {
                            setProgressBarVisibility(true)
                        } else {
                            setProgressBarVisibility(false)
                        }
                    }
                }
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
    val miniCalendarPagerLayoutListener = object: ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {

            val firstDayOfMonth = miniCalendarPagerAdapter.firstDayOfMonth.plusMonths((miniCalendarPager.currentItem - miniCalendarPagerAdapter.startingPosition).toLong())
            val desiredHeight = MiniCalendarItemAdapter.calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                calendarViewModel.startWeekOn
            )

            if (miniCalendarPager.height != desiredHeight) {
                miniCalendarPager.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val layoutParams = miniCalendarPager.layoutParams.apply {
                    height = desiredHeight
                }
                miniCalendarPager.layoutParams = layoutParams
                miniCalendarPager.viewTreeObserver.addOnGlobalLayoutListener(this)
            }

        }

    }

    private fun setToolbarMonthYearTitle(localDate: LocalDate) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        year.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_hint)), 0, year.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        toolbarTitle.text = "$month "
        toolbarTitle.append(year)
    }

}
