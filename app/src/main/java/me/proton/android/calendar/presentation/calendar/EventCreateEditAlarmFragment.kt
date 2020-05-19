package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.navigation.fragment.navArgs
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import kotlinx.android.synthetic.main.fragment_event_create_edit_alarm.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class EventCreateEditAlarmFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventCreateEditAlarmFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_create_edit_alarm

    override val actionMenuResourceId = R.menu.fragment_event_create_edit_dialog

    override fun onMenuItemClicked(menuItem: MenuItem) {
         if (menuItem.itemId == R.id.action_menu_done) {
             // TODO
             // copy all values edited here to VM, before this they should be ephemeral, but we should keep in memory edited-not-saved
             // notifications when switching between custom and canned ones
         }
    }

    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()

    val isAllDay = true // TODO FIXME

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
            rb_alarm_2.text = getString(R.string.event_alarm_all_day_2, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            rb_alarm_3.text = getString(R.string.event_alarm_all_day_3, LocalTime.of(18, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            rb_alarm_4.text = getString(R.string.event_alarm_all_day_4, LocalTime.of(9, 0).format(DateTimeFormatter.ofLocalizedTime(
                FormatStyle.SHORT)))
            // TODO CHANGE NAME TO "ALARM" NOT NOTIFICATION

        } else {
            rb_alarm_1.text = getString(R.string.event_alarm_partial_day_1)
            rb_alarm_2.text = getString(R.string.event_alarm_partial_day_2)
            rb_alarm_3.text = getString(R.string.event_alarm_partial_day_3)
            rb_alarm_4.text = getString(R.string.event_alarm_partial_day_4)
        }

        et_alarm_count.doAfterFilteredIntValueChanged(
            VALIDATION_ALARM_PERIOD_COUNT_DEFAULT,
            VALIDATION_ALARM_PERIOD_COUNT_MIN,
            VALIDATION_ALARM_PERIOD_COUNT_MAX) {

            onAlarmCountChanged(it)
            resetAlarmPeriodAdapter(it)
        }

        et_alarm_count.setText("1")

        rg_alarm.setOnCheckedChangeListener { radioGroup, index ->

            group_custom.visibleOrGone(false)
            group_custom_time.visibleOrGone(false)

            when (index) {

                R.id.rb_alarm_1 -> {

                }
                R.id.rb_alarm_2 -> {

                }
                R.id.rb_alarm_3 -> {


                }
                R.id.rb_alarm_4 -> {

                }
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

        press_alarm_time.setOnClickListener {
            val is24Hour = DateFormat.is24HourFormat(requireContext()) // TODO this is default, take it from settings in the future

            // TODO init with 9:00 or already defined time if editing
            AndroidUtils.displayTimePicker(requireContext(), LocalTime.now(), is24Hour) {
                TimberLogger.d("${it}")
            }
        }

        press_alarm_action.setOnClickListener {
            // TODO init with already chosen action when editing
            val actions = resources.getStringArray(R.array.alarm_actions)
            AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.event_alarm_action), actions, 0) {
                TimberLogger.d("alarm action: ${actions[it]}")
            }
        }
    }

}