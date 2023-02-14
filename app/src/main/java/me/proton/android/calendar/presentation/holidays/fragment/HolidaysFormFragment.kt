package me.proton.android.calendar.presentation.holidays.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.presentation.calendar.fragment.EventFormFragmentArgs
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent

@AndroidEntryPoint
class HolidaysFormFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "HolidaysFormFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_holidays_form

    override val navigateUp = true
    override val isScrollable = false

    private val navigationArguments: HolidaysFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        // TODO Change title depending on form / picker
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text =
            if (navigationArguments.calendarId != null) getString(R.string.calendar_form_update_title)
            else getString(R.string.holidays_calendar_title)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
