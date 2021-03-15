package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.displaySnackBar
import me.proton.android.calendar.common.visibleOrInvisible
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.MainActivity
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

        calendarViewModel.userAddresses.observe(viewLifecycleOwner) { userAddresses ->
            userAddresses ?: return@observe

            if (userAddresses.firstOrNull { it.displayName == null } != null) {
                // Refresh Addresses for user to fetch displayName values
                calendarViewModel.refreshAddressesFromServer()
            }

            val timeFormatIs24Hour = calendarViewModel.timeFormatIs24Hour(requireContext())
            val zoneId = calendarViewModel.timeZoneId.value ?: return@observe
            setupItemMiniCalendarContent(zoneId.id, timeFormatIs24Hour, userAddresses)
        }

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            zoneId ?: return@observe
            val timeFormatIs24Hour = calendarViewModel.timeFormatIs24Hour(requireContext())
            val userAddresses = calendarViewModel.userAddresses.value ?: return@observe
            setupItemMiniCalendarContent(zoneId.id, timeFormatIs24Hour, userAddresses)
        }

        calendarViewModel.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
            timeFormat ?: return@observe
            val zoneId = calendarViewModel.timeZoneId.value ?: return@observe
            val userAddresses = calendarViewModel.userAddresses.value ?: return@observe
            setupItemMiniCalendarContent(zoneId.id, calendarViewModel.timeFormatIs24Hour(requireContext()), userAddresses)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, timeFormatIs24Hour: Boolean, userAddresses: List<Address>) {
        val immutableDate = date ?: return
        logger.d("onViewCreated: $immutableDate")

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
