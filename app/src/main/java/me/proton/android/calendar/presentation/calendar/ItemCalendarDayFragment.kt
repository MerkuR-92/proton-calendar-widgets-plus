package me.proton.android.calendar.presentation.calendar

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.collection.LongSparseArray
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.item_agenda_event_all_day.view.*
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.android.synthetic.main.item_calendar_day_fragment.*
import kotlinx.coroutines.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.collapse
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.expand
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.user.domain.entity.UserAddress
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.text.SimpleDateFormat
import java.time.*
import java.util.*

class ItemCalendarDayFragment() : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var position: Int? = null
    private var date: LocalDate? = null

    private var timeZoneId: String? = null
    private var timeFormatIs24Hour: Boolean? = null
    private var userAddresses: List<UserAddress>? = null
    private val agendaMediator = MediatorLiveData<Triple<String, Boolean, List<UserAddress>>>()

    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<Event>>
    private var selectedDate: LocalDate? = null

    private lateinit var day: LocalTime
    private lateinit var dayView: DayView

    private lateinit var allEvents: List<Event>

    private lateinit var allDayEventCroppedListAdapter: DayViewAllDayEventAdapter
    private lateinit var allDayEventListAdapter: DayViewAllDayEventAdapter

    private var preDrawDone = false

    private var onScrollChangeListener: View.OnScrollChangeListener? = null

    companion object {
        fun newInstance(position: Int, date: LocalDate): ItemCalendarDayFragment {
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
        val rootView = inflater.inflate(R.layout.item_calendar_day_fragment, container, false)

        val dayLayout: LinearLayout = rootView.findViewById(R.id.day_layout)
        dayLayout.layoutTransition.setAnimateParentHierarchy(false)

        // Create a new calendar object set to the start of today
        day = LocalTime.MIDNIGHT

        // Populate today's entry in the map with a list of example events
        allEvents = listOf()

        dayView = rootView.findViewById(R.id.day_view)

        // setHourLabelViews() must be called before the view is rendered so we need to make sure to call it without waiting for first observe value
        setHourLabelViews()

        calendarViewModel.timeFormat.observe(viewLifecycleOwner) {
            setHourLabelViews()
        }

        onScrollChangeListener = View.OnScrollChangeListener { _, _, scrollY, _, _ ->
            // If fragment is the one resumed, save scrolling position to apply it to other days on swipe
            if (this.isResumed) calendarViewModel.dayViewScrollYPosition.value = scrollY
        }

        val scrollView: ScrollView = rootView.findViewById(R.id.day_scroll_view)
        rootView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scrollView.viewTreeObserver.removeOnPreDrawListener(this)
                doOnPreDraw(scrollView)
                return false
            }
        })

        scrollView.setOnScrollChangeListener(onScrollChangeListener)

        dayView.setOnTouchListener { v, event ->
            // Handle clicks in empty day view spaces to create events
            if (event.action == MotionEvent.ACTION_UP && dayView.areCoordinatesWithinEventGrid(
                    event.x.toInt(),
                    event.y.toInt()
                )
            ) {
                val startTime = dayView.getTimeForYCoordinate(event.y.toInt())
                calendarViewModel.lifeCycleScope.launch {
                    val calendarSettings = calendarViewModel.getDefaultCalendarSettings()
                    val immutableDate = date
                    if (calendarSettings == null || immutableDate == null) {
                        return@launch
                    }
                    val truncatedStartTime = LocalTime.of(startTime.hour, if (startTime.minute >= 30) 30 else 0)
                    requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                        .navigate(
                            Navigation.Deeplink.toEventCreate(
                                immutableDate,
                                truncatedStartTime
                            )
                        )
                }
                v.performClick()
            }
            true
        }

        return rootView
    }

    private fun setHourLabelViews() {
        val timeFormatIs24Hour = calendarViewModel.timeFormatIs24Hour(requireContext())
        // Inflate a label view for each hour the day view will display
        val hourLabelViews: MutableList<View> = ArrayList()
        for (i in dayView.startHour..dayView.endHour) {
            val tmpDay =
                if (i == 24) LocalTime.MIDNIGHT
                else day.withHour(i)
            val hourLabelView = layoutInflater.inflate(R.layout.item_hour_label, dayView, false) as TextView
            hourLabelView.text = tmpDay.formatTime(timeFormatIs24Hour, short = true)
            hourLabelViews.add(hourLabelView)
        }
        dayView.setHourLabelViews(hourLabelViews)
    }

    private lateinit var updateCurrentTimeIndicatorJob: Job
    private fun updateCurrentTimeIndicatorDelayed(timeZoneId: ZoneId) {
        // Create job to update the current time indicator every minute
        if (this::updateCurrentTimeIndicatorJob.isInitialized && updateCurrentTimeIndicatorJob.isActive) {
            updateCurrentTimeIndicatorJob.cancel()
        }
        updateCurrentTimeIndicatorJob = lifecycleScope.launch {
            if (date == LocalDate.now(timeZoneId)) {
                if (!dayView.hasCurrentTimeIndicator()) {
                    // Create time indicator in day view if it doesn't exist yet
                    val currentTimeView = layoutInflater.inflate(R.layout.item_current_time_indicator, dayView, false)
                    dayView.setCurrentTimeView(requireContext(), currentTimeView)
                }
                dayView.updateCurrentTimeView(timeZoneId)
            } else if (date != LocalDate.now(timeZoneId) && dayView.hasCurrentTimeIndicator()) {
                dayView.removeCurrentTimeView() // Remove current time indicator if this fragment is not current day anymore
            }
            delay(REFRESH_CURRENT_TIME_INDICATOR)
            updateCurrentTimeIndicatorDelayed(timeZoneId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateCurrentTimeIndicatorJob.cancel()
    }

    override fun onResume() {
        super.onResume()
        day_scroll_view.setOnScrollChangeListener(null)
        // Set scrolling position using previous view value if it exists
        day_scroll_view.scrollY = calendarViewModel.dayViewScrollYPosition.value ?: 0
        day_scroll_view.setOnScrollChangeListener(onScrollChangeListener)
    }

    private fun onEventsChange(timeZoneId: String, userAddresses: List<UserAddress>) {
        val userEmails = userAddresses.map { userAddress -> userAddress.email }
        // The day view needs a list of event views and a corresponding list of event time ranges
        var eventViews: MutableList<View?>? = null
        var eventTimeRanges: MutableList<DayView.EventTimeRange?>? = null
        val partialDayEvents: List<Event> =
            allEvents.filter { it.spansSingleDay(true, timeZoneId = timeZoneId) }
                .sortedBy { it.getStart(timeZoneId).toEpochSecond() }

        eventViews = ArrayList()
        eventTimeRanges = ArrayList<DayView.EventTimeRange?>()

        // Reclaim all of the existing event views so we can reuse them if needed, this process
        // can be useful if your day view is hosted in a recycler view for example
        val recycled: List<View> = dayView.removeEventViews() as List<View>
        var remaining = recycled.size
        for (event in partialDayEvents) {
            // Try to recycle an existing event view if there are enough left, otherwise inflate
            // a new one
            val eventView =
                if (remaining > 0) recycled[--remaining] else layoutInflater.inflate(
                    R.layout.item_day_view_event_partial,
                    dayView,
                    false
                )

            setEventViewStatus(eventView, event, userEmails, timeZoneId)

            // When an event is clicked, start a new draft event and show the edit event dialog
            eventView.setOnClickListener {
                onEventClick(event)
            }
            eventViews.add(eventView)

            // The day view needs the event time ranges in the start minute/end minute format,
            // so calculate those here
            val dtStart = event.getStart(timeZoneId)
            val dtEnd = event.getEnd(timeZoneId)
            val startMinute: Int = 60 * dtStart.hour + dtStart.minute
            val eventDuration = Duration.between(dtStart, dtEnd).toMinutes().toInt()
            val endMinute: Int = startMinute + if (eventDuration < 30) 30 else eventDuration
            eventTimeRanges.add(DayView.EventTimeRange(startMinute, endMinute))
        }

        // Update the day view with the new events
        dayView.setEventViews(eventViews, eventTimeRanges)
    }

    private fun setEventViewStatus(eventView: View, event: Event, userEmails: List<String>, timeZoneId: String) {

        val eventItemTitle = eventView.findViewById<View>(R.id.text_title) as TextView

        val viewBackground: LayerDrawable =
            eventView.findViewById<View>(R.id.view_background).background as LayerDrawable
        val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
        val viewBorder: Drawable = viewBackground.findDrawableByLayerId(R.id.border)
        val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)

        val viewBackgroundStripedLayout: CardView = eventView.findViewById(R.id.view_background_striped_layout)
        val viewBackgroundStriped: View = eventView.findViewById(R.id.view_background_striped)

        val decryptionErrorIcon: ImageView = eventView.findViewById(R.id.decryption_error_icon)
        val decryptionErrorView: View = eventView.findViewById(R.id.decryption_error_view)

        val summary = event.summary
        eventItemTitle.text = if (summary.isNullOrEmpty()) getString(R.string.default_event_summary) else summary
        eventItemTitle.doOnPreDraw {
            // This doOnPreDraw checks if last line of text view is cut off
            eventItemTitle.layout ?: return@doOnPreDraw // Check eventItemTitle value to avoid NPE
            val lastVisibleLineNumber: Int =
                eventItemTitle.layout.getLineForVertical(eventItemTitle.height + eventItemTitle.scrollY)
            if (eventItemTitle.height < eventItemTitle.layout.getLineBottom(lastVisibleLineNumber)) {
                // If line is cut off, set max line property
                val lineHeight = eventItemTitle.paint.fontMetrics.bottom - eventItemTitle.paint.fontMetrics.top
                val maxLines = eventItemTitle.height / lineHeight
                eventItemTitle.maxLines = maxLines.toInt()
                eventItemTitle.gravity = Gravity.CENTER_VERTICAL
                eventItemTitle.ellipsize
            }
        }

        val participationStatus = event.getParticipationStatus(userEmails)
        viewBackgroundStripedLayout.visibleOrGone(!event.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION)

        if (event.decryptionStatus == Event.DecryptionStatus.FAILURE) {
            decryptionErrorIcon.visibleOrGone(true)
            decryptionErrorView.visibleOrGone(true)
            eventItemTitle.visibleOrGone(false)
        } else {
            decryptionErrorIcon.visibleOrGone(false)
            decryptionErrorView.visibleOrGone(false)
        }

        viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))

        if (event.isInThePast(timeZoneId)) {
            eventItemTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_weak))
            ImageViewCompat.setImageTintList(
                decryptionErrorIcon,
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_weak))
            )

            if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
                viewMainSurface.setTint(ContextCompat.getColor(requireContext(), R.color.background_norm))
                viewBorder.setTint(ContextCompat.getColor(requireContext(), R.color.interaction_weak_pressed))
            } else if (participationStatus == ParticipationStatus.NEEDS_ACTION) {
                viewMainSurface.setTint(ContextCompat.getColor(requireContext(), R.color.background_norm))
                viewBorder.setTint(ContextCompat.getColor(requireContext(), R.color.interaction_weak_pressed))
                AndroidUtils.setStripedBackground(
                    viewBackgroundStriped,
                    requireContext(),
                    ContextCompat.getColor(requireContext(), R.color.shade_60)
                ) // striped background with 20% opacity for unanswered all day events
            } else {
                viewMainSurface.setTint(ContextCompat.getColor(requireContext(), R.color.background_secondary))
                viewBorder.setTint(ContextCompat.getColor(requireContext(), R.color.background_secondary))
                decryptionErrorView.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_norm))
                decryptionErrorView.alpha = 0.1f
            }
        } else {
            if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
                eventItemTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
                viewMainSurface.setTint(ContextCompat.getColor(requireContext(), R.color.background_norm))
                viewBorder.setTint(ContextCompat.getColor(requireContext(), R.color.interaction_weak_pressed))
            } else if (participationStatus == ParticipationStatus.NEEDS_ACTION) {
                eventItemTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
                viewMainSurface.setTint(ContextCompat.getColor(requireContext(), R.color.background_norm))
                viewBorder.setTint(ContextCompat.getColor(requireContext(), R.color.interaction_weak_pressed))
                AndroidUtils.setStripedBackground(
                    viewBackgroundStriped,
                    requireContext(),
                    Color.parseColor(event.calendar.color)
                ) // striped background with 20% opacity for unanswered all day events
            } else {
                viewMainSurface.setTint(Color.parseColor(event.calendar.color))
                viewBorder.setTint(Color.parseColor(event.calendar.color))
                eventItemTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_calendar_color))
                ImageViewCompat.setImageTintList(
                    decryptionErrorIcon,
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_on_calendar_color))
                )
                decryptionErrorView.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_on_calendar_color))
                decryptionErrorView.alpha = 0.2f
            }
        }

        if (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED) {
            eventItemTitle.paintFlags = eventItemTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            eventItemTitle.paintFlags = eventItemTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

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

        val dateFormat = SimpleDateFormat("EEE", DateTimeUtilsImpl.getLocaleForFormatting())
        all_day_header.text = dateFormat.format(Date.from(date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()))
        all_day_header_date.text = date?.dayOfMonth.toString()
        all_day_layout.visibleOrGone(true)
        all_day_header_date.visibleOrGone(true)

        agendaMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            value?.let {
                day_scroll_view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        day_scroll_view.viewTreeObserver.removeOnPreDrawListener(this)
                        doOnPreDraw(day_scroll_view, it)
                        return false
                    }
                })

                if (date == LocalDate.now(it)) {
                    all_day_header.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_norm))
                    all_day_header_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_norm))
                } else {
                    all_day_header.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    all_day_header_date.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
                }

                updateCurrentTimeIndicatorDelayed(it)
            }

            if (timeZoneId != null && timeFormatIs24Hour != null && userAddresses != null) {
                onEventsChange(timeZoneId!!, userAddresses!!)
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

            if (timeZoneId != null && timeFormatIs24Hour != null && userAddresses != null) {
                agendaMediator.value = Triple(timeZoneId!!, timeFormatIs24Hour!!, userAddresses!!)
            }
        }
        agendaMediator.observe(viewLifecycleOwner) {
            it?.let {
                setupItemMiniCalendarContent(it.first, it.second, it.third)
            }
        }

        calendarViewModel.dayViewScrollYPosition.observe(viewLifecycleOwner) {
            if (!this.isResumed && calendarViewModel.jumpToCurrentTime.value == false) {
                day_scroll_view.setOnScrollChangeListener(null)
                day_scroll_view.scrollY = it
                day_scroll_view.setOnScrollChangeListener(onScrollChangeListener)
            }
        }

        calendarViewModel.jumpToCurrentTime.observe(viewLifecycleOwner) { jumpToCurrentTime ->
            if (jumpToCurrentTime && preDrawDone && calendarViewModel.selectedDate.value == date) {
                lifecycleScope.launch {
                    calendarViewModel.jumpToCurrentTime.value = false
                    val timeZoneId = this@ItemCalendarDayFragment.timeZoneId ?: calendarViewModel.getCalendarUserSettingsPrimaryTimezone()
                    val currentTime = LocalTime.now(
                        if (timeZoneId != null) ZoneId.of(timeZoneId)
                        else ZoneId.systemDefault()
                    ).hour
                    val yPos = dayView.getHourTop(
                        if (currentTime > 0) currentTime - 1
                        else currentTime
                    )
                    day_scroll_view.setOnScrollChangeListener(null)
                    day_scroll_view.smoothScrollTo(0, yPos)
                    if (this@ItemCalendarDayFragment.isResumed) calendarViewModel.dayViewScrollYPosition.value = yPos
                    day_scroll_view.setOnScrollChangeListener(onScrollChangeListener)
                }
            }
        }
    }

    private fun doOnPreDraw(scrollView: View, zoneId: ZoneId? = null) {
        scrollView.setOnScrollChangeListener(null)
        val jumpToCurrentTime = calendarViewModel.jumpToCurrentTime.value
        if (jumpToCurrentTime == true) {
            // Set scrolling position to current time minus 1 hour
            calendarViewModel.jumpToCurrentTime.value = false
            lifecycleScope.launch {
                val timeZoneId =
                    if (zoneId != null) {
                        zoneId
                    } else {
                        val primaryTimeZone = calendarViewModel.getCalendarUserSettingsPrimaryTimezone()
                        if (primaryTimeZone != null) ZoneId.of(primaryTimeZone) else null
                    }
                timeZoneId?.let {
                    val currentTime = LocalTime.now(it).hour
                    val yPos = dayView.getHourTop(
                        if (currentTime > 0) currentTime - 1
                        else currentTime
                    )
                    scrollView.scrollY = yPos
                    if (this@ItemCalendarDayFragment.isResumed) calendarViewModel.dayViewScrollYPosition.value = yPos
                    updateCurrentTimeIndicatorDelayed(it)
                }
            }
        } else {
            // Use previous view scrolling position if it exists
            scrollView.scrollY = calendarViewModel.dayViewScrollYPosition.value ?: 0
        }
        scrollView.setOnScrollChangeListener(onScrollChangeListener)
        preDrawDone = true
    }

    private fun setupItemMiniCalendarContent(
        timeZoneId: String,
        timeFormatIs24Hour: Boolean,
        userAddresses: List<UserAddress>
    ) {
        val immutableDate = date ?: return

        if (day_view == null) return

        if (FeatureFlag.NEW_EVENT_DECRYPTION) {
            // TODO remove UserID livedata
            calendarViewModel.userId.observe(viewLifecycleOwner) { userId ->
                userId?.let {
                    getEvents(immutableDate, timeZoneId, userAddresses)
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
                immutableDate != selectedDate.plusDays(1)
            ) {
                logger.v("ItemCalendarDayFragment: events flow: remove observer for $immutableDate. Selected date is $selectedDate")
                eventsLiveData.removeObservers(viewLifecycleOwner)
            } else if (this::eventsLiveData.isInitialized && !eventsLiveData.hasActiveObservers() &&
                (immutableDate == selectedDate ||
                        immutableDate == selectedDate.minusDays(1) ||
                        immutableDate == selectedDate.plusDays(1))
            ) {
                logger.v("ItemCalendarDayFragment: events flow: recreate getEvents flow $immutableDate. Selected date is $selectedDate")
                getEvents(immutableDate, timeZoneId, userAddresses)
            }
        }
    }

    private fun getEvents(immutableDate: LocalDate, timeZoneId: String, userAddresses: List<UserAddress>) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            logger.v("ItemCalendarDayFragment: events flow: remove already existing observer for $immutableDate")
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        eventsLiveData = calendarViewModel.getEvents(immutableDate, immutableDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        if (this.isResumed) calendarViewModel.setLoading(true, position)
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {
                        allEvents = it.events
                        onEventsChange(timeZoneId, userAddresses)

                        val allDayEvents = it.events.filter { event -> !event.spansSingleDay(true, timeZoneId) }
                        val croppedList = allDayEvents.take(
                            if (allDayEvents.size > DAY_VIEW_ALL_DAY_MAX) DAY_VIEW_ALL_DAY_MAX - 1
                            else DAY_VIEW_ALL_DAY_MAX
                        )
                        val moreEvents =
                            if (allDayEvents.size > DAY_VIEW_ALL_DAY_MAX) allDayEvents.takeLast(allDayEvents.size - (DAY_VIEW_ALL_DAY_MAX - 1))
                            else listOf()

                        all_day_no_events.visibleOrGone(allDayEvents.isNullOrEmpty())
                        if (it.events.isNullOrEmpty()) all_day_no_events.text = getString(R.string.agenda_no_events)
                        else all_day_no_events.text = null
                        all_day_more_items_layout.removeAllViews()

                        val userEmails = userAddresses.map { userAddress -> userAddress.email }

                        /* Cropped list */
                        val allDayEventsCroppedListLayoutManager =
                            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
                        all_day_items_cropped_list.layoutManager = allDayEventsCroppedListLayoutManager
                        allDayEventCroppedListAdapter =
                            DayViewAllDayEventAdapter(userEmails, timeZoneId, immutableDate) { event ->
                                onEventClick(event)
                            }
                        all_day_items_cropped_list.adapter = allDayEventCroppedListAdapter
                        allDayEventCroppedListAdapter.submitList(croppedList)

                        /* Rest of the list */
                        val allDayEventsMoreListLayoutManager =
                            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
                        all_day_items_list.layoutManager = allDayEventsMoreListLayoutManager
                        allDayEventListAdapter =
                            DayViewAllDayEventAdapter(userEmails, timeZoneId, immutableDate) { event ->
                                onEventClick(event)
                            }
                        all_day_items_list.adapter = allDayEventListAdapter
                        allDayEventListAdapter.submitList(moreEvents)

                        all_day_more_collapse_button.setOnSingleClickListener { view ->
                            val duration = collapse(all_day_items_list).second
                            view?.run {
                                postDelayed({
                                    all_day_more_items_layout.visibleOrGone(true)
                                }, duration / 2)
                                postDelayed({
                                    all_day_more_collapse_button.visibleOrGone(false)
                                }, duration)
                            }
                        }

                        if (allDayEvents.size > DAY_VIEW_ALL_DAY_MAX) {
                            val eventView = layoutInflater.inflate(R.layout.item_day_view_event_all_day, dayView, false)

                            val title = (eventView.findViewById<View>(R.id.text_title) as TextView)
                            val titleParams = title.layoutParams
                            titleParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            title.layoutParams = titleParams
                            title.gravity = Gravity.CENTER_VERTICAL
                            title.text = getString(
                                R.string.day_view_all_day_more,
                                allDayEvents.size - (DAY_VIEW_ALL_DAY_MAX - 1)
                            )
                            title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))

                            (eventView.findViewById<View>(R.id.view_background).background as LayerDrawable).findDrawableByLayerId(
                                R.id.main_surface
                            ).setTint(
                                ContextCompat.getColor(requireContext(), R.color.interaction_weak)
                            )
                            (eventView.findViewById<View>(R.id.view_background).background as LayerDrawable).findDrawableByLayerId(
                                R.id.side_strip
                            ).setTint(
                                ContextCompat.getColor(requireContext(), R.color.interaction_weak)
                            )

                            // When an event is clicked, start a new draft event and show the edit event dialog
                            eventView.setOnClickListener { view ->
                                val duration = expand(all_day_items_list).second
                                view?.run {
                                    postDelayed({
                                        all_day_more_items_layout.visibleOrGone(false)
                                    }, duration / 2)
                                    postDelayed({
                                        all_day_more_collapse_button.visibleOrGone(true)
                                    }, duration)
                                }
                            }

                            val layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                resources.getDimensionPixelSize(R.dimen.all_day_item_height)
                            )
                            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.all_day_item_margin_end)
                            layoutParams.bottomMargin =
                                resources.getDimensionPixelSize(R.dimen.all_day_item_margin_bottom)
                            all_day_more_items_layout.addView(eventView, layoutParams)
                            if (all_day_items_list.isVisible.not()) all_day_more_items_layout.visibleOrGone(true)
                        } else {
                            all_day_items_list.visibleOrGone(false)
                            all_day_more_collapse_button.visibleOrGone(false)
                        }

                        if (all_day_items_list.isVisible) all_day_items_list.layoutParams =
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

                        calendarViewModel.setLoading(false, position)

                        all_day_more_items_layout.doOnPreDraw {
                            // This allow us to properly set the scroll position after changes have been made to the all day header
                            //  because the scroll view top position depends on the all day header height (top to bottom constraint)
                            day_scroll_view.scrollY = calendarViewModel.dayViewScrollYPosition.value ?: 0
                            day_scroll_view.setOnScrollChangeListener(onScrollChangeListener)
                        }

                        all_day_create_event_view.setOnSingleClickListener { view ->
                            if (allDayEvents.isNullOrEmpty()) {
                                requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                                    .navigate(
                                        Navigation.Deeplink.toEventCreate(
                                            immutableDate
                                        )
                                    )
                            }
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        // TODO Handle error for DayView event fetching
                        calendarViewModel.setLoading(false, position)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        calendarViewModel.setLoading(false, position)
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasObservers()) {
            logger.v("ItemCalendarDayFragment: events flow: remove observers in on destroy for $date")
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        dayView.removeEventViews()
    }
}
