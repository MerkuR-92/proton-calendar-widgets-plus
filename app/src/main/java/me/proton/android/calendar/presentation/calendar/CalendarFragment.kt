package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Operation
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import me.proton.android.calendar.R
import me.proton.android.calendar.domain.ValueStoreProvider
import kotlinx.android.synthetic.main.fragment_calendar.*
import kotlinx.android.synthetic.main.item_mini_calendar_fragment.*
import kotlinx.android.synthetic.main.pager_mini_calendar.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.*
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.MainViewModel
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.android.viewmodel.ext.android.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CalendarFragment : BaseDialogFragment() {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()

    private val initialToday = LocalDate.now()
    private lateinit var miniCalendarAdapter: MiniCalendarAdapter
    private lateinit var agendaAdapter: CalendarAgendaAdapter

    private lateinit var toolbarTitle: TextView

    private val valueStoreProvider: ValueStoreProvider by inject()
    private val mainViewModel: MainViewModel by viewModel()

    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_calendar

    override val isTopLevel = true
    override val isScrollable = false

    override fun onToolbarCreated(toolbar: Toolbar) {

        val buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_primary, toolbar_content, false)
        with (buttonCreate) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_plus))
            setOnClickListener {
                // each item in the adapter is one day
                val currentDate = initialToday.plusDays((agendaPager.currentItem - agendaAdapter.startingPosition).toLong())
                requireActivity().findNavController(R.id.nav_host_fragment_container_view).navigate(Navigation.Deeplink.toEventCreate(currentDate, ICalUtils.generateEventStartTime()))
            }
        }
        val buttonToday = layoutInflater.inflate(R.layout.toolbar_action_secondary, toolbar_content, false)
        with (buttonToday) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_calendar_today))
//            (this as ImageButton).setColorFilter(0) // this image is not one color, so we remove default tinting
            setOnClickListener {
                val todayOffset = ChronoUnit.DAYS.between(initialToday, LocalDate.now()).toInt()
                agendaPager.setCurrentItem(agendaAdapter.startingPosition + todayOffset, true)
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.toolbar_content)) {
            addView(
                buttonToday, resources.getDimensionPixelSize(
                    R.dimen.action_clickable_size
                ), resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )
            addView(
                buttonCreate, resources.getDimensionPixelSize(
                    R.dimen.action_clickable_size
                ), resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )
        }

        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)

    }

    val miniCalendarPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            handleMiniCalendarPageSelected(position)
        }
    }

    private fun handleMiniCalendarPageSelected(position: Int) {
        // ViewPager will adjust its height to the largest item it contains and display empty space for
        //  smaller items, like months with fewer week lines. That's why we need to resize it every time we
        //  display a month.
        val firstDayOfMonth = miniCalendarAdapter.firstDayOfMonth.plusMonths((position - miniCalendarAdapter.startingPosition).toLong())
        miniCalendarPager.animateHeightChange(
            MiniCalendarItemAdapter.calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                calendarViewModel.startWeekOn
            )
        )

        toolbarTitle.text = firstDayOfMonth.formatMonth()
    }

    val agendaPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            //setToolbarTitle(position)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        agendaPager.unregisterOnPageChangeCallback(agendaPageChangeCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appbar.addView(layoutInflater.inflate(R.layout.pager_mini_calendar, appbar, false))

        miniCalendarAdapter = MiniCalendarAdapter(requireActivity(), calendarViewModel, initialToday.withDayOfMonth(1))
        miniCalendarPager.apply{
            adapter = miniCalendarAdapter
            offscreenPageLimit = 1
            setCurrentItem(miniCalendarAdapter.startingPosition, false)
        }
        miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)

        miniCalendarPager.postDelayed({
            handleMiniCalendarPageSelected(miniCalendarAdapter.startingPosition)
        }, 1000)

        agendaAdapter = CalendarAgendaAdapter(requireActivity(), calendarViewModel, initialToday)
        agendaPager.apply{
            adapter = agendaAdapter
            offscreenPageLimit = 3 // TODO
            setCurrentItem(agendaAdapter.startingPosition, false)
        }
        agendaPager.registerOnPageChangeCallback(agendaPageChangeCallback)

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
            while(true) {
                delay(SYNC_EVENTS_REFRESH_MS)

                val userId = valueStoreProvider.provideValueStore("TODO LOGIN").getString("USERID")
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

}
