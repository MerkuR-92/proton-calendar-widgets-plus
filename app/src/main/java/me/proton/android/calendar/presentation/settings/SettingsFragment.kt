package me.proton.android.calendar.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.core.util.kotlin.toBoolean
import me.proton.core.util.kotlin.toInt
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import java.time.Instant

class SettingsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "SettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_settings

    override val navigateUp = false

    private val calendarViewModel: CalendarViewModel by sharedViewModel()

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

        settings_proton_account_update_timezone_press.setOnClickListener {
            settings_proton_account_update_timezone_switch.performClick()
        }
        settings_proton_account_update_timezone_switch.setOnClickListener {
            lifecycleScope.launch {
                calendarViewModel.updateCalendarUserSettings(autoDetectPrimaryTimezone = settings_proton_account_update_timezone_switch.isChecked.toInt())
            }
        }

        settings_proton_account_timezone_press.setOnSingleClickListener {
            //.atZone(calendarViewModel.timeZoneId.value).toInstant()
            val forInstant = Instant.now()
            val formattedTimeZoneIds = allowedTimezoneIds.map {
                ICalUtils.formatTimeZoneId(it, forInstant)
            }.toTypedArray()
            formattedTimeZoneIds.sortFormattedTimeZoneIds()
            val defaultTimeZone = calendarViewModel.timeZoneId.value?.id
            val selectedIndex =
                if (defaultTimeZone == null) -1
                else formattedTimeZoneIds.indexOf(ICalUtils.formatTimeZoneId(defaultTimeZone, forInstant))

            AndroidUtils.displaySingleChoicePicker(requireContext(), null, formattedTimeZoneIds, selectedIndex) {
                lifecycleScope.launch {
                    calendarViewModel.updateCalendarUserSettings(primaryTimezone = formattedTimeZoneIds[it].formattedTimeZoneToId())
                }
            }
        }

        // Settings values

        lifecycleScope.launch {
            settings_proton_account_update_timezone_switch.isChecked = calendarViewModel.getCalendarUserSettingsAutoDetectPrimaryTimezone()
            settings_proton_account_update_timezone_switch.jumpDrawablesToCurrentState()
        }

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            settings_proton_account_timezone_value.text = zoneId.id ?: getString(R.string.settings_value_placeholder)
        }
    }
}
