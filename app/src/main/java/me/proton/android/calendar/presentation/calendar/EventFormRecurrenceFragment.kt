package me.proton.android.calendar.presentation.calendar

import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.util.Frequency
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.chip_group_day_of_week.*
import kotlinx.android.synthetic.main.event_form_custom_recurrence_view.*
import kotlinx.android.synthetic.main.fragment_base_dialog.*
import kotlinx.android.synthetic.main.fragment_event_form_recurrence.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.presentation.BaseDialogFragment
import me.proton.android.calendar.presentation.NoLayoutRadioGroup
import org.koin.android.viewmodel.ext.android.sharedViewModel
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.*
import kotlin.collections.HashMap

class EventFormRecurrenceFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventFormRecurrenceFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_recurrence

    override val navigateUp = false

    private lateinit var toolbarTitle: TextView

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by sharedViewModel() //inject()
    private lateinit var monthlyRecurrenceOnMap: HashMap<Int, EventViewModel.MonthlyRepatOnOption>

    // index of the day of the week of Event start, used for forcing weekday picker to have it always picked
    //private val indexOfEventStartDay by lazy { (eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.dayOfWeek.ordinal + if (eventViewModel.startWeekOnMonday) 0 else 1) % 7 }

    lateinit var customEndingRadioGroup: NoLayoutRadioGroup
    private var lastSelectedRadioButtonId: Int = -1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbarTitle = toolbar.findViewById(R.id.dialog_toolbar_title)
        toolbarTitle.text = getString(R.string.event_recurrence_title)

        attachActionHandlers()
        observeEventLiveData() // TODO maybe this could be removed if this form is not reactive

    }

    override fun onBackPressedCustom() {
        if (event_form_recurrence_radio_group.checkedRadioButtonId == R.id.event_form_recurrence_custom
            && event_form_recurrence_custom_layout.isVisible) {
            //TODO set to last selected before clicking custom radio button ?
            if (lastSelectedRadioButtonId != -1) event_form_recurrence_radio_group.check(lastSelectedRadioButtonId)
            else event_form_recurrence_radio_group.clearCheck()
            event_form_recurrence_radio_group.visibleOrGone(true)
            event_form_recurrence_custom_layout.visibleOrGone(false)
            toolbarTitle.text = getString(R.string.event_recurrence_title)
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
        when (event_form_recurrence_radio_group.checkedRadioButtonId) {
            R.id.event_form_recurrence_custom_edit -> {
                //Do nothing, we don't need to update recurrence and keep the custom one
            }
            R.id.event_form_recurrence_1 -> {
                eventViewModel.handleRecurrence(null, untilDate = false)
            }
            R.id.event_form_recurrence_2 -> {
                eventViewModel.handleRecurrence(Frequency.DAILY, untilDate = false)
            }
            R.id.event_form_recurrence_3 -> {
                eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDate = false)
            }
            R.id.event_form_recurrence_4 -> {
                eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDate = false)
            }
            R.id.event_form_recurrence_5 -> {
                eventViewModel.handleRecurrence(Frequency.YEARLY, untilDate = false)
            }
            R.id.event_form_recurrence_custom -> {

                var untilDateChecked = false
                var thisManyRepeats: Int? = null

                // "repeat until" options, apply to all recurrence periods
                when (customEndingRadioGroup.getCheckedRadioButtonId()) {
                    custom_recurrence_end_1.id -> {} // ends: never
                    custom_recurrence_end_2.id -> { // ends: on specific Date
                        untilDateChecked = true
                    }
                    custom_recurrence_end_3.id -> { // ends: after X occurrences
                        thisManyRepeats = custom_recurrence_end_count.text.toString().toIntOrNull() ?: FormValidation.OCCURRENCE_COUNT_DEFAULT // TODO force when null?
                    }
                }

                // "repeat X times"
                when (getCheckedRadioButtonIndex(custom_recurrence_period_radio_group)) {
                    0 -> { // days
                        val interval = custom_recurrence_count.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                        eventViewModel.handleRecurrence(Frequency.DAILY, untilDateChecked, interval, thisManyRepeats)
                    }
                    1 -> { // weeks
                        val interval = custom_recurrence_count.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_WEEK_COUNT_DEFAULT

                        // weekdays for occurrence
                        val daysOfWeek = (chip_group_day_of_week_layout as ViewGroup).children.mapIndexedNotNull() { index, chip ->
                            if ((chip as Chip).isChecked) {
                                val weekStart = if (eventViewModel.userSettings.weekStart == 0) WeekFields.of(Locale.getDefault()).firstDayOfWeek.value else eventViewModel.userSettings.weekStart
                                biweekly.util.DayOfWeek.values()[(index + weekStart) % 7]
                            } else null
                        }.toList()

                        eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = daysOfWeek, customMonthly = false)
                    }
                    2 -> { // months
                        val interval = custom_recurrence_count.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT

                        eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = null, customMonthly = true)
                    }
                    3 -> { // years
                        val interval = custom_recurrence_count.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                        eventViewModel.handleRecurrence(Frequency.YEARLY, untilDateChecked, interval, thisManyRepeats)
                    }
                }

            }
        }

        findNavController().navigateUp()
    }

    private fun attachActionHandlers() {

        // main recurrence type radio group
        event_form_recurrence_radio_group.setCustomOnCheckedChangeListener { radioGroup, index ->
            if (index == R.id.event_form_recurrence_custom) {
                event_form_recurrence_radio_group.visibleOrGone(false)
                event_form_recurrence_custom_layout.visibleOrGone(true)
                showCustomRecurrenceForms(getCheckedRadioButtonIndex(custom_recurrence_period_radio_group))
                toolbarTitle.text = getString(R.string.event_recurrence_custom_title)
            } else {
                lastSelectedRadioButtonId = index
            }
        }

        custom_recurrence_end_1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requireActivity().clearFocusAndHideKeyboard(view)
        }
        custom_recurrence_end_2.setOnCheckedChangeListener { _, isChecked ->
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
            if (it == R.id.custom_recurrence_end_3) {
                if (!custom_recurrence_end_count.hasFocus()) custom_recurrence_end_count.requestFocus()
            } else {
                custom_recurrence_end_count.clearFocus()
            }

            val eventStartDate = eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!
                .toLocalDate()

            val currentRecurrenceUntilInstant = eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.until?.toInstant()

            val untilDate = eventViewModel.tempRecurrenceUntilLocalDate
                ?: if (currentRecurrenceUntilInstant != null) {
                    ZonedDateTime.ofInstant(currentRecurrenceUntilInstant, ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate()
                } else eventStartDate

            // handle click on day picker
            if (it == R.id.custom_recurrence_end_2) {
                AndroidUtils.displayDatePicker(
                    requireContext(),
                    eventViewModel.userSettings.weekStartDayOfWeek(),
                    untilDate,
                    eventStartDate,
                    FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.displayTimeZoneId)).toLocalDate()
                ) {
                    eventViewModel.handleRecurrenceUntilDate(it)
                    custom_recurrence_end_2.setText(
                        getString(
                            R.string.event_recurrence_ends_on_date,
                            it.format()
                        )
                    )
                }
            }
        }

        customEndingRadioGroup.add(
            requireView().findViewById(R.id.custom_recurrence_end_1),
            requireView().findViewById(R.id.custom_recurrence_end_2),
            requireView().findViewById(R.id.custom_recurrence_end_3)
        )

        customEndingRadioGroup.check(R.id.custom_recurrence_end_1)

        // "after X occurrences" radio button
        custom_recurrence_end_count_suffix.setText(
            resources.getQuantityString(
                R.plurals.plural_occurrence,
                FormValidation.OCCURRENCE_COUNT_DEFAULT
            )
        )

        // "after X occurrences" edit text
        custom_recurrence_end_count.setText(FormValidation.OCCURRENCE_COUNT_DEFAULT.toString())
        custom_recurrence_end_count.apply {
            doAfterFilteredIntValueChanged(
                FormValidation.OCCURRENCE_COUNT_DEFAULT,
                FormValidation.OCCURRENCE_COUNT_MIN,
                FormValidation.OCCURRENCE_COUNT_MAX
            ) {
                custom_recurrence_end_count_suffix.setText(
                    resources.getQuantityString(
                        R.plurals.plural_occurrence,
                        it
                    )
                )
            }
        }

        custom_recurrence_end_count_suffix.setOnSingleClickListener {
            customEndingRadioGroup.check(R.id.custom_recurrence_end_3)
            custom_recurrence_end_count.requestFocus()
        }

        custom_recurrence_end_count.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                customEndingRadioGroup.check(R.id.custom_recurrence_end_3)
                custom_recurrence_end_count.setSelection(custom_recurrence_end_count.length())
                requireContext().showKeyboard()
            }
        }

        custom_recurrence_end_1.setOnCheckedChangeListener { button, isChecked -> if (!isChecked) button.jumpDrawablesToCurrentState() }
        custom_recurrence_end_2.setOnCheckedChangeListener { button, isChecked -> if (!isChecked) button.jumpDrawablesToCurrentState() }
        custom_recurrence_end_3.setOnCheckedChangeListener { button, isChecked -> if (!isChecked) button.jumpDrawablesToCurrentState() }
    }

    private fun setupCustomRecurrenceSpinnerComponent() {
        var lastSelectedIndex: Int? = null

        custom_recurrence_period_radio_group.setCustomOnCheckedChangeListener { radioGroup, index ->
            requireActivity().clearFocusAndHideKeyboard(view)

            val position = getCheckedRadioButtonIndex(custom_recurrence_period_radio_group)
            showCustomRecurrenceForms(position)

            // prevent infinite loop when resetting adapters by EditText changes and Radio group selection
            if (lastSelectedIndex != null && lastSelectedIndex == position) {
                return@setCustomOnCheckedChangeListener
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
        custom_recurrence_count.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
        resetRecurrencePeriodText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
        custom_recurrence_period_radio_group.check(custom_recurrence_period_2.id)

        custom_recurrence_count_layout.setEndIconOnClickListener {
            when (getCheckedRadioButtonIndex(custom_recurrence_period_radio_group)) {
                // day
                0 -> custom_recurrence_count.setText(FormValidation.INTERVAL_DAY_COUNT_DEFAULT.toString())
                // week
                1 -> custom_recurrence_count.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
                // month
                2 -> custom_recurrence_count.setText(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT.toString())
                // year
                3 -> custom_recurrence_count.setText(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT.toString())
            }
        }

        // TODO Check if can be improved

        // DayOfWeek of biweekly starts on Sunday (ordinal 0) and ends on Saturday (ordinal 6), we convert it to match java.time ordinals
        val byDayIndices =
            eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.byDay?.map { it.day.toDayOfWeek().ordinal } ?: emptyList()

        // DayOfWeek of java.time starts on Monday (ordinal 0) and ends on Sunday (ordinal 6)
        val indexOfEventStartDay =
            eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!.dayOfWeek.ordinal
        val checkedDayIndices: List<Int> = byDayIndices + indexOfEventStartDay

        val weekDayLetters = (DayOfWeek.MONDAY.value .. DayOfWeek.SUNDAY.value).map { DayOfWeek.of(it).format(firstLetter = true) }

        // TODO cleanup below after removing hardcoded string-array with date names
        // We do minus 1 to match java.time DayOfWeek ordinals
        val weekStart = if (eventViewModel.userSettings.weekStart == 0) WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1 else eventViewModel.userSettings.weekStart - 1
        val weekEnd = 7
        var stringArrayIndex = weekStart
        // Iterate from weekStart first
        weekDayLetters
            .slice(weekStart until weekEnd) // until excludes weekEnd value
            .forEachIndexed { index, dayName ->
                setChipItemContent(index, stringArrayIndex, indexOfEventStartDay, checkedDayIndices, dayName)
                stringArrayIndex++
            }
        if (weekStart != 0) {
            // If weekStart was not Monday, iterate from 0 to fill the rest of the chips
            stringArrayIndex = 0
            weekDayLetters
                .slice(0 until weekStart) // until excludes weekStart value
                .forEachIndexed { index, dayName ->
                    val customIndex = (weekEnd - weekStart) + index
                    setChipItemContent(customIndex, stringArrayIndex, indexOfEventStartDay, checkedDayIndices, dayName)
                    stringArrayIndex++
                }
        }

        // TODO get this from VM
        val eventStartDate =
            eventViewModel.eventLiveData.value!!.getStart(eventViewModel.displayTimeZoneId)!!
                .toLocalDate()

        // applies only to month
        custom_recurrence_occurrence_time_radio_group.check(
            when (eventViewModel.tempMonthlyRepeatOption) {
                EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> custom_recurrence_occurrence_time_1.id
                EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> custom_recurrence_occurrence_time_2.id
                EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> custom_recurrence_occurrence_time_3.id
            }
        )

        val optionsRepeatOn = eventViewModel.calculateMonthlyRepeatOnOptions()
        monthlyRecurrenceOnMap = HashMap()
        optionsRepeatOn.forEach {
            when (it) {
                EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> {
                    custom_recurrence_occurrence_time_1.visibleOrGone(true)
                    custom_recurrence_occurrence_time_1.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                    monthlyRecurrenceOnMap[R.id.custom_recurrence_occurrence_time_1] = it
                }
                EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> {
                    custom_recurrence_occurrence_time_2.visibleOrGone(true)
                    custom_recurrence_occurrence_time_2.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                    monthlyRecurrenceOnMap[R.id.custom_recurrence_occurrence_time_2] = it
                }
                EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> {
                    custom_recurrence_occurrence_time_3.visibleOrGone(true)
                    custom_recurrence_occurrence_time_3.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                    monthlyRecurrenceOnMap[R.id.custom_recurrence_occurrence_time_3] = it
                }
            }
        }

        custom_recurrence_occurrence_time_radio_group.setCustomOnCheckedChangeListener { _, checkedId ->
            requireActivity().clearFocusAndHideKeyboard(view)
            val monthlyRepeatOnOption = monthlyRecurrenceOnMap[checkedId]
            if (monthlyRepeatOnOption != null) eventViewModel.handleRecurrenceRepeatOn(monthlyRepeatOnOption)
        }
    }

    private fun setChipItemContent(index: Int, stringArrayIndex: Int, indexOfEventStartDay: Int, checkedDayIndices: List<Int>, dayName: String) {
        ((chip_group_day_of_week_layout as ViewGroup).getChildAt(index) as Chip).apply {
            text = dayName
            isClickable =
                (stringArrayIndex != indexOfEventStartDay) // we disable and check by default the day of event's start
            isChecked = (stringArrayIndex in checkedDayIndices)
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
                Frequency.DAILY -> R.id.event_form_recurrence_2
                Frequency.WEEKLY -> R.id.event_form_recurrence_3
                Frequency.MONTHLY -> R.id.event_form_recurrence_4
                Frequency.YEARLY -> R.id.event_form_recurrence_5
                else -> R.id.event_form_recurrence_1
            }

            if (it.isCustomRecurring()) {
                //Set custom edit radio button text
                event_form_recurrence_custom_edit.visibleOrGone(true)
                event_form_recurrence_custom_edit.text = AndroidUtils.formatRecurrence(requireContext(), it, eventViewModel.eventTimeZoneId) ?: resources.getString(R.string.event_recurrence_none)

                // handle custom recurrence rule
                custom_recurrence_occurrence_time_radio_group.check(
                    when (eventViewModel.calculateMonthlyRepeatOnOptions()[eventViewModel.calculateMonthlyRepeatOnOptionIndex()]) {
                        EventViewModel.MonthlyRepatOnOption.ON_DAY_X -> custom_recurrence_occurrence_time_1.id
                        EventViewModel.MonthlyRepatOnOption.ON_X_WEEKDAY -> custom_recurrence_occurrence_time_2.id
                        EventViewModel.MonthlyRepatOnOption.ON_LAST_WEEKDAY -> custom_recurrence_occurrence_time_3.id
                    }
                )

                customEndingRadioGroup.check(R.id.custom_recurrence_end_1) // default

                eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.run {
                    // "ends" section
                    if (this.until != null) {
                        customEndingRadioGroup.check(R.id.custom_recurrence_end_2)
                        custom_recurrence_end_2.text = getString(
                            R.string.event_recurrence_ends_on_date,
                            ZonedDateTime.ofInstant(this.until.toInstant(), ZoneId.of(eventViewModel.eventTimeZoneId)).formatDate(eventViewModel.eventTimeZoneId)
                        )
                    }
                    if (this.count != null) {
                        customEndingRadioGroup.check(R.id.custom_recurrence_end_3)
                        custom_recurrence_end_count.setText("${this.count}")
                    }

                    // "repeats every" section
                    when (this.frequency) {
                        Frequency.WEEKLY -> {
                            custom_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_WEEK_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
                            custom_recurrence_period_radio_group.check(custom_recurrence_period_2.id)
                        }
                        Frequency.MONTHLY -> {
                            custom_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT)
                            custom_recurrence_period_radio_group.check(custom_recurrence_period_3.id)
                        }
                        Frequency.YEARLY -> {
                            custom_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_YEAR_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT)
                            custom_recurrence_period_radio_group.check(custom_recurrence_period_4.id)
                        }
                        else -> { // fallback to Frequency.DAILY
                            custom_recurrence_count.setText("${this.interval ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_DAY_COUNT_DEFAULT)
                            custom_recurrence_period_radio_group.check(custom_recurrence_period_1.id)
                        }
                    }
                }
            }

            event_form_recurrence_radio_group.check(
                if (it.isCustomRecurring()) R.id.event_form_recurrence_custom_edit
                else radioButtonId
            )
        })
    }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetRecurrencePeriodText(count: Int) {
        custom_recurrence_period_1.text = resources.getQuantityString(R.plurals.plural_day, count, count)
        custom_recurrence_period_2.text = resources.getQuantityString(R.plurals.plural_week, count, count)
        custom_recurrence_period_3.text = resources.getQuantityString(R.plurals.plural_month, count, count)
        custom_recurrence_period_4.text = resources.getQuantityString(R.plurals.plural_year, count, count)
    }

    // we keep track of TextWatcher so we can remove it when resetting recurrence validation
    var etRecurrenceTextWatcher: TextWatcher? = null

    private fun resetRecurrenceCountValidation(default: Int, min: Int, max: Int) {

        // remove current recurrence count text watcher
        etRecurrenceTextWatcher?.let { custom_recurrence_count.removeTextChangedListener(it) }

        // set new text watcher with new config
        etRecurrenceTextWatcher =
            custom_recurrence_count.doAfterFilteredIntValueChanged(default, min, max) {
                resetRecurrencePeriodText(it)
            }

        // set current value again because it might be outside of newly set limits
        custom_recurrence_count.setText(custom_recurrence_count.text)
    }

    /**
     * Shows or hides custom components for different recurrence types.
     */
    private fun showCustomRecurrenceForms(recurrenceTypeIndex: Int) {
        when (recurrenceTypeIndex) {
            0 -> { // day
                custom_recurrence_occurrence_time_radio_group.visibleOrGone(false)
                custom_recurrence_occurrence_group.visibleOrGone(false)
                custom_recurrence_chips_layout.visibleOrGone(false)
            }
            1 -> { // week
                custom_recurrence_occurrence_time_radio_group.visibleOrGone(false)
                custom_recurrence_occurrence_group.visibleOrGone(false)
                custom_recurrence_chips_layout.visibleOrGone(true)
            }
            2 -> { // month
                custom_recurrence_occurrence_time_radio_group.visibleOrGone(true)
                custom_recurrence_occurrence_group.visibleOrGone(true)
                custom_recurrence_chips_layout.visibleOrGone(false)
            }
            3 -> { // year
                custom_recurrence_occurrence_time_radio_group.visibleOrGone(false)
                custom_recurrence_occurrence_group.visibleOrGone(false)
                custom_recurrence_chips_layout.visibleOrGone(false)
            }
        }
    }
}
