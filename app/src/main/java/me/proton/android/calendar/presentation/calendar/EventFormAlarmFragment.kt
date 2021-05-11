package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.event_form_custom_alarm_view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form_alarm.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.AndroidUtils.doAfterFilteredIntValueChanged
import me.proton.android.calendar.common.AndroidUtils.getCheckedRadioButtonIndex
import me.proton.android.calendar.common.AndroidUtils.setCustomOnCheckedChangeListener
import me.proton.android.calendar.common.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.presentation.BaseDialogFragment
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import timber.log.Timber
import java.time.LocalTime


class EventFormAlarmFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventFormAlarmFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_alarm

    private lateinit var toolbarTitle: TextView

    private val eventViewModel: EventViewModel by sharedViewModel()

    private val isAllDay by lazy { eventViewModel.eventLiveData.value!!.isAllDay() }

    private var lastSelectedRadioButtonId: Int = -1

    override fun onBackPressedCustom() {
        if (event_form_alarm_radio_group.checkedRadioButtonId == R.id.event_form_alarm_custom
            && event_form_alarm_custom_layout.isVisible) {
            //Change view
            //TODO set to last selected before clicking custom radio button ?
            if (lastSelectedRadioButtonId != -1) event_form_alarm_radio_group.check(lastSelectedRadioButtonId)
            else event_form_alarm_radio_group.clearCheck()
            event_form_alarm_radio_group.visibleOrGone(true)
            event_form_alarm_custom_layout.visibleOrGone(false)
            toolbarTitle.text = getString(R.string.event_alarms_title)
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        val buttonDone = layoutInflater.inflate(R.layout.toolbar_action_text, dialog_toolbar_content, false)
        with (buttonDone) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(R.string.action_done)
            setOnSingleClickListener {
                onDoneClick()
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            addView(
                buttonDone, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun onDoneClick() {
        Timber.d("notification create/edit done")

        val alarmTypeOption = getCheckedRadioButtonIndex(event_form_alarm_radio_group)

        val option =
            if (getCheckedRadioButtonIndex(event_form_alarm_action_radio_group) == 0)
                EventViewModel.SendByOption.NOTIFICATION
            else EventViewModel.SendByOption.EMAIL
        eventViewModel.handleAlarmSendBy(option) // 0 -- notification (default), 1 -- email

        val countTypeOption = getCheckedRadioButtonIndex(custom_alarm_radio_group)
        // countTypeOption with value at 4 is used for "on the day" option
        eventViewModel.handleAlarm(alarmTypeOption,
            count = custom_alarm_field.text.toString().toIntOrNull() ?:
            if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
            else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
            countTypeOption = if (countTypeOption == -1 && isAllDay) 4 else countTypeOption)
        findNavController().navigateUp()

        // TODO
        // copy all values edited here to VM, before this they should be ephemeral, but we should keep in memory edited-not-saved
        // notifications when switching between custom and canned ones
    }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetAlarmCustomText(count: Int) {
        val selectedIndex = getCheckedRadioButtonIndex(custom_alarm_radio_group)
        val label = R.string.event_alarm_label
        val labelSelected = R.string.event_alarm_label_before

        custom_alarm_3.text = getString(if (selectedIndex == 2) labelSelected else label, resources.getQuantityString(R.plurals.plural_day, count, count))
        custom_alarm_4.text = getString(if (selectedIndex == 3) labelSelected else label, resources.getQuantityString(R.plurals.plural_week, count, count))
        if (!isAllDay) {
            custom_alarm_1.text = getString(if (selectedIndex == 0) labelSelected else label, resources.getQuantityString(R.plurals.plural_minute, count, count))
            custom_alarm_2.text = getString(if (selectedIndex == 1) labelSelected else label, resources.getQuantityString(R.plurals.plural_hour, count, count))
        }
    }

    private fun resetAlarmText(selectedIndex: Int) {

        val is24Hour = eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))

        if (isAllDay) { // TODO refactor and extract common formatting code to helpers -- pass timezone, locale and am/pm setting for later
            event_form_alarm_1.text = getString(R.string.event_alarm_all_day_1, LocalTime.of(9, 0).formatTime(is24Hour))
            event_form_alarm_2.text = getString(R.string.event_alarm_all_day_2, LocalTime.of(18, 0).formatTime(is24Hour))
            event_form_alarm_3.text = getString(R.string.event_alarm_all_day_3, LocalTime.of(9, 0).formatTime(is24Hour))
            event_form_alarm_4.text = getString(R.string.event_alarm_all_day_4, LocalTime.of(9, 0).formatTime(is24Hour))
        } else {
            event_form_alarm_1.text = getString(R.string.event_alarm_partial_day_1)
            event_form_alarm_2.text = getString(R.string.event_alarm_partial_day_2)
            event_form_alarm_3.text = getString(R.string.event_alarm_partial_day_3)
            event_form_alarm_4.text = getString(R.string.event_alarm_partial_day_4)
            event_form_alarm_5.text = getString(R.string.event_alarm_partial_day_5)
            if (selectedIndex != -1 && selectedIndex != R.id.event_form_alarm_1) {
                val radioButton = event_form_alarm_radio_group.findViewById<RadioButton>(selectedIndex)
                radioButton.text = getString(R.string.event_alarm_label_before, radioButton.text)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarTitle = toolbar.findViewById(R.id.dialog_toolbar_title)
        toolbarTitle.text = getString(R.string.event_alarms_title)
        toolbar.setNavigationIcon(R.drawable.ic_close)

        event_form_alarm_5.visibleOrGone(!isAllDay)
        resetAlarmText(-1)

        event_form_alarm_radio_group.setCustomOnCheckedChangeListener { _, index ->
            when (index) {
                R.id.event_form_alarm_custom -> {
                    toolbarTitle.text = getString(R.string.event_custom_alarms_title)

                    //Change view
                    event_form_alarm_radio_group.visibleOrGone(false)
                    event_form_alarm_custom_layout.visibleOrGone(true)
                }
                else -> {
                    resetAlarmText(index)
                    lastSelectedRadioButtonId = index
                }
            }
        }
        event_form_alarm_action_radio_group.setCustomOnCheckedChangeListener { radioGroup, index ->
            requireActivity().clearFocusAndHideKeyboard(view)
        }

        event_form_alarm_radio_group.check(event_form_alarm_1.id) // TODO read from event alarm
        event_form_alarm_action_radio_group.check(event_form_alarm_action_notification.id) // TODO read from event alarm

        var lastSelectedIndex: Int? = null

        custom_alarm_radio_group.setCustomOnCheckedChangeListener { radioGroup, index ->

            // prevent infinite loop when resetting adapters by EditText changes and Spinner selection
            if (lastSelectedIndex != null && lastSelectedIndex == index) {
                return@setCustomOnCheckedChangeListener
            }
            lastSelectedIndex = index

            if (index != -1) custom_alarm_same_day.isChecked = false

            requireActivity().clearFocusAndHideKeyboard(view)

            when (getCheckedRadioButtonIndex(custom_alarm_radio_group)) {
                0 -> { // minute
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_MINUTES
                    )
                }
                1 -> { // hour
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_HOURS
                    )
                }
                2 -> { // day
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_DAYS
                    )
                }
                3 -> { // week
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_WEEKS
                    )
                }
                // Hide before label if no button is checked
                else ->
                    resetAlarmCustomText(
                        if (custom_alarm_field.text.isNullOrEmpty()) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else custom_alarm_field.text.toString().toInt()
                    )
            }
        }

        custom_alarm_same_day.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) custom_alarm_radio_group.clearCheck()
            else button.jumpDrawablesToCurrentState()
        }

        val is24Hour = eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))

        // init
        custom_alarm_1.visibleOrGone(!isAllDay)
        custom_alarm_2.visibleOrGone(!isAllDay)
        custom_alarm_time_layout.visibleOrGone(isAllDay)
        custom_alarm_same_day_layout.visibleOrGone(isAllDay)
        if (isAllDay) {
            custom_alarm_field.setText("1")
            custom_alarm_radio_group.check(custom_alarm_3.id)
            resetAlarmCustomText(1)
            custom_alarm_time.text = getString(R.string.event_alarm_at_time, eventViewModel.tempAlarmTime.formatTime(is24Hour))
        } else {
            custom_alarm_field.setText("15")
            custom_alarm_radio_group.check(custom_alarm_1.id)
            resetAlarmCustomText(15)
        }
        custom_alarm_field_layout.setEndIconOnClickListener {
            custom_alarm_field.setText(
                if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT.toString()
                else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT.toString())
        }

        custom_alarm_time_press.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            AndroidUtils.displayTimePicker(requireContext(), LocalTime.now(), is24Hour) {
                eventViewModel.handleAlarmTime(it)
                custom_alarm_time.text = getString(R.string.event_alarm_at_time, it.formatTime(is24Hour))
            }
        }
    }

    // we keep track of TextWatcher so we can remove it when resetting alarm validation
    var alarmCustomFieldTextWatcher: TextWatcher? = null

    private fun resetAlarmCountValidation(default: Int, min: Int, max: Int) {
        // remove current alarm count text watcher
        alarmCustomFieldTextWatcher?.let { custom_alarm_field.removeTextChangedListener(it) }

        // set new text watcher with new config
        alarmCustomFieldTextWatcher =
            custom_alarm_field.doAfterFilteredIntValueChanged(default, min, max) {
                resetAlarmCustomText(it)
            }

        // set current value again because it might be outside of newly set limits
        custom_alarm_field.setText(custom_alarm_field.text)
    }
}
