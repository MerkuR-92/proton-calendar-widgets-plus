package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.children
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.util.Frequency
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.fragment_event_create_edit_recurrence.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.NoLayoutRadioGroup
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.text.DateFormat
import java.time.LocalDate

class EventCreateEditRecurrenceFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventCreateEditRecurrenceFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_create_edit_recurrence

    override val actionMenuResourceId = R.menu.fragment_event_create_edit_recurrence
    override val navigateUp = false

    override fun onMenuItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.action_menu_done) {

            when (rg_recurrence.checkedRadioButtonId) {
                R.id.rb_recurrence_1 -> {
                    eventViewModel.handleRecurrence(null, untilDate = false)
                }
                R.id.rb_recurrence_2 -> {
                    eventViewModel.handleRecurrence(Frequency.DAILY, untilDate = false)
                }
                R.id.rb_recurrence_3 -> {
                    eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDate = false)
                }
                R.id.rb_recurrence_4 -> {
                    eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDate = false)
                }
                R.id.rb_recurrence_5 -> {
                    eventViewModel.handleRecurrence(Frequency.YEARLY, untilDate = false)
                }
                R.id.rb_recurrence_custom -> {

                    var untilDateChecked = false
                    var thisManyRepeats: Int? = null

                    // "repeat until" options, apply to all recurrence periods
                    when (customEndingRadioGroup.getCheckedRadioButtonId()) {
                        R.id.rb_recurrence_custom_1 -> {} // ends: never
                        R.id.rb_recurrence_custom_2 -> { // ends: on specific Date
                            untilDateChecked = true
                        }
                        R.id.rb_recurrence_custom_3 -> { // ends: after X occurrences
                            thisManyRepeats = et_custom_recurrence_count.text.toString().toIntOrNull() ?: FormValidation.OCCURRENCE_COUNT_DEFAULT // TODO force when null?
                        }
                    }

                    // "repeat X times"
                    when (s_recurrence_period.selectedItemPosition) {
                        0 -> { // days
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                            eventViewModel.handleRecurrence(Frequency.DAILY, untilDateChecked, interval, thisManyRepeats)
                        }
                        1 -> { // weeks
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_WEEK_COUNT_DEFAULT

                            // weekdays for occurence
                            val dayNamesStartingIndex = if (eventViewModel.startWeekOnMonday) 1 else 0
                            val daysOfWeek = (ll_chips_day_of_week as ViewGroup).children.mapIndexedNotNull() { index, chip ->
                                if ((chip as Chip).isChecked) {
                                    biweekly.util.DayOfWeek.values()[(dayNamesStartingIndex + index) % 7]
                                } else null
                            }.toList()

                            eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = daysOfWeek, customMonthly = false)
                        }
                        2 -> { // months
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT

                            eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = null, customMonthly = true)
                        }
                        3 -> { // years
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                            eventViewModel.handleRecurrence(Frequency.YEARLY, untilDateChecked, interval, thisManyRepeats)
                        }
                    }

                }
            }

            findNavController().navigateUp()
        }
    }

    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by sharedViewModel() //inject()

    // index of the day of the week of Event start, used for forcing weekday picker to have it always picked
