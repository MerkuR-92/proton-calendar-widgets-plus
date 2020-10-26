package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import biweekly.ICalendar
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.visibleOrInvisible
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.MainActivity
import org.koin.core.KoinComponent
import java.time.LocalDate


class ItemCalendarAgendaFragment(
    val calendarViewModel: CalendarViewModel,
    val position: Int,
    val date: LocalDate
) : Fragment(), KoinComponent {
    private val fakeHeaderEvent = Event("", Calendar("", "", "", true, true), ICalendar())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.item_calendar_agenda_fragment, container, false)

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TimberLogger.d("onViewCreated: $date")

        rv_agenda.apply {
            //            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ItemCalendarAgendaFragment.context)
            // TODO: Use ViewModel to get userEmail once we have proper user management
            adapter = EventAdapter(calendarViewModel.timeZoneId.id, calendarViewModel.timeFormatIs24Hour, date, (requireActivity() as MainActivity).getUserEmail()) {
                findNavController().navigate(
                    Navigation.Deeplink.toEventDetails(
                        it.id,
                        it.occurrence?.occurrenceNumber ?: 0
                    )
                )
            }
            (this.adapter as? EventAdapter)?.submitList(listOf(fakeHeaderEvent))
        }

        list_view_status.visibleOrInvisible(true)
        list_view_status.text = resources.getString(R.string.agenda_loading_events)

        calendarViewModel.eventsLiveData(date, date).observe(viewLifecycleOwner) {
            TimberLogger.d("xxx observed events arrived in LIVE DATA, item agenda fragment: $date -> ${it.size}")

            if (it.isEmpty()) {
                list_view_status.visibleOrInvisible(true)
                list_view_status.text = resources.getString(R.string.agenda_no_events)
            } else {
                list_view_status.visibleOrInvisible(false)
            }
            (rv_agenda.adapter as? EventAdapter)?.submitList(
                listOf(fakeHeaderEvent).plus(it)
            )
        }
    }
}
