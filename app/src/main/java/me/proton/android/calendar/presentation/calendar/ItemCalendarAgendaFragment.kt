package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import kotlinx.android.synthetic.main.item_calendar_agenda_fragment.*
import me.proton.android.calendar.domain.ValueStoreProvider
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate


class ItemCalendarAgendaFragment(val calendarViewModel: CalendarViewModel, val position: Int, val date: LocalDate) : Fragment(), KoinComponent {

//    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val valueStoreProvider: ValueStoreProvider by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val rootView = inflater.inflate(R.layout.item_calendar_agenda_fragment, container, false)
        rootView.findViewById<TextView>(R.id.text).text = "$date"

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TimberLogger.d("onViewCreated: $date")

        recyclerView.apply {
            //            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ItemCalendarAgendaFragment.context)
            adapter = EventAdapter {
                findNavController().navigate(Navigation.Deeplink.toEventDetails(it.id, it.occurrence?.occurrenceNumber ?: 0))
            }
            recyclerView.addItemDecoration(
                DividerItemDecoration(
                    this@ItemCalendarAgendaFragment.context,
                    LinearLayoutManager.VERTICAL
                )
            )

        }

        try {
            val TODOvalueStore = valueStoreProvider.provideValueStore("TODO LOGIN")
//            val valueStore = valueStoreProvider.provideValueStore(TODOvalueStore.getString("USERID")!!)
            val calendarId = TODOvalueStore.getString("DEFAULT CALENDAR ID")

//            TimberLogger.d("binding live data for events from calendar $calendarId")
            calendarViewModel.events(date).observe(viewLifecycleOwner, Observer {
//                TimberLogger.d("observed events arrived: $it")
                (recyclerView.adapter as? EventAdapter)?.submitList(it)
            })

        } catch (e: Exception) {}

    }
}
