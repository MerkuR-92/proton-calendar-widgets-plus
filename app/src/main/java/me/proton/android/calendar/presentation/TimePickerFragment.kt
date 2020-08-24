package me.proton.android.calendar.presentation

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import me.proton.android.calendar.R
import org.koin.core.KoinComponent


class TimePickerFragment : BaseDialogFragment(), KoinComponent {

    override val TAG = "TimePickerFragment"
    override val layoutResourceId = R.layout.dialog_time_picker

    override val navigateUp = false


//    override val actionMenuResourceId = R.menu.fragment_event_details

    override fun onMenuItemClicked(menuItem: MenuItem) { // TODO maybe move this logic to viewmodel, but it's just 1 line
//        when (menuItem.itemId) {
//            R.id.action_menu_edit -> findNavController().navigate((Navigation.Deeplink.toEventEdit(navigationArguments.eventId)))
//        }
        // TODO
    }

//    private val navigationArguments: EventDetailsFragmentArgs by navArgs()

//    private val calendarViewModel: CalendarViewModel by inject()
//    private val mainViewModel: MainViewModel by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)




    }


}