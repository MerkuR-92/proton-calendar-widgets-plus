package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.util.Frequency
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.chip_group_day_of_week.*
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
import java.time.ZoneId
import java.time.ZonedDateTime

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
                    when (getCheckedRadioButtonIndex(rg_recurrence_period)) {
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
                            val daysOfWeek = (chip_group_day_of_week_layout as ViewGroup).children.mapIndexedNotNull() { index, chip ->
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
            rg_occurrence_time.visibleOrGone(false)
            group_custom_occurrence.visibleOrGone(false)
            ll_chips_day_of_week.visibleOrGone(false)

            if (index == R.id.rb_recurrence_custom) {
                rg_recurrence.visibleOrGone(false)
                group_custom.visibleOrGone(true)
                showCustomRecurrenceForms(getCheckedRadioButtonIndex(rg_recurrence_period))
            }
        }

        rb_recurrence_custom_1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requireActivity().clearFocusAndHideKeyboard(view)
        }
        rb_recurrence_custom_2.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requireActivity().clearFocusAndHideKeyboard(view)
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

            val eventStartDate = eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!
                .toLocalDate()

            val currentRecurrenceUntilInstant = eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.until?.toInstant()

            val untilDate = eventViewModel.tempRecurrenceUntilLocalDate
                ?: if (currentRecurrenceUntilInstant != null) {
                    ZonedDateTime.ofInstant(currentRecurrenceUntilInstant, ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate()
                } else eventStartDate

            // handle click on day picker
            if (it == R.id.rb_recurrence_custom_2) {
                AndroidUtils.displayDatePicker(
                    requireContext(),
                    untilDate,
                    eventStartDate,
                    FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate()
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
        var lastSelectedIndex: Int? = null

        rg_recurrence_period.setOnCheckedChangeListener { radioGroup, index ->
            requireActivity().clearFocusAndHideKeyboard(view)

            val position = getCheckedRadioButtonIndex(rg_recurrence_period)
            showCustomRecurrenceForms(position)

            // prevent infinite loop when resetting adapters by EditText changes and Spinner selection
            if (lastSelectedIndex != null && lastSelectedIndex == position) {
                return@setOnCheckedChangeListener
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

        // init with WEEK
        et_recurrence_count.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
        resetRecurrencePeriodAdapter(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
        rg_recurrence_period.check(rb_recurrence_period_2.id)

        et_recurrence_count_layout.setEndIconOnClickListener {
            when (getCheckedRadioButtonIndex(rg_recurrence_period)) {
                // day
                0 -> et_recurrence_count.setText(FormValidation.INTERVAL_DAY_COUNT_DEFAULT.toString())
                // week
                1 -> et_recurrence_count.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
                // month
                2 -> et_recurrence_count.setText(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT.toString())
                // year
                3 -> et_recurrence_count.setText(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT.toString())
            }
        }

        val dayNamesStartingIndex = if (eventViewModel.startWeekOnMonday) 1 else 0

        val byDayIndices =
            eventViewModel.eventLiveData.value!!.iCalEvent?.recurrenceRule?.value?.byDay?.map { (it.day.ordinal + 7 - dayNamesStartingIndex) % 7 }
                ?: emptyList()

        val indexOfEventStartDay =
            (eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!.dayOfWeek.ordinal + if (eventViewModel.startWeekOnMonday) 0 else 1) % 7
        val checkedDayIndices: List<Int> = byDayIndices + indexOfEventStartDay

        resources.getStringArray(R.array.days_of_week_letters)
            .slice(dayNamesStartingIndex..(dayNamesStartingIndex + 6))
            .forEachIndexed { index, dayName ->
                ((chip_group_day_of_week_layout as ViewGroup).getChildAt(index) as Chip).apply {
                    text = dayName
                    isClickable =
                        (index != indexOfEventStartDay) // we disable and check by default the day of event's start
                    isChecked = (index in checkedDayIndices)
                }
            }

        // TODO get this from VM
        val eventStartDate =
            eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!
                .toLocalDate()

        // applies only to month
        rg_occurrence_time.check(
            when (eventViewModel.tempMonthlyRepeatOption) {
                EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> rb_occurrence_time_1.id
                EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> rb_occurrence_time_2.id
                EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> rb_occurrence_time_3.id
            }
        )

        val optionsRepeatOn = eventViewModel.calculateMonthlyRepeatOnOptions()
        optionsRepeatOn.forEach {
            when (it) {
                EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> {
                    rb_occurrence_time_1.visibleOrGone(true)
                    rb_occurrence_time_1.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                }
                EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> {
                    rb_occurrence_time_2.visibleOrGone(true)
                    rb_occurrence_time_2.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                }
                EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> {
                    rb_occurrence_time_3.visibleOrGone(true)
                    rb_occurrence_time_3.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                }
            }
        }

        rg_occurrence_time.setOnCheckedChangeListener { group, checkedId ->
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.handleRecurrenceRepeatOn(getCheckedRadioButtonIndex(rg_occurrence_time))
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
                        eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!
                            .toLocalDate()

                    rg_occurrence_time.check(
                        when (eventViewModel.calculateMonthlyRepeatOnOptions()[eventViewModel.calculateMonthlyRepeatOnOptionIndex()]) {
                            EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> rb_occurrence_time_1.id
                            EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> rb_occurrence_time_2.id
                            EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> rb_occurrence_time_3.id
                        }
                    )

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
                                rg_recurrence_period.check(rb_recurrence_period_2.id)
                            }
                            Frequency.MONTHLY -> {
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT)
                                rg_recurrence_period.check(rb_recurrence_period_3.id)
                            }
                            Frequency.YEARLY -> {
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_YEAR_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT)
                                rg_recurrence_period.check(rb_recurrence_period_4.id)
                            }
                            else -> { // fallback to Frequency.DAILY
                                et_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT}")
                                resetRecurrencePeriodAdapter(FormValidation.INTERVAL_DAY_COUNT_DEFAULT)
                                rg_recurrence_period.check(rb_recurrence_period_1.id)
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
        rb_recurrence_period_1.text = resources.getQuantityString(R.plurals.plural_day, count, count)
        rb_recurrence_period_2.text = resources.getQuantityString(R.plurals.plural_week, count, count)
        rb_recurrence_period_3.text = resources.getQuantityString(R.plurals.plural_month, count, count)
        rb_recurrence_period_4.text = resources.getQuantityString(R.plurals.plural_year, count, count)
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
                rg_occurrence_time.visibleOrGone(false)
                group_custom_occurrence.visibleOrGone(false)
                ll_chips_day_of_week.visibleOrGone(false)
            }
            1 -> { // week
                rg_occurrence_time.visibleOrGone(false)
                group_custom_occurrence.visibleOrGone(false)
                ll_chips_day_of_week.visibleOrGone(true)
            }
            2 -> { // month
                rg_occurrence_time.visibleOrGone(true)
                group_custom_occurrence.visibleOrGone(true)
                ll_chips_day_of_week.visibleOrGone(false)
            }
            3 -> { // year
                rg_occurrence_time.visibleOrGone(false)
                group_custom_occurrence.visibleOrGone(false)
                ll_chips_day_of_week.visibleOrGone(false)
            }
        }
    }

}
