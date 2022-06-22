package me.proton.android.calendar.presentation.importAssistant.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_import_assistant_guide.import_assistant_status_guide_imports_press
import kotlinx.android.synthetic.main.fragment_import_assistant_guide.import_assistant_status_guide_imports_subtitle
import kotlinx.android.synthetic.main.fragment_import_assistant_guide.import_assistant_status_guide_new_import_button
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarImport.PRODUCT_CALENDAR
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent

@AndroidEntryPoint
class ImportAssistantGuideFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantGuideFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant_guide

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
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = "" // No Title
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            importAssistantViewModel.getImporters()
        }

        // Hidden by default
        import_assistant_status_guide_imports_subtitle.visibleOrGone(false)

        importAssistantViewModel.importerList.observe(viewLifecycleOwner) { importerList ->
            importerList ?: return@observe
            refreshOngoingImportText(importerList)
        }

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
        }

        import_assistant_status_guide_new_import_button.setOnSingleClickListener {
            (requireActivity() as MainActivity).showImportGoogleAuthDialog()
        }

        import_assistant_status_guide_imports_press.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_import_assistant_guide_to_nav_import_assistant_status)
        }
    }

    override fun onResume() {
        super.onResume()
        importAssistantViewModel.importerList.value?.let { importerList ->
            refreshOngoingImportText(importerList)
        }
    }

    private fun refreshOngoingImportText(importerList: List<ImporterEntity>) {
        val ongoingImports = importerList.count { it.product.contains(PRODUCT_CALENDAR) && it.active?.calendar != null }
        import_assistant_status_guide_imports_subtitle.visibleOrGone(ongoingImports > 0)
        if (ongoingImports > 0) {
            import_assistant_status_guide_imports_subtitle.text = getString(
                R.string.import_assistant_ongoing_import,
                ongoingImports
            )
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}

