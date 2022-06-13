package me.proton.android.calendar.presentation.importAssistant.fragment

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_close_button
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_import_button
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_description
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_loader_title
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_count
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_customize_import_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_email
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_layout
import kotlinx.android.synthetic.main.fragment_import_assistant.fragment_import_assistant_summary_list
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.adapter.ImportCalendarMappingListAdapter
import me.proton.android.calendar.presentation.importAssistant.adapter.MergeCalendarListAdapter
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
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
            onStartImportClick()
        }

        initExternalCalendarList()

        importAssistantViewModel.sourceEmail.observe(viewLifecycleOwner) { sourceEmail ->
            fragment_import_assistant_summary_email.text = sourceEmail
        }

        importAssistantViewModel.importCalendarMappingList.observe(viewLifecycleOwner) { importCalendarMappingList ->
            importCalendarMappingListAdapter.submitList(importCalendarMappingList)

            // Display summary header
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
            fragment_import_assistant_import_button.visibleOrGone(true)
        }

        lifecycleScope.launch {
            // Display loader
            fragment_import_assistant_loader_title.text = getString(R.string.import_assistant_gathering_data_loader_title)
            fragment_import_assistant_loader_layout.visibleOrGone(true)

            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {
                // Create importer and fetch external calendars
                importAssistantViewModel.handleGoogleSignInRedirect(
                    userId,
                    navigationArguments.code,
                    resources.getIntArray(R.array.accent_colors_base)
                )

                // Hide loader
                fragment_import_assistant_loader_layout.visibleOrGone(false)
            } else {
                view.displaySnackBar(getString(R.string.snack_network_error))
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
                    val userCalendars = calendarViewModel.getUserCalendars()
                    showBottomSheetDialog(importCalendarMapping, userCalendars)
                }
            }
        )
        externalCalendarListView.adapter = importCalendarMappingListAdapter
    }

    private fun onStartImportClick() {
        val calendarsToImport = importAssistantViewModel.importCalendarMappingList.value?.filter { it.importCalendar }
        if (calendarsToImport.isNullOrEmpty()) return // TODO Display Snack ?
        lifecycleScope.launch {
            // Display loading state on import button
            fragment_import_assistant_import_button.setLoading()
            // Display loader layout
            fragment_import_assistant_loader_layout.visibleOrGone(true)
            // Hide summary layout
            fragment_import_assistant_summary_layout.visibleOrGone(false)

            if (calendarsToImport.any { it.createDestinationCalendar }) {
                // Display Creating calendars loader
                fragment_import_assistant_loader_title.text = getString(R.string.import_assistant_creating_calendars)

                // Create new calendars
                val calendarsToCreateCount = calendarsToImport.count { it.createDestinationCalendar }
                var calendarsCreatedCount = 0
                // Set initial loader description text
                fragment_import_assistant_loader_description.text = getString(
                    R.string.import_assistant_creating_calendars_count,
                    calendarsCreatedCount,
                    calendarsToCreateCount,
                    calendarsToImport.first { it.createDestinationCalendar }.destinationEmail
                )
                fragment_import_assistant_loader_description.visibleOrGone(true)
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
                        // Update loader description text
                        calendarsCreatedCount++
                        fragment_import_assistant_loader_description.text = getString(
                            R.string.import_assistant_creating_calendars_count,
                            calendarsCreatedCount,
                            calendarsToCreateCount,
                            calendarToImport.destinationEmail
                        )
                    }
                }
                fragment_import_assistant_loader_description.text = getString(R.string.import_assistant_creating_calendars_finish)
            }
            val startImportResult = importAssistantViewModel.startImport(
                customCalendarMapping = true, // TODO HANDLE THIS FIELD
                importCalendarMappingList = calendarsToImport
            )
            if (startImportResult) displayImportInProgressView()
        }
    }

    private fun displayImportInProgressView() {
        fragment_import_assistant_loader_layout.visibleOrGone(false)
        fragment_import_assistant_import_button.visibleOrGone(false)
        fragment_import_assistant_close_button.visibleOrGone(true)

        // TODO VIEW
    }

    private fun showBottomSheetDialog(calendarToImport: ImportCalendarMapping, userCalendars: List<CalendarEntity>?) {
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
