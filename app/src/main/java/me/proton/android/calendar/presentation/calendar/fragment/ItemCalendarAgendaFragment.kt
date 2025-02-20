package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sortUiEventsForAgendaView
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayEventDecryptionErrorDialog
import me.proton.android.calendar.databinding.ItemCalendarAgendaFragmentBinding
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.calendar.adapter.EventAdapter
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class ItemCalendarAgendaFragment: Fragment() {

    @Inject
    lateinit var logger: Logger

    private var _binding: ItemCalendarAgendaFragmentBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val calendarViewModel: CalendarViewModel by activityViewModels()

    private var position: Int? = null
    private var date: LocalDate? = null

    private val fakeHeaderEvent = UiEvent()

    private var timeZoneId: String? = null
    private var timeFormatIs24Hour: Boolean? = null

    private lateinit var uiEventsLiveData: LiveData<CalendarsRepository.GetEventsResult<UiEvent>>
    private var selectedDate: LocalDate? = null

    private lateinit var eventsListLayoutAdapter: EventAdapter

    private var firstEventOfTheDayTime: LocalTime? = null

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
        _binding = ItemCalendarAgendaFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val eventsListLayoutManager =
            LinearLayoutManager(this@ItemCalendarAgendaFragment.context)
        binding.rvAgenda.layoutManager = eventsListLayoutManager
        eventsListLayoutAdapter =
            EventAdapter {
                if (it.decryptionStatus == Event.DecryptionStatus.Success) {
                    findNavController().navigate(
                        Navigation.Deeplink.toEventDetails(
                            it.id,
                            it.occurrenceNumber ?: 0
                        )
                    )
                } else {
                    lifecycleScope.launch {
                        requireContext().displayEventDecryptionErrorDialog(
                            calendarViewModel.allowDeleteEvent(it.calendarId),
                            it.isRecurring
                        ) { _, _ ->
                            lifecycleScope.launch {
                                val deleteResult = withContext(Dispatchers.Default) {
                                    calendarViewModel.handleDeleteEvent(
                                        it.id,
                                        it.calendarId,
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
                    }
                }
            }
        binding.rvAgenda.adapter = eventsListLayoutAdapter

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

        eventsListLayoutAdapter.setDate(immutableDate)
        eventsListLayoutAdapter.setTimeZoneId(timeZoneId)
        eventsListLayoutAdapter.setTimeFormatIs24Hour(timeFormatIs24Hour)

        // Check if recycler view is not null because of the delay
        if (binding.rvAgenda == null) return

        eventsListLayoutAdapter.submitList(listOf(fakeHeaderEvent))

        lifecycleScope.launch {
            getEvents(immutableDate, timeZoneId)
        }

        calendarViewModel.selectedDateTime.distinctUntilChanged().observe(viewLifecycleOwner) { selectedDateTime ->
            val selectedDate = selectedDateTime.first
            if (this.selectedDate == null) {
                // View pager creates fragment for selectedDate + 2 when you start swiping, so comparing immutableDate
                //  and selectedDate would make us remove the observer for the flow we just created on start.
                // To avoid this case we skip the first observed value of selectedDate.
                this.selectedDate = selectedDate
                return@observe
            }
            this.selectedDate = selectedDate
            if (selectedDate == immutableDate && firstEventOfTheDayTime != null) {
                // Set the time of the first event of the day so that we can easily adjust the day view scroll position if view mode changes
                calendarViewModel.firstEventOfTheDayTime = firstEventOfTheDayTime
            }
            if (this::uiEventsLiveData.isInitialized && uiEventsLiveData.hasActiveObservers() &&
                immutableDate != selectedDate &&
                immutableDate != selectedDate.minusDays(1) &&
                immutableDate != selectedDate.plusDays(1)) {
                logger.v("events flow: remove observer for $immutableDate. Selected date is $selectedDate")
                calendarViewModel.setLoading(false, position)
                uiEventsLiveData.removeObservers(viewLifecycleOwner)
            } else if (this::uiEventsLiveData.isInitialized && !uiEventsLiveData.hasActiveObservers() &&
                (immutableDate == selectedDate ||
                        immutableDate == selectedDate.minusDays(1) ||
                        immutableDate == selectedDate.plusDays(1))) {
                logger.v("events flow: recreate getEvents flow $immutableDate. Selected date is $selectedDate")
                lifecycleScope.launch {
                    getEvents(immutableDate, timeZoneId)
                }
            }
        }
    }

    private suspend fun getEvents(immutableDate: LocalDate, timeZoneId: String) {
        if (this::uiEventsLiveData.isInitialized && uiEventsLiveData.hasActiveObservers()) {
            logger.v("events flow: remove already existing observer for $immutableDate")
            calendarViewModel.setLoading(false, position)
            uiEventsLiveData.removeObservers(viewLifecycleOwner)
        }

        uiEventsLiveData = calendarViewModel.getUiEventsLookup(immutableDate, immutableDate, timeZoneId, this.lifecycle)
        uiEventsLiveData.observe(viewLifecycleOwner) { eventsResult ->

        eventsResult?.let {
                when (it) {
                    CalendarsRepository.GetEventsResult.InProgress -> {
                        val currentList = eventsListLayoutAdapter.currentList
                        if (currentList.size <= 1 && this.isResumed) {
                            calendarViewModel.setLoading(true, position)
                            binding.listViewStatus.visibleOrInvisible(true)
                            binding.listViewStatus.text = resources.getString(R.string.agenda_loading_events)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Success -> {

                        // Sort the events
                        val sortedEvents = it.events.sortUiEventsForAgendaView(timeZoneId)

                        val partDayEvents = it.events.filter {
                            !it.isAllDay && it.spansSingleDay(true) // Multi day events are displayed in the day view header
                        }
                        // Save the time of the first event of the day so that we can easily adjust the day view scroll position if view mode changes
                        firstEventOfTheDayTime =
                            if (partDayEvents.isNotEmpty()) {
                                Collections.min(
                                    partDayEvents.map {
                                        it.dateStart.withZoneSameLocal(ZoneId.of(timeZoneId)).toLocalTime()
                                    }
                                )
                            } else null

                        if (sortedEvents.isEmpty()) {
                            binding.listViewStatus.visibleOrInvisible(true)
                            binding.listViewStatus.text = resources.getString(R.string.agenda_no_events)
                        } else {
                            binding.listViewStatus.visibleOrInvisible(false)
                        }

                        lifecycleScope.launch {
                            val userAddresses = calendarViewModel.getUserAddresses()
                            userAddresses?.let {
                                eventsListLayoutAdapter.setUserEmails(it.map { it.email })
                            }
                            eventsListLayoutAdapter.submitList(
                                listOf(fakeHeaderEvent).plus(sortedEvents)
                            )
                            calendarViewModel.setLoading(false, position)
                        }
                    }
                    is CalendarsRepository.GetEventsResult.Exception -> {
                        binding.listViewStatus.visibleOrInvisible(true)
                        binding.listViewStatus.text =
                            resources.getString(R.string.agenda_loading_events_error)

                        eventsListLayoutAdapter.submitList(
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
        _binding = null
    }
}
