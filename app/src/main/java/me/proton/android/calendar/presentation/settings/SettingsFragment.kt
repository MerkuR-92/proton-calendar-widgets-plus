package me.proton.android.calendar.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_settings.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FeatureFlag
import me.proton.android.calendar.common.visibleOrGone
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.core.KoinComponent

class SettingsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "SettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_settings

    override val navigateUp = false

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_more_settings)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO Remove feature flags
        settings_app_title.visibleOrGone(FeatureFlag.SETTINGS_THEME)
        settings_app_separator.visibleOrGone(FeatureFlag.SETTINGS_THEME)
        settings_app_theme.visibleOrGone(FeatureFlag.SETTINGS_THEME)

        settings_proton_account_title.visibleOrGone(FeatureFlag.SETTINGS_TIMEZONE || FeatureFlag.SETTINGS_WEEK_START || FeatureFlag.SETTINGS_TIME_FORMAT || FeatureFlag.SETTINGS_WEEK_NUMBERS)
        settings_proton_account_separator.visibleOrGone(FeatureFlag.SETTINGS_TIMEZONE || FeatureFlag.SETTINGS_WEEK_START || FeatureFlag.SETTINGS_TIME_FORMAT || FeatureFlag.SETTINGS_WEEK_NUMBERS)
        settings_proton_account_week_start.visibleOrGone(FeatureFlag.SETTINGS_WEEK_START)
        settings_proton_account_time_format.visibleOrGone(FeatureFlag.SETTINGS_TIME_FORMAT)
        settings_proton_account_week_numbers.visibleOrGone(FeatureFlag.SETTINGS_WEEK_NUMBERS)

        settings_proton_account_week_numbers_press.setOnClickListener {
            settings_proton_account_week_numbers_switch.performClick()
        }
        settings_proton_account_week_numbers_switch.setOnCheckedChangeListener { _, checked ->
            // Handle show week numbers switch action
        }
    }
}
