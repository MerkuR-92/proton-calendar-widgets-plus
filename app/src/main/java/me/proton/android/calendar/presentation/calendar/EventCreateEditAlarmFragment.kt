package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.alarm_custom_view.*
import kotlinx.android.synthetic.main.fragment_event_create_edit_alarm.*
import kotlinx.android.synthetic.main.fragment_event_create_edit_recurrence.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class EventCreateEditAlarmFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventCreateEditAlarmFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_create_edit_alarm

    override val actionMenuResourceId = R.menu.fragment_event_create_edit_dialog

    override fun onMenuItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.action_menu_done) {
            Timber.d("notification create/edit done")

            val alarmTypeOption = getCheckedRadioButtonIndex(event_create_edit_alarm_radio_group)

//                 GET RADIO BUTTONS, IF == 1 then hardcode 9:00 in VM
//             val customPeriod =
//             val customCount = et_alarm_count.text.toString().toIntOrNull() ?: FormValidation.
//                 val customTime = nullable

            val option =
                if (getCheckedRadioButtonIndex(event_create_edit_alarm_action_radio_group) == 0)
                    EventViewModel.SendByOption.NOTIFICATION
                else EventViewModel.SendByOption.EMAIL
            eventViewModel.handleAlarmSendBy(option) // 0 -- notification (default), 1 -- email

            //We hide minutes and hours buttons so days and weeks have id 2 & 3
            val countTypeOption = getCheckedRadioButtonIndex(alarm_custom_radio_group) - 2
            eventViewModel.handleAlarm(alarmTypeOption,
                count = alarm_custom_field.text.toString().toIntOrNull(),
                countTypeOption = countTypeOption)
            findNavController().navigateUp()

            // TODO
            // copy all values edited here to VM, before this they should be ephemeral, but we should keep in memory edited-not-saved
            // notifications when switching between custom and canned ones
        }
    }
    private lateinit var toolbarTitle: TextView

    private val eventViewModel: EventViewModel by sharedViewModel()

    private val isAllDay by lazy { eventViewModel.eventLiveData.value!!.isAllDay() }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetAlarmCustomText(count: Int) {
        alarm_custom_3.text = getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_day, count, count))
        alarm_custom_4.text = getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_week, count, count))
        if (!isAllDay) {
            alarm_custom_1.text = getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_minute, count, count))
            alarm_custom_2.text = getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_hour, count, count))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        toolbarTitle.text = getString(R.string.event_alarms_title)
        toolbar.setNavigationIcon(R.drawable.ic_close)

        if (isAllDay) { // TODO refactor and extract common formatting code to helpers -- pass timezone, locale and am/pm setting for later
            event_create_edit_alarm_1.text = getString(R.string.event_alarm_all_day_1, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            event_create_edit_alarm_2.text = getString(R.string.event_alarm_all_day_2, LocalTime.of(18, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            event_create_edit_alarm_3.text = getString(R.string.event_alarm_all_day_3, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            event_create_edit_alarm_4.text = getString(R.string.event_alarm_all_day_4, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            event_create_edit_alarm_5.visibleOrGone(false)
        } else {
            event_create_edit_alarm_1.text = getString(R.string.event_alarm_partial_day_1)
            event_create_edit_alarm_2.text = getString(R.string.event_alarm_partial_day_2)
            event_create_edit_alarm_3.text = getString(R.string.event_alarm_partial_day_3)
            event_create_edit_alarm_4.text = getString(R.string.event_alarm_partial_day_4)
            event_create_edit_alarm_5.visibleOrGone(true)
            event_create_edit_alarm_5.text = getString(R.string.event_alarm_partial_day_5)
        }

        event_create_edit_alarm_radio_group.check(event_create_edit_alarm_1.id) // TODO read from event alarm
        event_create_edit_alarm_action_radio_group.check(event_create_edit_alarm_action_notification.id) // TODO read from event alarm

        event_create_edit_alarm_radio_group.setOnCheckedChangeListener { radioGroup, index ->
            when (index) {
                R.id.event_create_edit_alarm_custom -> {
                    toolbarTitle.text = getString(R.string.event_custom_alarms_title)

                    //Change view
                    event_create_edit_alarm_radio_group.visibleOrGone(false)
                    event_create_edit_alarm_custom_layout.visibleOrGone(true)
                }
            }
        }
        event_create_edit_alarm_action_radio_group.setOnCheckedChangeListener { radioGroup, index ->
            requireActivity().clearFocusAndHideKeyboard(view)
        }

        var lastSelectedIndex: Int? = null

        alarm_custom_radio_group.setOnCheckedChangeListener { radioGroup, index ->

            // prevent infinite loop when resetting adapters by EditText changes and Spinner selection
            if (lastSelectedIndex != null && lastSelectedIndex == index) {
                return@setOnCheckedChangeListener
            }
            lastSelectedIndex = index

            requireActivity().clearFocusAndHideKeyboard(view)

            when (getCheckedRadioButtonIndex(alarm_custom_radio_group)) {
                0 -> { // minute
                    resetAlarmCountValidation(
                        FormValidation.ALARM_PERIOD_COUNT_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_MINUTES
                    )
                }
                1 -> { // hour
                    resetAlarmCountValidation(
                        FormValidation.ALARM_PERIOD_COUNT_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_HOURS
                    )
                }
                2 -> { // day
                    resetAlarmCountValidation(
                        FormValidation.ALARM_PERIOD_COUNT_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_DAYS
                    )
                }
                3 -> { // week
                    resetAlarmCountValidation(
                        FormValidation.ALARM_PERIOD_COUNT_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_WEEKS
                    )
                }
            }
        }

        // init
        alarm_custom_1.visibleOrGone(!isAllDay)
        alarm_custom_2.visibleOrGone(!isAllDay)
        alarm_custom_time_layout.visibleOrGone(isAllDay)
        if (isAllDay) {
            alarm_custom_field.setText("1")
            resetAlarmCustomText(1)
            alarm_custom_radio_group.check(alarm_custom_3.id)
            alarm_custom_time.text = eventViewModel.tempAlarmTime.format()
        } else {
            alarm_custom_field.setText("15")
            resetAlarmCustomText(15)
            alarm_custom_radio_group.check(alarm_custom_1.id)
        }

        alarm_custom_field_layout.setEndIconOnClickListener {
            alarm_custom_field.setText(FormValidation.ALARM_PERIOD_COUNT_DEFAULT.toString())
        }

        alarm_custom_time_press.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future

            AndroidUtils.displayTimePicker(requireContext(), LocalTime.now(), is24Hour) {
                eventViewModel.handleAlarmTime(it)
                alarm_custom_time.text = it.format()
            }
        }
    }

    // we keep track of TextWatcher so we can remove it when resetting alarm validation
    var alarmCustomFieldTextWatcher: TextWatcher? = null

    private fun resetAlarmCountValidation(default: Int, min: Int, max: Int) {
        // remove current alarm count text watcher
        alarmCustomFieldTextWatcher?.let { alarm_custom_field.removeTextChangedListener(it) }

        // set new text watcher with new config
        alarmCustomFieldTextWatcher =
            alarm_custom_field.doAfterFilteredIntValueChanged(default, min, max) {
                resetAlarmCustomText(it)
            }

        // set current value again because it might be outside of newly set limits
        alarm_custom_field.setText(alarm_custom_field.text)
    }
}
