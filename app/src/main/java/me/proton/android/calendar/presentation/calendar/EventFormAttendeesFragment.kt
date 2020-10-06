package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import androidx.navigation.fragment.navArgs
import me.proton.android.calendar.R
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.core.KoinComponent
import org.koin.core.inject


class EventFormAttendeesFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventFormAttendeesFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_attendees

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }

}