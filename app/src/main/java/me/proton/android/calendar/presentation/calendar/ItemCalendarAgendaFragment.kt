package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.MediatorLiveData
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
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate


class ItemCalendarAgendaFragment() : Fragment(), KoinComponent {

    private val calendarViewModel: CalendarViewModel by sharedViewModel()
    private val logger: Logger by inject()

    private var position: Int? = null
    private var date: LocalDate? = null

    private val fakeHeaderEvent = Event("", Calendar("", "", "", 1, true), ICalendar())

    private var timeZoneId: String? = null
    private var timeFormatIs24Hour: Boolean? = null
    private var userAddresses: List<Address>? = null
    private val agendaMediator = MediatorLiveData<Triple<String, Boolean, List<Address>>>()

    companion object {
        fun newInstance(position: Int, date: LocalDate) : ItemCalendarAgendaFragment{
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

        agendaMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && timeFormatIs24Hour != null && userAddresses != null) {
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
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, timeFormatIs24Hour: Boolean, userAddresses: List<Address>) {
        val immutableDate = date ?: return
        logger.v("setupItemMiniCalendarContent: $immutableDate")

        rv_agenda.apply {
            layoutManager = LinearLayoutManager(this@ItemCalendarAgendaFragment.context)

            adapter = EventAdapter(timeZoneId, timeFormatIs24Hour, immutableDate, userAddresses.map { it.email }) {
                if (it.decryptionStatus == Event.DecryptionStatus.SUCCESS) {
                    findNavController().navigate(
                        Navigation.Deeplink.toEventDetails(
                            it.id,
                            it.occurrence?.occurrenceNumber ?: 0
                        )
                    )
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.event_decryption_error_dialog_title)
                        .setMessage(R.string.event_decryption_error_dialog_message)
                        .setPositiveButton(R.string.event_decryption_error_dialog_confirmation) { _, _ ->
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

        if (FeatureFlag.NEW_EVENT_DECRYPTION) {

            // TODO remove UserID livedata
            calendarViewModel.userId.observe(viewLifecycleOwner) { userId ->

                userId?.let {
                    calendarViewModel.getEvents(immutableDate, immutableDate, timeZoneId)
                        .observe(viewLifecycleOwner) { eventsResult ->

                            //logger.e("got events for $immutableDate: $eventsResult")

                            eventsResult?.let {
                                when (it) {
                                    CalendarsRepository.GetEventsResult.InProgress -> {
                                        list_view_status.visibleOrInvisible(true)
                                        list_view_status.text = resources.getString(R.string.agenda_loading_events)
                                    }
                                    is CalendarsRepository.GetEventsResult.Success -> {

                                        if (it.events.isEmpty()) {
                                            list_view_status.visibleOrInvisible(true)
                                            list_view_status.text = resources.getString(R.string.agenda_no_events)
                                        } else {
                                            list_view_status.visibleOrInvisible(false)
                                        }
                                        (rv_agenda.adapter as? EventAdapter)?.submitList(
                                            listOf(fakeHeaderEvent).plus(it.events.sortForAgendaView(timeZoneId))
                                        )

                                    }
                                    is CalendarsRepository.GetEventsResult.Exception -> {
                                        list_view_status.visibleOrInvisible(true)
                                        list_view_status.text =
                                            resources.getString(R.string.agenda_loading_events_error)

                                        (rv_agenda.adapter as? EventAdapter)?.submitList(
                                            listOf(fakeHeaderEvent)
                                        )
                                    }
                                }

                            }

                        }
                }

            }

        } else {

            calendarViewModel.eventsLiveData(immutableDate, immutableDate, timeZoneId).observe(viewLifecycleOwner) {
                logger.d("observed events arrived in LIVE DATA, item agenda fragment: $immutableDate -> ${it?.size}")

                if (it == null) {
                    list_view_status.visibleOrInvisible(true)
                    list_view_status.text = resources.getString(R.string.agenda_loading_events)
                } else if (it.isEmpty()) {
                    list_view_status.visibleOrInvisible(true)
                    list_view_status.text = resources.getString(R.string.agenda_no_events)
                } else {
                    list_view_status.visibleOrInvisible(false)
                }
                (rv_agenda.adapter as? EventAdapter)?.submitList(
                    listOf(fakeHeaderEvent).plus(it ?: emptyList())
                )
            }

        }

    }
}
