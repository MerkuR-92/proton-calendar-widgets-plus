package me.proton.android.calendar.presentation.settings.fragment

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_general_settings.settings_alternative_routing_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_alternative_routing_switch
import kotlinx.android.synthetic.main.fragment_general_settings.settings_language
import kotlinx.android.synthetic.main.fragment_general_settings.settings_language_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_language_value
import kotlinx.android.synthetic.main.fragment_general_settings.settings_theme
import kotlinx.android.synthetic.main.fragment_general_settings.settings_theme_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_theme_value
import kotlinx.android.synthetic.main.fragment_general_settings.settings_time_format_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_time_format_value
import kotlinx.android.synthetic.main.fragment_general_settings.settings_timezone_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_timezone_value
import kotlinx.android.synthetic.main.fragment_general_settings.settings_update_timezone_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_update_timezone_switch
import kotlinx.android.synthetic.main.fragment_general_settings.settings_week_numbers_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_week_numbers_switch
import kotlinx.android.synthetic.main.fragment_general_settings.settings_week_start_press
import kotlinx.android.synthetic.main.fragment_general_settings.settings_week_start_value
import kotlinx.coroutines.launch
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.FeatureFlag.CHANGE_LANGUAGE
import me.proton.android.calendar.common.allowedTimezoneIds
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.formattedTimeZoneToId
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.sortFormattedTimeZoneIds
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent
import java.time.DayOfWeek
import java.time.Instant

@AndroidEntryPoint
class GeneralSettingsFragment : BaseDialogFragment(), KoinComponent {

    override val TAG: String
        get() = "GeneralSettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_general_settings

