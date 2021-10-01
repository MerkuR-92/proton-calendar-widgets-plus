package me.proton.android.calendar.presentation.bug_report

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Operation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_base.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_bug_report.*
import kotlinx.android.synthetic.main.nav_view_main.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent

class BugReportFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "BugReportFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_bug_report

    override val navigateUp = false

    private lateinit var buttonSend: View
    private lateinit var loadingAction: View

    private val calendarViewModel: CalendarViewModel by sharedViewModel()

    override fun onBackPressedCustom() {
        if (bug_report_title.text.isNotEmpty() || bug_report_description.text.isNotEmpty()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                // Reinitialise event view model data when user chooses to discard modifications
                lifecycleScope.launch {
                    findNavController().navigateUp()
                }
            }
        } else findNavController().navigateUp()
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bug_report_discard_changes_title)
            .setMessage(R.string.bug_report_discard_changes_description)
            .setPositiveButton(R.string.bug_report_discard_changes_confirm, callback)
            .setNegativeButton(R.string.bug_report_discard_changes_cancel) { _, _ -> }
            .show()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSend = layoutInflater.inflate(R.layout.toolbar_action_button, dialog_toolbar_content, false)
        with (buttonSend) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_paper_plane))
            setOnSingleClickListener {
                requireActivity().clearFocusAndHideKeyboard(view)

                if (bug_report_description.text.isEmpty()) {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_send_bug_report_description_empty))
                    return@setOnSingleClickListener
                }

                displayLoading(true)
                lifecycleScope.launch {
                    val user = withContext(Dispatchers.Default) {
                        calendarViewModel.selectUser()
                    }
                    val osName = "Android"
                    val osVersion = "" + Build.VERSION.SDK_INT
                    val client = "AndroidCalendar"
                    val appVersionName = getString(
                        R.string.nav_view_version_name,
                        BuildConfig.VERSION_NAME
                    )
                    val title: String = bug_report_title.text.toString()
                    val description: String = bug_report_description.text.toString()
                    val username: String = user?.name ?: ""
                    val email: String = user?.email ?: ""

                    calendarViewModel.sendBugReport(
                        osName,
                        osVersion,
                        client,
                        appVersionName,
                        title,
                        description,
                        username,
                        email
                    ).observe(viewLifecycleOwner) {
                        displayLoading(false)
                        if (it is Operation.State.SUCCESS) {
                            requireActivity().displaySnackBar(requireContext().getString(R.string.snack_send_bug_report_success))
                            findNavController().navigateUp()
                        } else if (it is Operation.State.FAILURE) {
                            view?.displaySnackBar(requireContext().getString(R.string.snack_send_bug_report_error))
                        }
                    }
                }
            }
        }

        loadingAction = layoutInflater.inflate(R.layout.toolbar_action_loader, dialog_toolbar_content, false)
        loadingAction.visibleOrGone(false)

        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                buttonSend, layoutParams
            )
            addView(
                loadingAction, layoutParams
            )
        }

        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_more_bug)
    }

    private fun displayLoading(display: Boolean) {
        // Update action bar buttons visibility
        loadingAction.visibleOrGone(display)
        buttonSend.visibleOrGone(!display)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
