package me.proton.android.calendar.presentation.importAssistant.fragment

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
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent

@AndroidEntryPoint
class ImportAssistantStatusFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantStatusFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant_status

    override val navigateUp = true
    override val isScrollable = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by activityViewModels()
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
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = getString(R.string.import_assistant_previous_import)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
