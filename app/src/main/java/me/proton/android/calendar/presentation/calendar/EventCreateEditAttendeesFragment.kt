package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.view.*
import androidx.navigation.fragment.navArgs
import me.proton.android.calendar.R
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.core.KoinComponent
import org.koin.core.inject


class EventCreateEditAttendeesFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventCreateEditAttendeesFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_create_edit_attendees
    //    override val actionMenuResourceId = R.menu.fragment_event_create_edit

    override fun onMenuItemClicked(menuItem: MenuItem) {
//         if (menuItem.itemId == R.id.action_menu_save) {
             // TODO
//         }
    }

    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }

}