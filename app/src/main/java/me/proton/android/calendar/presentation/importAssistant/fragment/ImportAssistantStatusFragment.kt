package me.proton.android.calendar.presentation.importAssistant.fragment

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_import_assistant_status.fragment_import_assistant_status_list
import kotlinx.android.synthetic.main.fragment_import_assistant_status.fragment_import_assistant_status_refresh
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarImport
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.data.api.ReportEntity
import me.proton.android.calendar.domain.model.Import
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.adapter.ImportStatusListAdapter
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
    private val mainViewModel: MainViewModel by activityViewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    private lateinit var importStatusListAdapter: ImportStatusListAdapter

    private var importerList: List<ImporterEntity>? = null
    private var reportList: List<ReportEntity>? = null
    private var zoneId: ZoneId? = null

    override fun onBackPressedCustom() {
        if (findNavController().previousBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
            findNavController().navigateUp()
        } else {
            if (!findNavController().popBackStack(R.id.nav_calendar, false)) {
                // TODO this is a workaround for navigating back to month view after opening EventForm from EventDetails
                //  that was opened from system notification
                //  R.id.nav_calendar is not in the hierarchy so popping backstack will fail and we need
                //  to navigate manually
                findNavController().navigate(Navigation.Deeplink.toMonth())
            }
        }
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = getString(R.string.import_assistant_previous_import)
        if (findNavController().previousBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
            toolbar.setNavigationIcon(R.drawable.ic_proton_arrow_left)
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_proton_cross)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initImportList()

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
        }

        fragment_import_assistant_status_refresh.isRefreshing = true
        refreshList()

        val importListMediator = MediatorLiveData<Triple<List<ImporterEntity>, List<ReportEntity>, ZoneId>>()
        importListMediator.addSource(importAssistantViewModel.importerList) { importerList ->
            this.importerList = importerList
            if (importerList != null && reportList != null && zoneId != null) {
                importListMediator.value = Triple(importerList, reportList!!, zoneId!!)
            }
        }
        importListMediator.addSource(importAssistantViewModel.reportList) { reportList ->
            this.reportList = reportList
            if (importerList != null && reportList != null && zoneId != null) {
                importListMediator.value = Triple(importerList!!, reportList, zoneId!!)
            }
        }
        importListMediator.addSource(calendarViewModel.timeZoneId) { zoneId ->
            this.zoneId = zoneId
            if (importerList != null && reportList != null && zoneId != null) {
                importListMediator.value = Triple(importerList!!, reportList!!, zoneId)
            }
        }
        importListMediator.observe(viewLifecycleOwner) {
            it ?: return@observe

            fragment_import_assistant_status_refresh.isRefreshing = false

            val importerList = it.first
            val reportList = it.second
            val zoneId = it.third
            val importList = arrayListOf<Import>()
            importerList.filter { importerEntity ->
                importerEntity.product.contains(CalendarImport.PRODUCT_CALENDAR) && importerEntity.active?.calendar != null
            }.forEach { importerEntity ->
                // Importers
                importList.add(
                    Import(
                        importerEntity.id,
                        importerEntity.account,
                        null,
                        importerEntity.active?.calendar?.createTime?.let { createTime ->
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(createTime.toLong()), zoneId)
                        },
                        importerEntity.active?.calendar?.state?.let { state ->
                            // Canceling is a special case because it has the same state value as canceled but only exists for active importers
                            if (state == Import.ImportState.CANCELED.value) Import.ImportState.CANCELING
                            else Import.ImportState.values()[state]
                        },
                        importerEntity.active?.calendar?.errorCode
                    )
                )
            }
            reportList.filter { reporterEntity ->
                reporterEntity.summary.calendar != null
            }.forEach { reporterEntity ->
                // Reports
                importList.add(
                    Import(
                        reporterEntity.id,
                        reporterEntity.account,
                        reporterEntity.summary.calendar?.totalSize,
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(reporterEntity.createTime.toLong()), zoneId),
                        reporterEntity.summary.calendar?.state?.let { state ->
                            Import.ImportState.values()[state]
                        }
                    )
                )
            }
            importStatusListAdapter.submitList(
                importList.sortedByDescending { it.dateTime }
            )
        }
    }

    private fun initImportList() {
        val importListView = fragment_import_assistant_status_list
        val importLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        importListView.layoutManager = importLayoutManager
        importStatusListAdapter = ImportStatusListAdapter { import, action ->
            when (action) {
                ImportStatusListAdapter.Action.CANCEL -> {
                    showConfirmationDialog(
                        R.string.import_assistant_cancel_import_title,
                        R.string.import_assistant_cancel_import_message,
                        R.string.import_assistant_cancel_import_positive_button,
                        R.string.import_assistant_cancel_import_negative_button
                    ) { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch {
                            if (importAssistantViewModel.cancelImport(import.id)) refreshList()
                        }
                    }
                }
                ImportStatusListAdapter.Action.RESUME -> {
                    lifecycleScope.launch {
                        // No confirmation dialog for resume
                        if (import.errorCode == Import.ErrorCode.LOST_CONNECTION.value) {
                            // Lost connection, we need to sign in to Google and create a new token to update importer
                            val userId = accountViewModel.getPrimaryUserId()
                            if (userId != null) {
                                val googleAuthenticationUrl = importAssistantViewModel.getGoogleAuthenticationUrl(userId, import.id)
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleAuthenticationUrl))
                                startActivity(browserIntent)
                            } else {
                                view?.displaySnackBar(getString(R.string.snack_network_error))
                            }
                        } else {
                            if (importAssistantViewModel.resumeImport(import.id)) refreshList()
                        }
                    }
                }
                ImportStatusListAdapter.Action.DELETE -> {
                    showConfirmationDialog(
                        R.string.import_assistant_delete_report_title,
                        R.string.import_assistant_delete_report_message,
                        R.string.import_assistant_delete_report_positive_button,
                        R.string.import_assistant_delete_report_negative_button
                    ) { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch {
                            // Import.id is the reportId since we mapped both reports and active importers to the Import object
                            importAssistantViewModel.deleteReport(import.id)
                        }
                    }
                }
            }
        }
        importListView.adapter = importStatusListAdapter

        fragment_import_assistant_status_refresh.setOnRefreshListener {
            refreshList()
        }
    }

    private fun refreshList() {
        lifecycleScope.launch {
            importAssistantViewModel.getReports()
            importAssistantViewModel.getImporters()
            // TODO Handle error and cancel loading animation
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }

    private fun showConfirmationDialog(
        title: Int,
        message: Int,
        positiveButtonText: Int,
        negativeButtonText: Int,
        listener: DialogInterface.OnClickListener
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setCancelable(true)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText, listener)
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                dialog.dismiss()
            }

        materialDialogBuilder.show()
    }
}
