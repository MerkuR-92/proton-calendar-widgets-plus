package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import kotlinx.android.synthetic.main.fragment_event_create_edit_alarm.*
import kotlinx.android.synthetic.main.fragment_event_create_edit_alarm.group_custom
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
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

             val alarmTypeOption = rg_alarm.checkedRadioButtonId - rb_alarm_1.id

//                 GET RADIO BUTTONS, IF == 1 then hardcode 9:00 in VM
//             val customPeriod =
//             val customCount = et_alarm_count.text.toString().toIntOrNull() ?: FormValidation.
//                 val customTime = nullable

             eventViewModel.handleAlarm(alarmTypeOption, count = et_alarm_count.text.toString().toIntOrNull(), countTypeOption = s_alarm_period.selectedItemPosition)
             findNavController().navigateUp()

             // TODO
             // copy all values edited here to VM, before this they should be ephemeral, but we should keep in memory edited-not-saved
             // notifications when switching between custom and canned ones
         }
    }

    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by sharedViewModel()

    private val isAllDay by lazy { eventViewModel.eventLiveData.value!!.isAllDay() }

    private fun onAlarmCountChanged(count: Int) {
        TimberLogger.d("count=$count")
    }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetAlarmPeriodAdapter(count: Int) {

        val adapter = if (isAllDay) {
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayListOf(
                getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_day, count)),
                getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_week, count))
            ))
        } else {
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayListOf<String>(
                getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_minute, count, count)),
                getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_hour, count, count)),
                getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_day, count, count)),
                getString(R.string.event_alarm_label_before, resources.getQuantityString(R.plurals.plural_week, count, count))
            ))
        }

        s_alarm_period.apply {
            val selectedIndex = s_alarm_period.selectedItemPosition
            setAdapter(adapter)
            setSelection(selectedIndex)
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isAllDay) { // TODO refactor and extract common formatting code to helpers -- pass timezone, locale and am/pm setting for later
            rb_alarm_1.text = getString(R.string.event_alarm_all_day_1, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            rb_alarm_2.text = getString(R.string.event_alarm_all_day_2, LocalTime.of(18, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            rb_alarm_3.text = getString(R.string.event_alarm_all_day_3, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            rb_alarm_4.text = getString(R.string.event_alarm_all_day_4, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            rb_alarm_5.visibleOrGone(false)
        } else {
            rb_alarm_1.text = getString(R.string.event_alarm_partial_day_1)
            rb_alarm_2.text = getString(R.string.event_alarm_partial_day_2)
            rb_alarm_3.text = getString(R.string.event_alarm_partial_day_3)
            rb_alarm_4.text = getString(R.string.event_alarm_partial_day_4)
            rb_alarm_5.visibleOrGone(true)
            rb_alarm_5.text = getString(R.string.event_alarm_partial_day_5)
        }

        rg_alarm.check(rb_alarm_1.id) // TODO read from event alarm

        rg_alarm.setOnCheckedChangeListener { radioGroup, index ->

            group_custom.visibleOrGone(false)
            group_custom_time.visibleOrGone(false)

            when (index) {
                R.id.rb_alarm_custom -> {
                    if (isAllDay) {
                        group_custom.visibleOrGone(true)
                        group_custom_time.visibleOrGone(true)
                    } else {
                        group_custom.visibleOrGone(true)
                        group_custom_time.visibleOrGone(false)
                    }
                }
            }
        }

        s_alarm_period.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            var lastSelectedIndex: Int? = null

            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {

                // prevent infinite loop when resetting adapters by EditText changes and Spinner selection
                if (lastSelectedIndex != null && lastSelectedIndex == position) {
                    return
                }
                lastSelectedIndex = position

                // all-day events have only days and weeks, partial-day events have minutes, hours, days and weeks
                //  this is an offset for Alarm Count Validation reset
                val positionAdjustedForEventType = position + (if (isAllDay) 2 else 0)

                 when (positionAdjustedForEventType) {
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

            override fun onNothingSelected(p0: AdapterView<*>?) {}

        }

        // init
        if (isAllDay) {
            et_alarm_count.setText("1")
            resetAlarmPeriodAdapter(1)
            s_alarm_period.setSelection(0)
            tv_alarm_time.text = eventViewModel.tempAlarmTime.format()
        } else {
            et_alarm_count.setText("15")
            resetAlarmPeriodAdapter(15)
            s_alarm_period.setSelection(0)
        }








        press_alarm_time.setOnClickListener {
            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future

            AndroidUtils.displayTimePicker(requireContext(), LocalTime.now(), is24Hour) {
                eventViewModel.handleAlarmTime(it)
                tv_alarm_time.text = it.format()
            }
        }

        press_alarm_action.setOnClickListener {
            val actions = arrayOf(resources.getString(R.string.event_alarm_action_notification), resources.getString(R.string.event_alarm_action_email))
            AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.event_alarm_action), actions, eventViewModel.tempAlarmSendByOption.ordinal) {
                val option = if (it == 0) EventViewModel.SendByOption.NOTIFICATION else EventViewModel.SendByOption.EMAIL

                eventViewModel.handleAlarmSendBy(option) // 0 -- notification (default), 1 -- email
                tv_alarm_action.text = formatNotification(option)
            }
        }

        tv_alarm_action.text = formatNotification(eventViewModel.tempAlarmSendByOption)

    }

    // we keep track of TextWatcher so we can remove it when resetting alarm validation
    var etAlarmTextWatcher: TextWatcher? = null

    private fun resetAlarmCountValidation(default: Int, min: Int, max: Int) {

        // remove current alarm count text watcher
        etAlarmTextWatcher?.let { et_alarm_count.removeTextChangedListener(it) }

        // set new text watcher with new config
        etAlarmTextWatcher =
            et_alarm_count.doAfterFilteredIntValueChanged(default, min, max) {
                resetAlarmPeriodAdapter(it)
            }

        // set current value again because it might be outside of newly set limits
        et_alarm_count.setText(et_alarm_count.text)
    }

    private fun formatNotification(option: EventViewModel.SendByOption): String {
        return when (option) {
            EventViewModel.SendByOption.NOTIFICATION -> resources.getString(R.string.event_alarm_action_notification)
            EventViewModel.SendByOption.EMAIL -> resources.getString(R.string.event_alarm_action_email)
        }
    }

}
