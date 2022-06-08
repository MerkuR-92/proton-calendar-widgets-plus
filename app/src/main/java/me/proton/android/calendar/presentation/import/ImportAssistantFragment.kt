package me.proton.android.calendar.presentation.import

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_count
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_email
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_list
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.api.ExternalCalendarEntity
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.import.adapter.ExternalCalendarListAdapter
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent

class ImportAssistantFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant

    override val navigateUp = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    private val navigationArguments: ImportAssistantFragmentArgs by navArgs()

    private lateinit var externalCalendarListAdapter: ExternalCalendarListAdapter

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = "" // No Title
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
        }

        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {
                // Display loader
                fragment_import_assistant_loader_layout.visibleOrGone(true)

                // Create importer and fetch external calendars
                val handleGoogleSignInRedirectResult = mainViewModel.handleGoogleSignInRedirect(userId, navigationArguments.code)
                val externalCalendarList = handleGoogleSignInRedirectResult?.second
                val accountEmail = handleGoogleSignInRedirectResult?.first

                // Hide loader
                fragment_import_assistant_loader_layout.visibleOrGone(false)

                // Display summary header
                fragment_import_assistant_summary_layout.visibleOrGone(true)
                fragment_import_assistant_summary_count.text = getString(
                    R.string.import_assistant_summary_count,
                    externalCalendarList?.size,
                    externalCalendarList?.size
                )
                fragment_import_assistant_summary_email.text = accountEmail

                if (externalCalendarList != null && accountEmail != null) {
                    displayExternalCalendarList(accountEmail, externalCalendarList)
                } else {
                    // TODO Handle error
                }
            } else {
                view.displaySnackBar(getString(R.string.snack_network_error))
            }
        }
    }

    private fun displayExternalCalendarList(accountEmail: String, externalCalendarList: List<ExternalCalendarEntity>) {
        val externalCalendarListView = fragment_import_assistant_summary_list
        val externalCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        externalCalendarListView.layoutManager = externalCalendarLayoutManager
        externalCalendarListAdapter = ExternalCalendarListAdapter(accountEmail) { externalCalendarEntity ->
            // On external calendar click
        }
        externalCalendarListView.adapter = externalCalendarListAdapter
        externalCalendarListAdapter.submitList(externalCalendarList)
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
