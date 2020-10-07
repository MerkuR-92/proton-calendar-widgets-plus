package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import biweekly.ICalendar
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.visibleOrInvisible
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate


class ItemCalendarAgendaFragment(
    val calendarViewModel: CalendarViewModel,
    val position: Int,
    val date: LocalDate
) : Fragment(), KoinComponent {

//    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val valueStoreProvider: ValueStoreProvider by inject()

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
            adapter = EventAdapter(calendarViewModel.timeZoneId.id, date) {
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

                //withContext(Dispatchers.Main) {
                    if (it.isEmpty()) {
                        list_view_status.visibleOrInvisible(true)
                        list_view_status.text = resources.getString(R.string.agenda_no_events)
                    } else {
                        list_view_status.visibleOrInvisible(false)
                    }
                    (rv_agenda.adapter as? EventAdapter)?.submitList(
                        listOf(fakeHeaderEvent).plus(
                            it
                        )
                    )
                //}
            }




    }
}
