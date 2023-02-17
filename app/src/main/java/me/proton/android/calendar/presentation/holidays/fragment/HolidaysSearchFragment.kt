package me.proton.android.calendar.presentation.holidays.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_base_dialog.dialog_appbar
import kotlinx.android.synthetic.main.fragment_holidays_search.holidays_search_close
import kotlinx.android.synthetic.main.toolbar_action_text.view.toolbar_action_text
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.presentation.holidays.viewModel.HolidaysViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent

@AndroidEntryPoint
class HolidaysSearchFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "HolidaysFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_holidays_search

    override val navigateUp = true
    override val isScrollable = false

    private val holidaysViewModel: HolidaysViewModel by activityViewModels()
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

        // Hide app bar
        dialog_appbar.visibleOrGone(false)

        holidays_search_close.toolbar_action_text.text = getString(R.string.dialog_button_close)
        holidays_search_close.toolbar_action_text.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            findNavController().navigateUp()
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
