package me.proton.android.calendar.presentation.importAssistant.fragment

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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_import_button
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_count
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_customize_import_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_email
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_list
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.api.ExternalCalendarEntity
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.adapter.ImportCalendarMappingListAdapter
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent

@AndroidEntryPoint
class ImportAssistantFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant

    override val navigateUp = false
    override val isScrollable = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    private val navigationArguments: ImportAssistantFragmentArgs by navArgs()

    private lateinit var importCalendarMappingListAdapter: ImportCalendarMappingListAdapter

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

        fragment_import_assistant_summary_customize_import_layout.setOnSingleClickListener {
            // TODO Smooth scroll through the view
        }

        fragment_import_assistant_import_button.setOnSingleClickListener {
            // TODO
            view.displaySnackBar("Import")
            val calendarsToImport = importCalendarMappingListAdapter.currentList
            lifecycleScope.launch {
                importAssistantViewModel.startImport(
                    customCalendarMapping = true, // TODO HANDLE THIS FIELD
                    importCalendarMappingList = calendarsToImport.filter { it.importCalendar }
                )
            }
        }

        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {
                // Display loader
                fragment_import_assistant_loader_layout.visibleOrGone(true)

                // Create importer and fetch external calendars
                val handleGoogleSignInRedirectResult = importAssistantViewModel.handleGoogleSignInRedirect(userId, navigationArguments.code)
                val externalCalendarList = handleGoogleSignInRedirectResult?.second
                val sourceEmail = handleGoogleSignInRedirectResult?.first

                // Hide loader
                fragment_import_assistant_loader_layout.visibleOrGone(false)

                if (externalCalendarList != null && sourceEmail != null) {
                    // Display summary header
                    fragment_import_assistant_summary_layout.visibleOrGone(true)
                    fragment_import_assistant_summary_count.text = getString(
                        R.string.import_assistant_summary_count,
                        externalCalendarList.size,
                        externalCalendarList.size
                    )
                    fragment_import_assistant_summary_email.text = sourceEmail
                    fragment_import_assistant_import_button.text = resources.getQuantityString(
                        R.plurals.import_assistant_import_button,
                        externalCalendarList.size,
                        externalCalendarList.size
                    )
                    fragment_import_assistant_import_button.visibleOrGone(true)

                    // Display the list
                    displayExternalCalendarList(sourceEmail, externalCalendarList)
                } else {
                    // TODO Handle error
                    view.displaySnackBar(getString(R.string.import_assistant_error))
                }
            } else {
                view.displaySnackBar(getString(R.string.snack_network_error))
            }
        }
    }

    private fun displayExternalCalendarList(sourceEmail: String, externalCalendarList: List<ExternalCalendarEntity>) {
        val externalCalendarListView = fragment_import_assistant_summary_list
        val externalCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        externalCalendarListView.layoutManager = externalCalendarLayoutManager
        importCalendarMappingListAdapter = ImportCalendarMappingListAdapter { importCalendarMapping ->
            // On external calendar click
            val currentList = importCalendarMappingListAdapter.currentList
            val indexOfItem = currentList.indexOf(importCalendarMapping)
            currentList[indexOfItem].importCalendar = !importCalendarMapping.importCalendar
            importCalendarMappingListAdapter.submitList(currentList)
            importCalendarMappingListAdapter.notifyItemChanged(indexOfItem)

            val importedCalendarCount = currentList.count { it.importCalendar }
            fragment_import_assistant_summary_count.text = getString(
                R.string.import_assistant_summary_count,
                importedCalendarCount,
                externalCalendarList.size
            )
            fragment_import_assistant_import_button.text = resources.getQuantityString(
                R.plurals.import_assistant_import_button,
                importedCalendarCount,
                importedCalendarCount
            )
        }
        externalCalendarListView.adapter = importCalendarMappingListAdapter

        lifecycleScope.launch {
            val defaultUserEmail = importAssistantViewModel.getDefaultUserEmail() ?: run {
                // TODO HANDLE ERROR
                return@launch
            }
            val importCalendarMappingList = arrayListOf<ImportCalendarMapping>()
            externalCalendarList.forEach {
                importCalendarMappingList.add(
                    ImportCalendarMapping(
                        importCalendar = true, // Set to true by default
                        sourceId = it.id,
                        sourceName = it.source,
                        sourceEmail = sourceEmail,
                        createDestinationCalendar = true,
                        destinationId = null,
                        destinationName = it.source,
                        destinationEmail = defaultUserEmail,
                        destinationColor = resources.getIntArray(R.array.accent_colors_base).random()
                    )
                )
            }

            importCalendarMappingListAdapter.submitList(importCalendarMappingList)
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