    override val navigateUp = true

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
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
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_more_general_settings)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings_week_numbers_press.setOnClickListener {
            settings_week_numbers_switch.performClick()
        }
        settings_week_numbers_switch.setOnClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                displayNetworkError()
                settings_week_numbers_switch.isChecked = !settings_week_numbers_switch.isChecked
                return@setOnClickListener
            }
            lifecycleScope.launch {
                calendarViewModel.updateDisplayWeekNumber(settings_week_numbers_switch.isChecked)
            }
        }

        settings_update_timezone_press.setOnClickListener {
            settings_update_timezone_switch.performClick()
        }
        settings_update_timezone_switch.setOnClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                displayNetworkError()
                settings_update_timezone_switch.isChecked = !settings_update_timezone_switch.isChecked
                return@setOnClickListener
            }
            lifecycleScope.launch {
                calendarViewModel.updateAutoDetectPrimaryTimezone(settings_update_timezone_switch.isChecked)
            }
        }

        settings_timezone_press.setOnSingleClickListener {
            val forInstant = Instant.now()
            val formattedTimeZoneIds = allowedTimezoneIds.map {
                DateTimeUtilsImpl.formatTimeZoneId(it, forInstant)
            }.toTypedArray()
            formattedTimeZoneIds.sortFormattedTimeZoneIds()
            lifecycleScope.launch {
                val defaultTimeZone = calendarViewModel.getTimeZoneId()?.id
                val selectedIndex =
                    if (defaultTimeZone == null) -1
                    else formattedTimeZoneIds.indexOf(DateTimeUtilsImpl.formatTimeZoneId(defaultTimeZone, forInstant))

                AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.settings_timezone_title), formattedTimeZoneIds, selectedIndex) {
                    if (!mainViewModel.isConnectedToNetwork) {
                        displayNetworkError()
                        return@displaySingleChoicePicker
                    }
                    calendarViewModel.updatePrimaryTimezone(formattedTimeZoneIds[it].formattedTimeZoneToId())
                }
            }
        }

        val appThemes = resources.getStringArray(R.array.app_themes)
        settings_theme_value.text = appThemes[application.getAppTheme().value]

        // TODO Handle themes for Android P and below
        settings_theme.visibleOrGone(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings_theme_press.setOnSingleClickListener {
                AndroidUtils.displaySingleChoicePicker(
                    requireContext(),
                    getString(R.string.settings_theme_title),
                    appThemes,
                    AppTheme.values().indexOf(application.getAppTheme())
                ) { index ->
                    settings_theme_value.text = appThemes[index]
                    application.changeAppTheme(AppTheme.values()[index])
                }
            }
        }

        val appLanguagesLabels = resources.getStringArray(R.array.custom_language_labels)
        val appLanguagesValues = resources.getStringArray(R.array.custom_language_values)
        val selectedLanguageValue = (activity as MainActivity).getAppSettingsLanguage()
        val selectedLanguageIndex = appLanguagesValues.indexOfFirst { it == selectedLanguageValue }
        val systemDefaultLabel = resources.getString(R.string.settings_language_default)
        settings_language_value.text = if (selectedLanguageIndex == -1) systemDefaultLabel else appLanguagesLabels[selectedLanguageIndex]

        val appLanguageDialogLabels = appLanguagesLabels.toMutableList()
        appLanguageDialogLabels.add(0, systemDefaultLabel)

        settings_language.visibleOrGone(CHANGE_LANGUAGE)
        settings_language_press.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_language_title),
                appLanguageDialogLabels.toTypedArray(),
                run {
                    val selectedLanguageDialogIndex = appLanguagesValues.indexOfFirst { it == (activity as MainActivity).getAppSettingsLanguage() }
                    if (selectedLanguageDialogIndex == -1) 0 else selectedLanguageDialogIndex + 1
                }
            ) { index ->
                if (index == 0) {
                    settings_language_value.text = systemDefaultLabel
                    (activity as MainActivity).changeAppLanguage("") // Use empty string for System default
                } else {
                    settings_language_value.text = appLanguagesLabels[index - 1]
                    (activity as MainActivity).changeAppLanguage(appLanguagesValues[index - 1])
                }
            }
        }

        val timeFormats = resources.getStringArray(R.array.time_formats)
        settings_time_format_press.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_time_format_title),
                timeFormats,
                timeFormats.indexOf(settings_time_format_value.text)
            ) { index ->
                if (!mainViewModel.isConnectedToNetwork) {
                    displayNetworkError()
                    return@displaySingleChoicePicker
                }
                settings_time_format_value.text = timeFormats[index]
                calendarViewModel.updateTimeFormat(index)
            }
        }

        val weekStartValues = resources.getStringArray(R.array.week_start)
        settings_week_start_press.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_week_start_title),
                weekStartValues,
                weekStartValues.indexOf(settings_week_start_value.text)) { index ->
                if (!mainViewModel.isConnectedToNetwork) {
                    displayNetworkError()
                    return@displaySingleChoicePicker
                }
                lifecycleScope.launch {
                    val weekStart = when (index) {
                        2 -> DayOfWeek.SATURDAY.value // 6 is value for Saturday and index 2 in available days string array
                        3 -> DayOfWeek.SUNDAY.value // 7 is value for Sunday and index 3 in available days string array
                        else -> index
                    }
                    calendarViewModel.updateWeekStart(weekStart)
                }
            }
        }

        val isAlternativeRoutingEnabled = mainViewModel.isAlternativeRoutingEnabled()
        settings_alternative_routing_switch.isChecked = isAlternativeRoutingEnabled
        settings_alternative_routing_press.setOnClickListener {
            settings_alternative_routing_switch.performClick()
        }
        settings_alternative_routing_switch.setOnClickListener {
            mainViewModel.setAlternativeRoutingEnabled(settings_alternative_routing_switch.isChecked)
        }

        // Observers

        calendarViewModel.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
            settings_time_format_value.text = timeFormats[timeFormat]
        }

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            settings_timezone_value.text = zoneId.id?.let {
                DateTimeUtilsImpl.formatTimeZoneId(it, Instant.now())
            } ?: getString(R.string.settings_value_placeholder)
        }

        calendarViewModel.weekStart.observe(viewLifecycleOwner) { weekStart ->
            settings_week_start_value.text = when (weekStart) {
                DayOfWeek.SATURDAY.value -> weekStartValues[2] // 6 is value for Saturday and index 2 in available days string array
                DayOfWeek.SUNDAY.value -> weekStartValues[3] // 7 is value for Sunday and index 3 in available days string array
                else -> weekStartValues[weekStart]
            }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            settings_week_numbers_switch.isChecked = displayWeekNumber
            settings_week_numbers_switch.jumpDrawablesToCurrentState()
        }

        calendarViewModel.autoDetectPrimaryTimezone.observe(viewLifecycleOwner) { autoDetectPrimaryTimezone ->
            settings_update_timezone_switch.isChecked = autoDetectPrimaryTimezone
            settings_update_timezone_switch.jumpDrawablesToCurrentState()
        }

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}

