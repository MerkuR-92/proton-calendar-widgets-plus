package me.proton.android.calendar.presentation.importAssistant.fragment

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_close_button
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_illustration
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_import_button
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_in_progress_description
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_in_progress_illustration
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_in_progress_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_in_progress_redirect
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_description
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_title
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_scroll_view
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_count
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_create_details
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_customize_import_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_email
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_error
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_error_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_header_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_list
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_merge_details
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.MAX_CALENDAR_FREE
import me.proton.android.calendar.common.MAX_CALENDAR_PAID
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.adapter.ImportCalendarMappingListAdapter
import me.proton.android.calendar.presentation.importAssistant.adapter.MergeCalendarListAdapter
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.presentation.ui.view.ProtonProgressButton
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
        if (fragment_import_assistant_import_button.currentState == ProtonProgressButton.State.LOADING) {
            view?.displaySnackBar(getString(R.string.import_assistant_in_progress_snack))
            return
        }
        if (fragment_import_assistant_close_button.visibility == View.VISIBLE) findNavController().navigateUp()
        else {
            displayDiscardChangesConfirmationDialog { _, _ ->
                findNavController().navigateUp()
            }
        }
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_assistant_discard_import_title)
            .setMessage(R.string.import_assistant_discard_import_message)
            .setPositiveButton(R.string.import_assistant_discard_import_positive, callback)
            .setNegativeButton(R.string.import_assistant_discard_import_negative) { _, _ -> }
            .show()
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
            userCalendars ?: return@observe

            if (this::importCalendarMappingListAdapter.isInitialized) {
                importCalendarMappingListAdapter.isOptionsEnabled(userCalendars.any { it.isActive })
            }

            checkCalendarLimit()
        }

        fragment_import_assistant_summary_customize_import_layout.setOnSingleClickListener {
            fragment_import_assistant_scroll_view.smoothScrollTo(
                0,
                fragment_import_assistant_summary_customize_import_layout.bottom + fragment_import_assistant_illustration.bottom + requireContext().dpToPixel(34)
            )
        }

        fragment_import_assistant_import_button.setOnSingleClickListener {
            if (fragment_import_assistant_import_button.currentState == ProtonProgressButton.State.LOADING) return@setOnSingleClickListener
            onStartImportClick()
        }

        // Show loader view by default
        showGatheringDataView()

        initExternalCalendarList()

        importAssistantViewModel.sourceEmail.observe(viewLifecycleOwner) { sourceEmail ->
            fragment_import_assistant_summary_email.text = sourceEmail
        }

        importAssistantViewModel.importCalendarMappingList.observe(viewLifecycleOwner) { importCalendarMappingList ->
            importCalendarMappingList ?: return@observe

            if (this::importCalendarMappingListAdapter.isInitialized) {
                importCalendarMappingListAdapter.submitList(importCalendarMappingList)
            }

            showImportSummaryView(importCalendarMappingList)

            checkCalendarLimit()
        }

        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {
                // Create importer and fetch external calendars
                if (!importAssistantViewModel.handleGoogleSignInRedirect(
                        userId,
                        navigationArguments.code,
                        resources.getIntArray(R.array.accent_colors_base)
                    )) {
                    // Display error snack and navigate back
                    requireActivity().displaySnackBar(getString(R.string.import_assistant_data_gathering_error))
                    findNavController().navigateUp()
                }
            } else {
                // Display error snack and navigate back
                requireActivity().displaySnackBar(getString(R.string.snack_network_error))
                findNavController().navigateUp()
            }
        }
    }

    private fun initExternalCalendarList() {
        val externalCalendarListView = fragment_import_assistant_summary_list
        val externalCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        externalCalendarListView.layoutManager = externalCalendarLayoutManager
        importCalendarMappingListAdapter = ImportCalendarMappingListAdapter(
            importListener = { importCalendarMapping ->
                // On external calendar click
                val indexOfItem = importAssistantViewModel.setImportCalendar(importCalendarMapping, !importCalendarMapping.importCalendar)
                indexOfItem?.let {
                    importCalendarMappingListAdapter.notifyItemChanged(indexOfItem)
                }
            },
            optionsListener = { importCalendarMapping ->
                // On options click
                lifecycleScope.launch {
                    val userCalendars = calendarViewModel.getActiveUserCalendars()
                    showBottomSheetDialog(importCalendarMapping, userCalendars)
                }
            }
        )
        externalCalendarListView.adapter = importCalendarMappingListAdapter
    }

    private fun checkCalendarLimit() {
        val userCalendars = calendarViewModel.userCalendars.value ?: return
        val userCalendarsCount = userCalendars.size
        val importCalendarMappingList = importAssistantViewModel.importCalendarMappingList.value ?: return
        val importCalendarsToCreateCount = importCalendarMappingList.filter { it.createDestinationCalendar && it.importCalendar }.size
        val importCalendarsToImportCount = importCalendarMappingList.filter { it.importCalendar }.size

        lifecycleScope.launch {
            val isFreeUser = calendarViewModel.isFreeUser() ?: return@launch
            if (isFreeUser && (userCalendarsCount + importCalendarsToCreateCount) > MAX_CALENDAR_FREE ||
                !isFreeUser && (userCalendarsCount + importCalendarsToCreateCount) > MAX_CALENDAR_PAID) {
                fragment_import_assistant_summary_header_layout.visibleOrGone(false)
                fragment_import_assistant_summary_error_layout.visibleOrGone(true)
                val countCalendarsOverLimit =
                    if (isFreeUser) {
                        if (userCalendarsCount >= MAX_CALENDAR_FREE) importCalendarsToCreateCount
                        else userCalendarsCount + importCalendarsToCreateCount - MAX_CALENDAR_FREE
                    } else {
                        if (userCalendarsCount >= MAX_CALENDAR_PAID) importCalendarsToCreateCount
                        else userCalendarsCount + importCalendarsToCreateCount - MAX_CALENDAR_PAID
                    }
                val calendarPluralString = resources.getQuantityString(R.plurals.calendar, countCalendarsOverLimit)
                fragment_import_assistant_summary_error.text = getString(
                    R.string.import_assistant_import_summary_error,
                    countCalendarsOverLimit,
                    calendarPluralString
                )
                importButtonIsEnabled(false)
                if (this@ImportAssistantFragment::importCalendarMappingListAdapter.isInitialized) {
                    importCalendarMappingListAdapter.setLimitReached(true)
                }
            } else {
                fragment_import_assistant_summary_header_layout.visibleOrGone(true)
                fragment_import_assistant_summary_error_layout.visibleOrGone(false)
                if (importCalendarsToImportCount > 0) importButtonIsEnabled(true)
                if (this@ImportAssistantFragment::importCalendarMappingListAdapter.isInitialized) {
                    importCalendarMappingListAdapter.setLimitReached(false)
                }
            }
        }
    }

    // TODO Remove this method and only use isEnabled once core ProtonButton has been updated
    private fun importButtonIsEnabled(isEnabled: Boolean) {
        fragment_import_assistant_import_button.isEnabled = isEnabled

        // TODO Remove custom disabled style once core ProtonButton has been updated
        fragment_import_assistant_import_button.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_inverted))
        fragment_import_assistant_import_button.backgroundTintList = ColorStateList.valueOf(
            requireContext().getColorFromAttr(
                if (isEnabled) R.attr.proton_interaction_norm
                else R.attr.proton_interaction_norm_disabled
            )
        )
    }

    private fun onStartImportClick() {
        val calendarsToImport = importAssistantViewModel.importCalendarMappingList.value?.filter { it.importCalendar }
        if (calendarsToImport.isNullOrEmpty()) {
            view?.displaySnackBar(getString(R.string.import_assistant_start_import_empty_error))
            return
        }
        lifecycleScope.launch {
            // Display loading state on import button
            fragment_import_assistant_import_button.setLoading()

            if (calendarsToImport.any { it.createDestinationCalendar }) {
                // Create new calendars
                createCalendars(calendarsToImport)
            }

            // Start the import
            val startImportResult = importAssistantViewModel.startImport(
                customCalendarMapping = calendarsToImport.any { it.mergeCalendar }, // If we're merging a calendar then user has custom mapping
                importCalendarMappingList = calendarsToImport
            )

            // Reset import button state
            fragment_import_assistant_import_button.setIdle()

            if (startImportResult) {
                showImportInProgressView()
            } else {
                showImportSummaryView(calendarsToImport)
                view?.displaySnackBar(getString(R.string.import_assistant_start_import_error))
            }
        }
    }

    private suspend fun createCalendars(calendarsToImport: List<ImportCalendarMapping>) {
        // Create new calendars
        val calendarsToCreateCount = calendarsToImport.count { it.createDestinationCalendar }
        var calendarsCreatedCount = 0

        // Display creating new calendars loader view
        showCreatingCalendarsView(
            calendarsToCreateCount,
            calendarsCreatedCount,
            calendarsToImport.first { it.createDestinationCalendar }.destinationEmail
        )

        calendarsToImport.forEachIndexed { index, calendarToImport ->
            if (calendarToImport.createDestinationCalendar && calendarToImport.destinationId == null) {
                // Create calendar
                val newCalendarId = importAssistantViewModel.createCalendar(
                    calendarToImport.destinationName,
                    calendarToImport.destinationEmail,
                    calendarToImport.destinationColor
                )
                // Set newly created calendar ID
                calendarsToImport[index].destinationId = newCalendarId

                calendarsCreatedCount++
                // Update loader description text
                updateCalendarCreatedView(calendarsToCreateCount, calendarsCreatedCount, calendarToImport.destinationEmail)
            }
        }
        fragment_import_assistant_loader_description.text = getString(R.string.import_assistant_creating_calendars_finish)
    }

    private fun showImportInProgressView() {
        // Display import in progress layout
        fragment_import_assistant_loader_layout.visibleOrGone(false)
        fragment_import_assistant_in_progress_layout.visibleOrGone(true)
        fragment_import_assistant_summary_layout.visibleOrGone(false)

        val defaultUserEmail = importAssistantViewModel.defaultUserEmail.value ?: "" // TODO Handle null ?
        val sourceEmail = importAssistantViewModel.sourceEmail.value ?: "" // TODO Handle null ?
        fragment_import_assistant_in_progress_description.text = getString(
            R.string.import_assistant_in_progress_description,
            sourceEmail,
            defaultUserEmail
        )

        // Show close button
        fragment_import_assistant_import_button.visibleOrGone(false)
        fragment_import_assistant_close_button.visibleOrGone(true)

        // Change illustration
        fragment_import_assistant_in_progress_illustration.visibleOrGone(true)
        fragment_import_assistant_illustration.visibleOrGone(false)

        fragment_import_assistant_close_button.setOnSingleClickListener {
            onNavigationIconClicked()
        }

        fragment_import_assistant_in_progress_redirect.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_import_assistant_to_nav_import_assistant_status)
        }
    }

    private fun showGatheringDataView() {
        // Set loader title
        fragment_import_assistant_loader_title.text = getString(R.string.import_assistant_gathering_data_loader_title)

        // Display loader layout
        fragment_import_assistant_loader_layout.visibleOrGone(true)
        fragment_import_assistant_in_progress_layout.visibleOrGone(false)
        fragment_import_assistant_summary_layout.visibleOrGone(false)

        // Hide the buttons
        fragment_import_assistant_import_button.visibleOrGone(false)
        fragment_import_assistant_close_button.visibleOrGone(false)
    }

    private fun showImportSummaryView(importCalendarMappingList: List<ImportCalendarMapping>) {
        // Display summary layout
        fragment_import_assistant_loader_layout.visibleOrGone(false)
        fragment_import_assistant_in_progress_layout.visibleOrGone(false)
        fragment_import_assistant_summary_layout.visibleOrGone(true)

        val calendarsToImport = importCalendarMappingList.filter { it.importCalendar }
        fragment_import_assistant_summary_count.text = getString(
            R.string.import_assistant_summary_count,
            calendarsToImport.size,
            importCalendarMappingList.size
        )
        fragment_import_assistant_import_button.text = resources.getQuantityString(
            R.plurals.import_assistant_import_button,
            calendarsToImport.size,
            calendarsToImport.size
        )

        // Disable button if list is empty
        importButtonIsEnabled(importCalendarMappingList.any { it.importCalendar })

        // Display calendars to create count
        val calendarsToCreate = importCalendarMappingList.filter { it.createDestinationCalendar && it.importCalendar }.size
        fragment_import_assistant_summary_create_details.visibleOrGone(calendarsToCreate > 0)
        fragment_import_assistant_summary_create_details.text = getString(
            R.string.fragment_import_assistant_summary_create_details,
            calendarsToCreate,
            resources.getQuantityString(R.plurals.calendar, calendarsToCreate)
        )

        // Display calendars to merge count
        val calendarsToMerge = importCalendarMappingList.filter { it.mergeCalendar && it.importCalendar }.size
        fragment_import_assistant_summary_merge_details.visibleOrGone(calendarsToMerge > 0)
        fragment_import_assistant_summary_merge_details.text = getString(
            R.string.fragment_import_assistant_summary_merge_details,
            calendarsToMerge,
            resources.getQuantityString(R.plurals.calendar, calendarsToMerge)
        )

        // Show the import button
        fragment_import_assistant_import_button.visibleOrGone(true)
        fragment_import_assistant_close_button.visibleOrGone(false)
    }

    private fun showCreatingCalendarsView(calendarsToCreateCount: Int, calendarsCreatedCount: Int, destinationEmail: String) {
        // Set loader title
        fragment_import_assistant_loader_title.text = getString(R.string.import_assistant_creating_calendars)

        // Hide the buttons
        fragment_import_assistant_import_button.visibleOrGone(false)
        fragment_import_assistant_close_button.visibleOrGone(false)

        // Display loader layout
        fragment_import_assistant_loader_layout.visibleOrGone(true)
        fragment_import_assistant_in_progress_layout.visibleOrGone(false)
        fragment_import_assistant_summary_layout.visibleOrGone(false)

        // Set initial loader description text
        updateCalendarCreatedView(calendarsToCreateCount, calendarsCreatedCount,destinationEmail)

        // Display loader description
        fragment_import_assistant_loader_description.visibleOrGone(true)
    }

    private fun updateCalendarCreatedView(calendarsToCreateCount: Int, calendarsCreatedCount: Int, destinationEmail: String) {
        fragment_import_assistant_loader_description.text = getString(
            R.string.import_assistant_creating_calendars_count,
            calendarsCreatedCount,
            calendarsToCreateCount,
            destinationEmail
        )
    }

    private fun showBottomSheetDialog(calendarToImport: ImportCalendarMapping, userCalendars: List<Calendar>?) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window?.navigationBarColor = requireContext().getColorFromAttr(
                R.attr.proton_background_norm
            )
        }

        bottomSheetDialog.setContentView(R.layout.dialog_calendar_import_mapping)

        val sourceCalendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_import_mapping_source_title)
        sourceCalendarName?.text = calendarToImport.sourceName

        val newCalendarIcon = bottomSheetDialog.findViewById<ImageView>(R.id.dialog_calendar_import_mapping_new_calendar_icon)
        val newCalendarColor =
            if (calendarToImport.createDestinationCalendar) calendarToImport.destinationColor
            else resources.getIntArray(R.array.accent_colors_base).random()
        newCalendarIcon?.imageTintList = ColorStateList.valueOf(newCalendarColor)
        val newCalendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_import_mapping_new_calendar_title)
        newCalendarName?.text = calendarToImport.sourceName

        val newCalendarPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_import_mapping_new_calendar_press)
        newCalendarPress?.setOnSingleClickListener {
            if (calendarToImport.createDestinationCalendar) {
                // Nothing to do here
                bottomSheetDialog.dismiss()
            }
            else {
                lifecycleScope.launch {
                    importAssistantViewModel.setCreateNewCalendar(calendarToImport, newCalendarColor)
                    bottomSheetDialog.dismiss()
                }
            }
        }

        val mergeCalendarLayout = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_import_mapping_merge_layout)
        mergeCalendarLayout?.visibleOrGone(!userCalendars.isNullOrEmpty())
        if (!userCalendars.isNullOrEmpty()) {
            val mergeCalendarListView = bottomSheetDialog.findViewById<RecyclerView>(R.id.dialog_calendar_import_mapping_merge_list)
            val mergeCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            mergeCalendarListView?.layoutManager = mergeCalendarLayoutManager
            val mergeCalendarListAdapter = MergeCalendarListAdapter {
                // On calendar click
                lifecycleScope.launch {
                    importAssistantViewModel.setMergeExistingCalendar(calendarToImport, it)
                    bottomSheetDialog.dismiss()
                }
            }
            mergeCalendarListView?.adapter = mergeCalendarListAdapter
            mergeCalendarListAdapter.submitList(userCalendars)
        }

        bottomSheetDialog.show()
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