//    private val indexOfEventStartDay by lazy { (eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.dayOfWeek.ordinal + if (eventViewModel.startWeekOnMonday) 0 else 1) % 7 }

    lateinit var customEndingRadioGroup: NoLayoutRadioGroup


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attachActionHandlers()
        observeEventLiveData() // TODO maybe this could be removed if this form is not reactive

    }

    private fun attachActionHandlers() {

        // main recurrence type radio group
        rg_recurrence.setOnCheckedChangeListener { radioGroup, index ->
            group_custom.visibleOrGone(false)
            tv_custom_occurrence.visibleOrGone(false)
            group_custom_occurrence.visibleOrGone(false)
            ll_chips_day_of_week.visibleOrGone(false)

            if (index == R.id.rb_recurrence_custom) {
                group_custom.visibleOrGone(true)
                showCustomRecurrenceForms(s_recurrence_period.selectedItemPosition)
            }
        }

        // custom recurrence spinner ("repeats every X days/weeks/months...")
        setupCustomRecurrenceSpinnerComponent()

        // custom ending radio group ("ends on date/after X occurrences...")
        setupCustomEndingRadioGroup()
    }

    private fun setupCustomEndingRadioGroup() {
        customEndingRadioGroup = NoLayoutRadioGroup {
            // handle checks and focus
            if (it == R.id.rb_recurrence_custom_3) {
                if (!et_custom_recurrence_count.hasFocus()) et_custom_recurrence_count.requestFocus()
            } else {
                et_custom_recurrence_count.clearFocus()
            }

            val startDate = eventViewModel.tempRecurrenceUntilLocalDate
                ?: eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!
                    .toLocalDate()

            // handle click on day picker
            if (it == R.id.rb_recurrence_custom_2) {
                AndroidUtils.displayDatePicker(
                    requireContext(),
                    startDate,
                    startDate,
                    FormValidation.OCURRENCE_MAX_UNTIL.toLocalDate()
                ) {
                    eventViewModel.handleRecurrenceUntilDate(it)
                    rb_recurrence_custom_2.setText(
                        getString(
                            R.string.event_recurrence_ends_on_date,
                            it.format()
                        )
                    )
                }
            }
        }

        customEndingRadioGroup.add(
            requireView().findViewById(R.id.rb_recurrence_custom_1),
            requireView().findViewById(R.id.rb_recurrence_custom_2),
            requireView().findViewById(R.id.rb_recurrence_custom_3)
        )

        customEndingRadioGroup.check(R.id.rb_recurrence_custom_1)

        // "after X occurrences" radio button
        tv_recurrence_custom_suffix.setText(
            resources.getQuantityString(
                R.plurals.plural_occurrence,
                FormValidation.OCCURRENCE_COUNT_DEFAULT
            )
        )

        // "after X occurrences" edit text
        et_custom_recurrence_count.setText(FormValidation.OCCURRENCE_COUNT_DEFAULT.toString())
        et_custom_recurrence_count.apply {
            doAfterFilteredIntValueChanged(
                FormValidation.OCCURRENCE_COUNT_DEFAULT,
                FormValidation.OCCURRENCE_COUNT_MIN,
                FormValidation.OCCURRENCE_COUNT_MAX
            ) {
                tv_recurrence_custom_suffix.setText(
                    resources.getQuantityString(
                        R.plurals.plural_occurrence,
                        it
                    )
                )
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    customEndingRadioGroup.check(R.id.rb_recurrence_custom_3)
                }
            }
        }

        tv_recurrence_custom_suffix.setOnClickListener {
            customEndingRadioGroup.check(R.id.rb_recurrence_custom_3)
            et_custom_recurrence_count.requestFocus()
        }
    }

    private fun setupCustomRecurrenceSpinnerComponent() {
        s_recurrence_period.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            var lastSelectedIndex: Int? = null

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {

                showCustomRecurrenceForms(position)

                // prevent infinite loop when resetting adapters by EditText changes and Spinner selection
                if (lastSelectedIndex != null && lastSelectedIndex == position) {
                    return
                }
                lastSelectedIndex = position

                when (position) {
                    0 -> { // day
                        resetRecurrenceCountValidation(
                            FormValidation.INTERVAL_DAY_COUNT_DEFAULT,
                            FormValidation.INTERVAL_DAY_COUNT_MIN,
                            FormValidation.INTERVAL_DAY_COUNT_MAX
                        )
                    }
                    1 -> { // week
                        resetRecurrenceCountValidation(
                            FormValidation.INTERVAL_WEEK_COUNT_DEFAULT,
                            FormValidation.INTERVAL_WEEK_COUNT_MIN,
                            FormValidation.INTERVAL_WEEK_COUNT_MAX
                        )
                    }
                    2 -> { // month
                        resetRecurrenceCountValidation(
                            FormValidation.INTERVAL_MONTH_COUNT_DEFAULT,
                            FormValidation.INTERVAL_MONTH_COUNT_MIN,
                            FormValidation.INTERVAL_MONTH_COUNT_MAX
                        )
                    }
                    3 -> { // year
                        resetRecurrenceCountValidation(
                            FormValidation.INTERVAL_YEAR_COUNT_DEFAULT,
                            FormValidation.INTERVAL_YEAR_COUNT_MIN,
                            FormValidation.INTERVAL_YEAR_COUNT_MAX
                        )
                    }
                }

            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // init with WEEK
        et_recurrence_count.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
        resetRecurrencePeriodAdapter(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
        s_recurrence_period.setSelection(1)

        val dayNamesStartingIndex = if (eventViewModel.startWeekOnMonday) 1 else 0

        val byDayIndices = eventViewModel.eventLiveData.value!!.iCalEvent?.recurrenceRule?.value?.byDay?.map { (it.day.ordinal + 7 - dayNamesStartingIndex) % 7 } ?: emptyList()

        val indexOfEventStartDay = (eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.dayOfWeek.ordinal + if (eventViewModel.startWeekOnMonday) 0 else 1) % 7
        val checkedDayIndices: List<Int> = byDayIndices + indexOfEventStartDay

        resources.getStringArray(R.array.days_of_week_letters)
            .slice(dayNamesStartingIndex..(dayNamesStartingIndex + 6))
            .forEachIndexed { index, dayName ->
                ((ll_chips_day_of_week as ViewGroup).getChildAt(index) as Chip).apply {
                    text = dayName
                    isClickable = (index != indexOfEventStartDay) // we disable and check by default the day of event's start
                    isChecked = (index in checkedDayIndices)
                }
            }

        // TODO get this from VM
        val eventStartDate =
            eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!
                .toLocalDate()

        // applies only to month
        val monthlyRecurrenceOn = mapMonthlyRecurrenceOnToString(eventStartDate, eventViewModel.tempMonthlyRepeatOption)
        tv_occurrence_time.setText(monthlyRecurrenceOn)

        press_recurrence_time.setOnClickListener {
            val optionsRepeatOn = eventViewModel.calculateMonthlyRepeatOnOptions()

            AndroidUtils.displaySingleChoicePicker(
                requireContext(), getString(R.string.event_recurrence_occurs_on),
                optionsRepeatOn.map {
                    mapMonthlyRecurrenceOnToString(eventStartDate, it)
                }.toTypedArray(), eventViewModel.calculateMonthlyRepeatOnOptionIndex()
            ) {
                eventViewModel.handleRecurrenceRepeatOn(it)
                tv_occurrence_time.setText(
                    mapMonthlyRecurrenceOnToString(
                        eventStartDate,
                        optionsRepeatOn[it]
                    )
                )
            }

        }
    }

    private fun mapMonthlyRecurrenceOnToString(eventStartDate: LocalDate, option: EventViewModel.MonthlyRepatOnOption): String {
        return when(option) {
            EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> getString(
                R.string.event_recurrence_occurs_monthly_on_day,
                eventStartDate.dayOfMonth
            )
            EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> getString(
                R.string.event_recurrence_occurs_on_monthly_on_x_day_of_week,
                eventStartDate.formatMonthlyDayOfWeek(resources)
            )
            EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> getString(
                R.string.event_recurrence_occurs_on_monthly_on_x_day_of_week,
                eventStartDate.formatMonthlyDayOfWeek(resources, backwards = true)
            )
        }
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer {

            TimberLogger.d("event: ${it.iCalendar.printToString()}")

            val radioButtonId = when (it.iCalEvent.recurrenceRule?.value?.frequency) {
                Frequency.DAILY -> R.id.rb_recurrence_2
                Frequency.WEEKLY -> R.id.rb_recurrence_3
                Frequency.MONTHLY -> R.id.rb_recurrence_4
                Frequency.YEARLY -> R.id.rb_recurrence_5
                else -> R.id.rb_recurrence_1
            }

            rg_recurrence.check(
                if (it.isCustomRecurring()) { // handle custom recurrence rule

                    // TODO get this from VM
                    val eventStartDate =
                        eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!
                            .toLocalDate()

                    val monthlyRecurrenceOn = mapMonthlyRecurrenceOnToString(eventStartDate, eventViewModel.calculateMonthlyRepeatOnOptions()[eventViewModel.calculateMonthlyRepeatOnOptionIndex()])
                    tv_occurrence_time.setText(monthlyRecurrenceOn)

                    // ll_chips_day_of_week is initialised when populating spinner

                    customEndingRadioGroup.check(R.id.rb_recurrence_custom_1) // default

                    eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.run {
                        // "ends" section
                        if (this.until != null) {
                            customEndingRadioGroup.check(R.id.rb_recurrence_custom_2)
                            rb_recurrence_custom_2.setText(getString(
                                    R.string.event_recurrence_ends_on_date,
                                    DateFormat.getDateInstance(
                                        DateFormat.LONG
                                    ).format(this.until))
                            )
                        }
                        if (this.count != null) {
                            customEndingRadioGroup.check(R.id.rb_recurrence_custom_3)
                            et_custom_recurrence_count.setText("${this.count}")
                        }

                        // "repeats every" section
                        when (this.frequency) {
                            Frequency.WEEKLY -> {
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_WEEK_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
                                s_recurrence_period.setSelection(1)
                            }
                            Frequency.MONTHLY -> {
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT)
                                s_recurrence_period.setSelection(2)
                            }
                            Frequency.YEARLY -> {
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_YEAR_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT)
                                s_recurrence_period.setSelection(3)
                            }
                            else -> { // fallback to Frequency.DAILY
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_DAY_COUNT_DEFAULT)
                                s_recurrence_period.setSelection(0)
                            }
                        }

                    }

                    // return radio button to check
                    R.id.rb_recurrence_custom
                } else radioButtonId
            )

        })
    }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetRecurrencePeriodAdapter(count: Int) {

        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayListOf<String>(
                resources.getQuantityString(R.plurals.plural_day, count, count),
                resources.getQuantityString(R.plurals.plural_week, count, count),
                resources.getQuantityString(R.plurals.plural_month, count, count),
                resources.getQuantityString(R.plurals.plural_year, count, count)
            )
        )

        s_recurrence_period.apply {
            val selectedIndex = s_recurrence_period.selectedItemPosition
            setAdapter(adapter)
            setSelection(selectedIndex)
        }

    }

    // we keep track of TextWatcher so we can remove it when resetting recurrence validation
    var etRecurrenceTextWatcher: TextWatcher? = null

    private fun resetRecurrenceCountValidation(default: Int, min: Int, max: Int) {

        // remove current recurrence count text watcher
        etRecurrenceTextWatcher?.let { et_recurrence_count.removeTextChangedListener(it) }

        // set new text watcher with new config
        etRecurrenceTextWatcher =
            et_recurrence_count.doAfterFilteredIntValueChanged(default, min, max) {
                resetRecurrencePeriodAdapter(it)
            }

        // set current value again because it might be outside of newly set limits
        et_recurrence_count.setText(et_recurrence_count.text)
    }

    /**
     * Shows or hides custom components for different recurrence types.
     */
    private fun showCustomRecurrenceForms(recurrenceTypeIndex: Int) {
        when (recurrenceTypeIndex) {
            0 -> { // day
                tv_custom_occurrence.visibleOrGone(false)
                group_custom_occurrence.visibleOrGone(false)
                ll_chips_day_of_week.visibleOrGone(false)
            }
            1 -> { // week
                tv_custom_occurrence.visibleOrGone(true)
                group_custom_occurrence.visibleOrGone(false)
                ll_chips_day_of_week.visibleOrGone(true)
            }
            2 -> { // month
                tv_custom_occurrence.visibleOrGone(true)
                group_custom_occurrence.visibleOrGone(true)
                ll_chips_day_of_week.visibleOrGone(false)
            }
            3 -> { // year
                tv_custom_occurrence.visibleOrGone(false)
                group_custom_occurrence.visibleOrGone(false)
                ll_chips_day_of_week.visibleOrGone(false)
            }
        }
    }

}
