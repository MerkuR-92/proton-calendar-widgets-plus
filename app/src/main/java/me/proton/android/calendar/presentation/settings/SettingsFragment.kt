package me.proton.android.calendar.presentation.settings

import android.os.Build
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
import me.proton.android.calendar.presentation.MainActivity
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
        settings_theme.visibleOrGone(FeatureFlag.SETTINGS_THEME)
        settings_week_start.visibleOrGone(FeatureFlag.SETTINGS_WEEK_START)
        settings_time_format.visibleOrGone(FeatureFlag.SETTINGS_TIME_FORMAT)
        settings_week_numbers.visibleOrGone(FeatureFlag.SETTINGS_WEEK_NUMBERS)

        settings_week_numbers_press.setOnClickListener {
            settings_week_numbers_switch.performClick()
        }
        settings_week_numbers_switch.setOnCheckedChangeListener { _, checked ->
            // Handle show week numbers switch action
        }

        settings_update_timezone_press.setOnClickListener {
            settings_update_timezone_switch.performClick()
        }
        settings_update_timezone_switch.setOnClickListener {
            lifecycleScope.launch {
                calendarViewModel.updateAutoDetectPrimaryTimezone(settings_update_timezone_switch.isChecked)
            }
        }

        settings_timezone_press.setOnSingleClickListener {
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
                    calendarViewModel.updatePrimaryTimezone(formattedTimeZoneIds[it].formattedTimeZoneToId())
                }
            }
        }

        val appThemes = resources.getStringArray(R.array.app_themes)
        settings_theme_value.text = appThemes[(activity as MainActivity).getAppTheme().value]

        // TODO Handle themes for Android P and below
        settings_theme.visibleOrGone(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings_theme_press.setOnSingleClickListener {
                AndroidUtils.displaySingleChoicePicker(
                    requireContext(),
                    getString(R.string.settings_theme_title),
                    appThemes,
                    AppTheme.values().indexOf((activity as MainActivity).getAppTheme())
                ) { index ->
                    settings_theme_value.text = appThemes[index]
                    (activity as MainActivity).changeAppTheme(AppTheme.values()[index])
                }
            }
        }

        val timeFormats = resources.getStringArray(R.array.time_formats)
        calendarViewModel.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
            settings_time_format_value.text = timeFormats[timeFormat]
        }

        settings_time_format_press.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_time_format_title),
                timeFormats,
                timeFormats.indexOf(settings_time_format_value.text)
            ) { index ->
                settings_time_format_value.text = timeFormats[index]
                calendarViewModel.updateTimeFormat(index)
            }
        }

        lifecycleScope.launch {
            settings_update_timezone_switch.isChecked = calendarViewModel.getCalendarUserSettingsAutoDetectPrimaryTimezone()
            settings_update_timezone_switch.jumpDrawablesToCurrentState()
        }

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            settings_timezone_value.text = zoneId.id ?: getString(R.string.settings_value_placeholder)
        }
    }
}
