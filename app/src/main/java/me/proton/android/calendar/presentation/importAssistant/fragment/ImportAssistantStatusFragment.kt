package me.proton.android.calendar.presentation.importAssistant.fragment

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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_import_assistant_status.fragment_import_assistant_status_list
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

        lifecycleScope.launch {
            importAssistantViewModel.getReports()
            importAssistantViewModel.getImporters()
        }

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
            val importerList = it.first
            val reportList = it.second
            val zoneId = it.third
            it?.let {
                val importList = arrayListOf<Import>()
                importerList.filter { it.product.contains(CalendarImport.PRODUCT_CALENDAR) && it.active?.calendar != null }.forEach { importerEntity ->
                    importList.add(
                        Import(
                            importerEntity.id,
                            importerEntity.account,
                            null,
                            importerEntity.active?.calendar?.createTime?.let { createTime ->
                                LocalDateTime.ofInstant(Instant.ofEpochSecond(createTime.toLong()), zoneId)
                            },
                            importerEntity.active?.calendar?.state?.let { state ->
                                Import.ImportState.values()[state]
                            }

                        )
                    )
                }
                reportList.filter { it.summary.calendar != null }.forEach { reporterEntity ->
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
                importStatusListAdapter.submitList(importList)
            }
        }
    }

    private fun initImportList() {
        val importListView = fragment_import_assistant_status_list
        val importLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        importListView.layoutManager = importLayoutManager
        importStatusListAdapter = ImportStatusListAdapter {

        }
        importListView.adapter = importStatusListAdapter
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
