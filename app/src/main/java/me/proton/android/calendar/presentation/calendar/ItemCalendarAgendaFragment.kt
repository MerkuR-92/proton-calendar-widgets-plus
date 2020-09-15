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
import kotlinx.android.synthetic.main.fragment_calendar.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate


class ItemCalendarAgendaFragment(val calendarViewModel: CalendarViewModel, val position: Int, val date: LocalDate) : Fragment(), KoinComponent {

//    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val valueStoreProvider: ValueStoreProvider by inject()

    private val fakeHeaderEvent = Event("", Calendar("", "", "", true), ICalendar())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.item_calendar_agenda_fragment, container, false)
        //rootView.findViewById<TextView>(R.id.text_date_header).text = "${date.format(showDayOfWeek = true)}"

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TimberLogger.d("onViewCreated: $date")

        rv_agenda.apply {
            //            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ItemCalendarAgendaFragment.context)
            adapter = EventAdapter(calendarViewModel.timeZoneId.id, date) {
                findNavController().navigate(Navigation.Deeplink.toEventDetails(it.id, it.occurrence?.occurrenceNumber ?: 0))
            }

            /*rv_agenda.addItemDecoration(
                DividerItemDecoration(
                    this@ItemCalendarAgendaFragment.context,
                    LinearLayoutManager.VERTICAL
                )
            )*/
            (rv_agenda.adapter as? EventAdapter)?.submitList(listOf(fakeHeaderEvent))
        }

        try {
//            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
//            val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

//            TimberLogger.d("binding live data for events from calendar $calendarId")
//            calendarViewModel.eventsLiveData(date).observe(viewLifecycleOwner, Observer {
//                TimberLogger.d("observed events arrived: $it")
//                (recyclerView.adapter as? EventAdapter)?.submitList(it)
//            })



            list_view_status.visibleOrInvisible(true)
            list_view_status.text = resources.getString(R.string.agenda_loading_events)

            lifecycleScope.launch(Dispatchers.Default) {

                TimberLogger.d("requesting prefetch for date: $date timezone: ${calendarViewModel.timeZoneId.id}")
                launch {
                    calendarViewModel.prefetchEvents(date, date, calendarViewModel.timeZoneId.id)
                }

                calendarViewModel.eventsFlow(date).collect {
                    TimberLogger.d("observed events arrived in flow, item agenda fragment: ${it.size}")

                    withContext(Dispatchers.Main) {
                        if (it.isEmpty()) {
                            list_view_status.visibleOrInvisible(true)
                            list_view_status.text = resources.getString(R.string.agenda_no_events)
                        } else {
                            list_view_status.visibleOrInvisible(false)
                        }
                        (rv_agenda.adapter as? EventAdapter)?.submitList(listOf(fakeHeaderEvent).plus(it))
                    }
                }
            }

        } catch (e: Exception) {}

    }
}
