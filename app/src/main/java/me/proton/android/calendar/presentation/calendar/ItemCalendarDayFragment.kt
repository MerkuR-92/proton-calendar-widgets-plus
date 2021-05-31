package me.proton.android.calendar.presentation.calendar

import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.collection.LongSparseArray
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import biweekly.ICalendar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.item_agenda_event_all_day.view.*
import kotlinx.android.synthetic.main.item_calendar_day_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.collapse
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.expand
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.text.DateFormat
import java.time.Duration
import java.time.LocalDate
import java.util.*

class ItemCalendarDayFragment() : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var position: Int? = null
    private var date: LocalDate? = null

    private var timeZoneId: String? = null
    private var timeFormatIs24Hour: Boolean? = null
    private var userAddresses: List<Address>? = null
    private val agendaMediator = MediatorLiveData<Triple<String, Boolean, List<Address>>>()

    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<Event>>
    private var selectedDate: LocalDate? = null

    private lateinit var day: java.util.Calendar
    private lateinit var timeFormat: DateFormat
    private lateinit var dayView: DayView

    private var allEvents: LongSparseArray<List<Event>>? = null
    private var dateFormat: DateFormat? = null

    private lateinit var allDayEventCroppedListAdapter: DayViewAllDayEventAdapter
    private lateinit var allDayEventListAdapter: DayViewAllDayEventAdapter

    companion object {
        fun newInstance(position: Int, date: LocalDate) : ItemCalendarDayFragment{
            return ItemCalendarDayFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArguments.POSITION_ARG, position)
                    putSerializable(FragmentArguments.DATE_ARG, date)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(FragmentArguments.POSITION_ARG)
            date = it.getSerializable(FragmentArguments.DATE_ARG) as? LocalDate?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =  inflater.inflate(R.layout.item_calendar_day_fragment, container, false)

        val dayLayout: LinearLayout = rootView.findViewById(R.id.day_layout)
        dayLayout.layoutTransition.setAnimateParentHierarchy(false)

        // Create a new calendar object set to the start of today
        day = java.util.Calendar.getInstance()
        day.set(java.util.Calendar.HOUR_OF_DAY, 0)
        day.set(java.util.Calendar.MINUTE, 0)
        day.set(java.util.Calendar.SECOND, 0)
        day.set(java.util.Calendar.MILLISECOND, 0)

        // Populate today's entry in the map with a list of example events
        allEvents = LongSparseArray<List<Event>>()

        dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())

        dayView = rootView.findViewById(R.id.day_view)

        val scrollView: ScrollView = rootView.findViewById(R.id.day_scroll_view)
        rootView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                scrollView.scrollY = calendarViewModel.dayViewScrollYPosition.value ?: 0
                return false
            }
        })

        // Inflate a label view for each hour the day view will display
        val hour: java.util.Calendar = day.clone() as java.util.Calendar
        val hourLabelViews: MutableList<View> = java.util.ArrayList()
        for (i in dayView.startHour..dayView.endHour) {
            hour[java.util.Calendar.HOUR_OF_DAY] = i
            val hourLabelView = layoutInflater.inflate(R.layout.item_hour_label, dayView, false) as TextView
            hourLabelView.text = timeFormat.format(hour.time)
            hourLabelViews.add(hourLabelView)
        }
        dayView.setHourLabelViews(hourLabelViews)

        scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            calendarViewModel.dayViewScrollYPosition.value = scrollY
            if (oldScrollY - scrollY < 0) {
                // TODO Hide mini calendar
            }
        }

        return rootView
    }

    override fun onResume() {
        super.onResume()
        day_scroll_view.scrollY = calendarViewModel.dayViewScrollYPosition.value ?: 0
    }

    private fun onEventsChange(timeZoneId: String) {
        // The day view needs a list of event views and a corresponding list of event time ranges
        var eventViews: MutableList<View?>? = null
        var eventTimeRanges: MutableList<DayView.EventTimeRange?>? = null
        val partialDayEvents: List<Event>? = allEvents!![day.timeInMillis]?.filter { it.spansSingleDay(true, timeZoneId = timeZoneId) }
        if (partialDayEvents != null) {
            // Sort the events by start time so the layout happens in correct order
            Collections.sort(partialDayEvents,
                Comparator<Event> { o1, o2 ->
                    val o1Start = o1.getStart(timeZoneId) ?: return@Comparator 0 // Date Start property can never be null here
                    val o2Start = o2.getStart(timeZoneId) ?: return@Comparator 0 // Date Start property can never be null here
                    if (o1Start.hour < o2Start.hour) -1 else if (o1Start.hour == o2Start.hour) if (o1Start.minute < o2Start.minute) -1 else if (o1Start.minute == o2Start.minute) 0 else 1 else 1
                }
            )
            eventViews = java.util.ArrayList()
            eventTimeRanges = java.util.ArrayList<DayView.EventTimeRange?>()

            // Reclaim all of the existing event views so we can reuse them if needed, this process
            // can be useful if your day view is hosted in a recycler view for example
            val recycled: List<View> = dayView.removeEventViews() as List<View>
            var remaining = recycled.size
            for (event in partialDayEvents) {
                // Try to recycle an existing event view if there are enough left, otherwise inflate
                // a new one
                val eventView =
                    if (remaining > 0) recycled[--remaining] else layoutInflater.inflate(R.layout.item_day_view_event_partial, dayView, false)

                val summary = event.summary
                (eventView.findViewById<View>(R.id.text_title) as TextView).text = if (summary.isNullOrEmpty()) getString(R.string.default_event_summary) else summary
                (eventView.findViewById<View>(R.id.view_background).background as LayerDrawable).findDrawableByLayerId(R.id.main_surface).setTint(Color.parseColor(event.calendar.color))
                (eventView.findViewById<View>(R.id.view_background).background as LayerDrawable).findDrawableByLayerId(R.id.side_strip).setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))

                // When an event is clicked, start a new draft event and show the edit event dialog
                eventView.setOnClickListener {
                    onEventClick(event)
                }
                eventViews.add(eventView)

                // The day view needs the event time ranges in the start minute/end minute format,
                // so calculate those here
                val dtStart = event.getStart(timeZoneId)
                val dtEnd = event.getEnd(timeZoneId)
                val startMinute: Int = 60 * (dtStart?.hour ?: 0) + (dtStart?.minute ?: 0)
                val eventDuration = Duration.between(dtStart, dtEnd).toMinutes().toInt()
                val endMinute: Int = startMinute + if (eventDuration < 30) 30 else eventDuration
                eventTimeRanges.add(DayView.EventTimeRange(startMinute, endMinute))
            }
        }

        // Update the day view with the new events
        dayView.setEventViews(eventViews, eventTimeRanges)
    }

    private fun onEventClick(event: Event) {
        if (event.decryptionStatus == Event.DecryptionStatus.SUCCESS) {
            findNavController().navigate(
                Navigation.Deeplink.toEventDetails(
                    event.id,
                    event.occurrence?.occurrenceNumber ?: 0
                )
            )
        } else {
            val confirmationMessage =
                if (event.isRecurring()) R.string.event_decryption_error_dialog_confirmation_recurring
                else R.string.event_decryption_error_dialog_confirmation
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.event_decryption_error_dialog_title)
                .setMessage(R.string.event_decryption_error_dialog_message)
                .setPositiveButton(confirmationMessage) { _, _ ->
                    lifecycleScope.launch { // TODO
                        val deleteResult = withContext(Dispatchers.Default) {
                            calendarViewModel.handleDeleteEvent(
                                event.id,
                                EventEditDeleteOption.ALL_EVENTS
                            )
                        }
                        if (deleteResult is UseCase.Result.Success<*>) {
                            requireActivity().displaySnackBar(getString(R.string.snack_event_deleted))
                        } else {
                            if (deleteResult is UseCase.Result.Error) {
                                logger.e("Error deleting event: ${deleteResult.message}")
                            } else if (deleteResult is UseCase.Result.InvalidParams) {
                                logger.e("InvalidParams deleting event: ${deleteResult.message}")
                            }
                            requireActivity().displaySnackBar(getString(R.string.snack_event_deleted_error))
                        }
                    }
                }
                .setNegativeButton(R.string.event_decryption_error_dialog_close) { _, _ -> }
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        agendaMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && timeFormatIs24Hour != null && userAddresses != null) {
                onEventsChange(timeZoneId!!)
                agendaMediator.value = Triple(timeZoneId!!, timeFormatIs24Hour!!, userAddresses!!)
            }
        }
        agendaMediator.addSource(calendarViewModel.timeFormat) { value ->
            timeFormatIs24Hour = value?.let { calendarViewModel.timeFormatIs24Hour(requireContext()) }

            if (timeZoneId != null && timeFormatIs24Hour != null && userAddresses != null) {
                agendaMediator.value = Triple(timeZoneId!!, timeFormatIs24Hour!!, userAddresses!!)
            }
        }
        agendaMediator.addSource(calendarViewModel.userAddresses) { value ->
            userAddresses = value

            if (userAddresses?.firstOrNull { it.displayName == null } != null) {
                // Refresh Addresses for user to fetch displayName values
                calendarViewModel.refreshAddressesFromServer()
            }

            if (timeZoneId != null && timeFormatIs24Hour != null && userAddresses != null) {
                agendaMediator.value = Triple(timeZoneId!!, timeFormatIs24Hour!!, userAddresses!!)
            }
        }
        agendaMediator.observe(viewLifecycleOwner) {
            it?.let { setupItemMiniCalendarContent(it.first, it.second, it.third) }
        }

        calendarViewModel.dayViewScrollYPosition.observe(viewLifecycleOwner) {
            if (!this.isResumed) day_scroll_view.scrollY = it
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, timeFormatIs24Hour: Boolean, userAddresses: List<Address>) {
        val immutableDate = date ?: return

        if (FeatureFlag.NEW_EVENT_DECRYPTION) {

            // TODO remove UserID livedata

            calendarViewModel.userId.observe(viewLifecycleOwner) { userId ->

                userId?.let {
                    getEvents(immutableDate, timeZoneId)
                }

            }

        }

        calendarViewModel.selectedDate.distinctUntilChanged().observe(viewLifecycleOwner) { selectedDate ->
            if (this.selectedDate == null) {
                // View pager creates fragment for selectedDate + 2 when you start swiping, so comparing immutableDate
                //  and selectedDate would make us remove the observer for the flow we just created on start.
                // To avoid this case we skip the first observed value of selectedDate.
                this.selectedDate = selectedDate
                return@observe
            }
            this.selectedDate = selectedDate
            if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers() &&
                immutableDate != selectedDate &&
                immutableDate != selectedDate.minusDays(1) &&
                immutableDate != selectedDate.plusDays(1)) {
                logger.v("ItemCalendarDayFragment: events flow: remove observer for $immutableDate. Selected date is $selectedDate")
                eventsLiveData.removeObservers(viewLifecycleOwner)
            } else if (this::eventsLiveData.isInitialized && !eventsLiveData.hasActiveObservers() &&
                (immutableDate == selectedDate ||
                        immutableDate == selectedDate.minusDays(1) ||
                        immutableDate == selectedDate.plusDays(1))) {
                logger.v("ItemCalendarDayFragment: events flow: recreate getEvents flow $immutableDate. Selected date is $selectedDate")
                getEvents(immutableDate, timeZoneId)
            }
        }
    }

    private fun getEvents(immutableDate: LocalDate, timeZoneId: String) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            logger.v("ItemCalendarDayFragment: events flow: remove already existing observer for $immutableDate")
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        eventsLiveData = calendarViewModel.getEvents(immutableDate, immutableDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        // TODO
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {
                        allEvents?.put(
                            day.timeInMillis,
                            it.events
                        )
                        onEventsChange(timeZoneId)

                        val allDayEvents = it.events.filter { event -> !event.spansSingleDay(true, timeZoneId) }
                        val croppedList = allDayEvents.take(
                            if (allDayEvents.size > DAY_VIEW_ALL_DAY_MAX) DAY_VIEW_ALL_DAY_MAX - 1
                            else DAY_VIEW_ALL_DAY_MAX
                        )
                        val moreEvents =
                            if (allDayEvents.size > DAY_VIEW_ALL_DAY_MAX) allDayEvents.takeLast(allDayEvents.size - (DAY_VIEW_ALL_DAY_MAX - 1))
                            else listOf()

                        all_day_layout.visibleOrGone(!allDayEvents.isNullOrEmpty())
                        all_day_more_items_layout.removeAllViews()

                        val userEmails = calendarViewModel.getUserEmails()

                        /* Cropped list */
                        val allDayEventsCroppedListLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
                        all_day_items_cropped_list.layoutManager = allDayEventsCroppedListLayoutManager
                        allDayEventCroppedListAdapter = DayViewAllDayEventAdapter(userEmails ?: listOf()) { event ->
                            onEventClick(event)
                        }
                        all_day_items_cropped_list.adapter = allDayEventCroppedListAdapter
                        allDayEventCroppedListAdapter.submitList(croppedList)

                        /* Rest of the list */
                        val allDayEventsMoreListLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
                        all_day_items_list.layoutManager = allDayEventsMoreListLayoutManager
                        allDayEventListAdapter = DayViewAllDayEventAdapter(userEmails ?: listOf()) { event ->
                            onEventClick(event)
                        }
                        all_day_items_list.adapter = allDayEventListAdapter
                        allDayEventListAdapter.submitList(moreEvents)

                        all_day_more_collapse_button.setOnSingleClickListener {
                            collapse(all_day_items_list)
                            all_day_more_collapse_button.visibleOrGone(false)
                            all_day_more_items_layout.visibleOrInvisible(true)
                        }

                        if (allDayEvents.size > DAY_VIEW_ALL_DAY_MAX) {
                            val eventView = layoutInflater.inflate(R.layout.item_day_view_event_all_day, dayView, false)

                            val title = (eventView.findViewById<View>(R.id.text_title) as TextView)
                            val titleParams = title.layoutParams
                            titleParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            title.layoutParams = titleParams
                            title.gravity = Gravity.CENTER_VERTICAL
                            title.text = getString(R.string.day_view_all_day_more, allDayEvents.size - (DAY_VIEW_ALL_DAY_MAX - 1))
                            title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))

                            (eventView.findViewById<View>(R.id.view_background).background as LayerDrawable).findDrawableByLayerId(R.id.main_surface).setTint(
                                ContextCompat.getColor(requireContext(), R.color.interaction_weak)
                            )
                            (eventView.findViewById<View>(R.id.view_background).background as LayerDrawable).findDrawableByLayerId(R.id.side_strip).setTint(
                                ContextCompat.getColor(requireContext(), R.color.interaction_weak)
                            )

                            // When an event is clicked, start a new draft event and show the edit event dialog
                            eventView.setOnClickListener {
                                expand(all_day_items_list)
                                all_day_more_collapse_button.visibleOrGone(true)
                                all_day_more_items_layout.visibleOrInvisible(false)
                            }

                            val layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                resources.getDimensionPixelSize(R.dimen.all_day_item_height))
                            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.all_day_item_margin_end)
                            layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.all_day_item_margin_bottom)
                            all_day_more_items_layout.addView(eventView, layoutParams)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        // TODO
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers()) {
            logger.v("ItemCalendarDayFragment: events flow: remove observers in on destroy for $date")
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
    }
}
