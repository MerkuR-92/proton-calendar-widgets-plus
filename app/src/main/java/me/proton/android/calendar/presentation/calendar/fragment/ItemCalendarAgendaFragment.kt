package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import biweekly.ICalendar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sortForAgendaView
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.calendar.adapter.EventAdapter
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.core.user.domain.entity.UserAddress
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate
import java.util.*


class ItemCalendarAgendaFragment() : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var position: Int? = null
    private var date: LocalDate? = null

    private val fakeHeaderEvent = Event.from("", Calendar("", "", "", 1, true, 0), ICalendar())

    private var timeZoneId: String? = null
    private var timeFormatIs24Hour: Boolean? = null

    private lateinit var eventsLiveData: LiveData<CalendarsRepository.GetEventsResult<Event>>
    private var selectedDate: LocalDate? = null

    companion object {
        fun newInstance(position: Int, date: LocalDate) : ItemCalendarAgendaFragment {
            return ItemCalendarAgendaFragment().apply {
                arguments = Bundle().apply {
                    putInt(POSITION_ARG, position)
                    putSerializable(DATE_ARG, date)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(POSITION_ARG)
            date = it.getSerializable(DATE_ARG) as? LocalDate?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.item_calendar_agenda_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val agendaMediator = MediatorLiveData<Pair<String, Boolean>>()
        agendaMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && timeFormatIs24Hour != null) {
                agendaMediator.value = Pair(timeZoneId!!, timeFormatIs24Hour!!)
            }
        }
        agendaMediator.addSource(calendarViewModel.timeFormat) { value ->
            timeFormatIs24Hour = value?.let { calendarViewModel.timeFormatIs24Hour(it, requireContext()) }

            if (timeZoneId != null && timeFormatIs24Hour != null) {
                agendaMediator.value = Pair(timeZoneId!!, timeFormatIs24Hour!!)
            }
        }
        agendaMediator.observe(viewLifecycleOwner) {
            it?.let {
                setupItemMiniCalendarContent(it.first, it.second)
            }
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, timeFormatIs24Hour: Boolean) {
        val immutableDate = date ?: return

        // Check if recycler view is not null because of the delay
        if (rv_agenda == null) return

        rv_agenda.apply {
            layoutManager = LinearLayoutManager(this@ItemCalendarAgendaFragment.context)

            adapter = EventAdapter(timeZoneId, timeFormatIs24Hour, immutableDate) {
                if (it.decryptionStatus == Event.DecryptionStatus.SUCCESS) {
                    findNavController().navigate(
                        Navigation.Deeplink.toEventDetails(
                            it.id,
                            it.occurrence?.occurrenceNumber ?: 0
                        )
                    )
                } else {
                    val confirmationMessage =
                        if (it.isRecurring()) R.string.event_decryption_error_dialog_confirmation_recurring
                        else R.string.event_decryption_error_dialog_confirmation
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.event_decryption_error_dialog_title)
                        .setMessage(R.string.event_decryption_error_dialog_message)
                        .setPositiveButton(confirmationMessage) { _, _ ->
                            lifecycleScope.launch { // TODO
                                val deleteResult = withContext(Dispatchers.Default) {
                                    calendarViewModel.handleDeleteEvent(
                                        it.id,
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
            (this.adapter as? EventAdapter)?.submitList(listOf(fakeHeaderEvent))
        }

        // TODO remove UserID livedata
        calendarViewModel.userId.observe(viewLifecycleOwner) { userId ->
            userId?.let {
                getEvents(immutableDate, timeZoneId)
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
                logger.v("events flow: remove observer for $immutableDate. Selected date is $selectedDate")
                calendarViewModel.setLoading(false, position)
                eventsLiveData.removeObservers(viewLifecycleOwner)
            } else if (this::eventsLiveData.isInitialized && !eventsLiveData.hasActiveObservers() &&
                (immutableDate == selectedDate ||
                        immutableDate == selectedDate.minusDays(1) ||
                        immutableDate == selectedDate.plusDays(1))) {
                logger.v("events flow: recreate getEvents flow $immutableDate. Selected date is $selectedDate")
                getEvents(immutableDate, timeZoneId)
            }
        }
    }

    private fun getEvents(immutableDate: LocalDate, timeZoneId: String) {
        if (this::eventsLiveData.isInitialized && eventsLiveData.hasActiveObservers()) {
            logger.v("events flow: remove already existing observer for $immutableDate")
            calendarViewModel.setLoading(false, position)
            eventsLiveData.removeObservers(viewLifecycleOwner)
        }
        eventsLiveData = calendarViewModel.getEvents(immutableDate, immutableDate, timeZoneId, this.lifecycle)
        eventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

            eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        val currentList = (rv_agenda.adapter as? EventAdapter)?.currentList
                        if (currentList == null || currentList.size <= 1 && this.isResumed) {
                            calendarViewModel.setLoading(true, position)
                            list_view_status.visibleOrInvisible(true)
                            list_view_status.text = resources.getString(R.string.agenda_loading_events)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        // Sort the events
                        val sortedEvents = it.events.sortForAgendaView(timeZoneId)

                        if (immutableDate == calendarViewModel.selectedDate.value) {
                            val partDayEvents = it.events.filter {
                                !it.isAllDay() && it.spansSingleDay(true, timeZoneId) // Multi day events are displayed in the day view header
                            }
                            // Save the time of the first event of the day so that we can easily adjust the day view scroll position if view mode changes
                            calendarViewModel.firstEventOfTheDayTime =
                                if (partDayEvents.isNotEmpty()) {
                                    Collections.min(
                                        partDayEvents.map {
                                            it.getOccurrenceStart(timeZoneId).toLocalTime()
                                        }
                                    )
                                } else null
                        }

                        if (sortedEvents.isEmpty()) {
                            list_view_status.visibleOrInvisible(true)
                            list_view_status.text = resources.getString(R.string.agenda_no_events)
                        } else {
                            list_view_status.visibleOrInvisible(false)
                        }

                        lifecycleScope.launch {
                            val userAddresses = calendarViewModel.getUserAddresses()
                            userAddresses?.let {
                                (rv_agenda.adapter as? EventAdapter)?.setUserEmails(it.map { it.email })
                            }
                            (rv_agenda.adapter as? EventAdapter)?.submitList(
                                listOf(fakeHeaderEvent).plus(sortedEvents)
                            )
                            calendarViewModel.setLoading(false, position)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        list_view_status.visibleOrInvisible(true)
                        list_view_status.text =
                            resources.getString(R.string.agenda_loading_events_error)

                        (rv_agenda.adapter as? EventAdapter)?.submitList(
                            listOf(fakeHeaderEvent)
                        )
                        calendarViewModel.setLoading(false, position)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        calendarViewModel.setLoading(false, position)
    }
}
