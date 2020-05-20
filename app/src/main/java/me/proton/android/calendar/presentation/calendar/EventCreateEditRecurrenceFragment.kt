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
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate


class EventCreateEditRecurrenceFragment() : BaseDialogFragment(), KoinComponent {

    override val TAG = "EventCreateEditRecurrenceFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_create_edit_recurrence

    override val actionMenuResourceId = R.menu.fragment_event_create_edit_recurrence
    override val navigateUp = false



    override fun onMenuItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.action_menu_done) {

            // TODO persist data

            when (rg_recurrence.checkedRadioButtonId) {
                R.id.rb_recurrence_1 -> {
                    eventViewModel.handleRecurrence(null, false)
                }
                R.id.rb_recurrence_2 -> {
                    eventViewModel.handleRecurrence(Frequency.DAILY, false)
                }
                R.id.rb_recurrence_3 -> {
                    eventViewModel.handleRecurrence(Frequency.WEEKLY, false)
                }
                R.id.rb_recurrence_4 -> {
                    eventViewModel.handleRecurrence(Frequency.MONTHLY, false)
                }
                R.id.rb_recurrence_5 -> {
                    eventViewModel.handleRecurrence(Frequency.YEARLY, false)
                }
                R.id.rb_recurrence_custom -> {

//                    var untilDate: LocalDate? = null
                    var untilDateChecked: Boolean = false
                    var thisManyRepeats: Int? = null

                    // "repeat until" options, apply to all recurrence periods
                    when (customEndingRadioGroup.getCheckedRadioButtonId()) {
                        R.id.rb_recurrence_custom_1 -> { // ends: never
//                            eventViewModel.handleRecurrenceUntilDate(null)
//                            eventViewModel.handleRecurrenceThisManyRepeats(null)
                        }
                        R.id.rb_recurrence_custom_2 -> { // ends: on specific Date
                            untilDateChecked = true
                        }
                        R.id.rb_recurrence_custom_3 -> { // ends: after X occurrences
//                            eventViewModel.handleRecurrenceUntilDate(null)
                            thisManyRepeats = et_custom_recurrence_count.text.toString().toIntOrNull() ?: FormValidation.OCCURRENCE_COUNT_DEFAULT // TODO force when null?
                        }
                    }

                    // "repeat X times"
                    when (s_recurrence_period.selectedItemPosition) {
                        0 -> { // days
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT
//                            et_recurrence_count.setText(count.toString()) // TODO some kind of validation and showing in red?
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

                            eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = daysOfWeek)
                        }
                        2 -> { // months
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT

                            eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDateChecked, interval, thisManyRepeats)

                            // days in a month for occurence TODO
                            // weird spinner index mapping!
                            // TODO HANDLE IN: handleRecurrenceRepeatOn
                        }
                        3 -> { // years
                            val interval = et_recurrence_count.text.toString().toIntOrNull()
                                ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                            eventViewModel.handleRecurrence(Frequency.YEARLY, untilDateChecked, interval, thisManyRepeats)
                        }
                    }




                    // TODO
// after setup, persist custom recurrence in VM


                }
            }

            findNavController().navigateUp()


        }
    }

    private val navigationArguments: EventCreateEditFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by inject()
    private val eventViewModel: EventViewModel by sharedViewModel() //inject()

    private val indexOfEventStartDay by lazy { (eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.dayOfWeek.ordinal + if (eventViewModel.startWeekOnMonday) 0 else 1) % 7 }

//    private val indexOfEventStartDay = eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.dayOfWeek.ordinal// - if (eventViewModel.startWeekOnMonday) 0 else 1
//    }// + dayNamesStartingIndex //.. + 1 - dayNamesStartingIndex


    lateinit var customEndingRadioGroup: NoLayoutRadioGroup


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attachActionHandlers()
        observeEventLiveData()


    }

    // TODO CLEANUP THIS ENTIRE METHOD
    private fun attachActionHandlers() {

        rg_recurrence.setOnCheckedChangeListener { radioGroup, index ->

            group_custom.visibleOrGone(false)
            tv_custom_occurrence.visibleOrGone(false)
            group_custom_occurrence.visibleOrGone(false)
            ll_chips_day_of_week.visibleOrGone(false)

            when (index) {
                R.id.rb_recurrence_1 -> {
//                    eventViewModel.handleRecurrence(null)
                }
                R.id.rb_recurrence_2 -> {
//                    eventViewModel.handleRecurrence(Frequency.DAILY)
                }
                R.id.rb_recurrence_3 -> {
//                    eventViewModel.handleRecurrence(Frequency.WEEKLY)

                }
                R.id.rb_recurrence_4 -> {
//                    eventViewModel.handleRecurrence(Frequency.MONTHLY)
                }
                R.id.rb_recurrence_5 -> {
//                    eventViewModel.handleRecurrence(Frequency.YEARLY)
                }
                R.id.rb_recurrence_custom -> {
                    group_custom.visibleOrGone(true)
                    showCustomRecurrenceForms(s_recurrence_period.selectedItemPosition)
                }
            }
        }

        customEndingRadioGroup = NoLayoutRadioGroup {
            // handle checks and focus
            if (it == R.id.rb_recurrence_custom_3) {
                if (!et_custom_recurrence_count.hasFocus()) et_custom_recurrence_count.requestFocus()
            } else {
                et_custom_recurrence_count.clearFocus()
            }

            // handle actions
            if (it == R.id.rb_recurrence_custom_2) {

                AndroidUtils.displayDatePicker(
                    requireContext(),
                    eventViewModel.tempRecurrenceUntilLocalDate ?: eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.toLocalDate(),
                    FormValidation.OCURRENCE_MAX_UNTIL
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

        tv_recurrence_custom_suffix.setText(
            resources.getQuantityString(
                R.plurals.plural_occurrence,
                FormValidation.OCCURRENCE_COUNT_DEFAULT
            )
        )
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
                TimberLogger.d("ends after $it occurences")
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

        // init with DAY
        et_recurrence_count.setText(FormValidation.INTERVAL_DAY_COUNT_DEFAULT.toString())
        resetRecurrencePeriodAdapter(FormValidation.INTERVAL_DAY_COUNT_DEFAULT)
        s_recurrence_period.setSelection(0)


        val dayNamesStartingIndex = if (eventViewModel.startWeekOnMonday) 1 else 0

        resources.getStringArray(R.array.days_of_week_letters)
            .slice(dayNamesStartingIndex..(dayNamesStartingIndex + 6))
            .forEachIndexed { index, dayName ->
                ((ll_chips_day_of_week as ViewGroup).getChildAt(index) as Chip).apply {
                    text = dayName
                    isClickable = (index != indexOfEventStartDay) // we disable and check by default the day of event's start
                    isChecked = (index == indexOfEventStartDay)
                }
            }

//        s_recurrence_period.adapter

        // applies only to month
        press_recurrence_time.setOnClickListener {

            val ocurrenceOnGivenDayText =
                "TODO pass event start date AND bySetPos" // every fourth Monday <- OR -> on the last Friday (WHEN THIS IS THE LAST WEEK OF THE CURRENT MONTH)

            // TODO get this from VM
            val eventStartDate =
                eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!
                    .toLocalDate()

            AndroidUtils.displaySingleChoicePicker(
                requireContext(), getString(R.string.event_recurrence_occurs_on), listOfNotNull(
                    getString(
                        R.string.event_recurrence_occurs_monthly_on_day,
                        eventStartDate.dayOfMonth
                    ),
                    if (eventStartDate.weekInMonth() <= 4) { // we show only first 4 weeks of month this way
                        getString(
                            R.string.event_recurrence_occurs_on_monthly_on_x_day_of_week,
                            eventStartDate.formatMonthlyDayOfWeek(resources)
                        )
                    } else null,
                    if (eventStartDate.isLastDayOfWeekInMonth()) getString(
                        R.string.event_recurrence_occurs_on_monthly_on_x_day_of_week,
                        eventStartDate.formatMonthlyDayOfWeek(resources, backwards = true)
                    ) else null
                ).toTypedArray(), 0 /*TODO*/
            ) {
                eventViewModel.handleRecurrenceRepeatOn(it)
            }

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
                if (it.isCustomRecurring()) {

                    // fill in custom recurrence data in GUI
                    // TODO


                    R.id.rb_recurrence_custom
                } else radioButtonId
            )




            // set "ends" section


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
    var etRecurrencetextWatcher: TextWatcher? = null

    private fun resetRecurrenceCountValidation(default: Int, min: Int, max: Int) {

        // remove current recurrence count text watcher
        etRecurrencetextWatcher?.let { et_recurrence_count.removeTextChangedListener(it) }

        // set new text watcher with new config
        etRecurrencetextWatcher =
            et_recurrence_count.doAfterFilteredIntValueChanged(default, min, max) {
                TimberLogger.d("resetRecurrencePeriodAdapter = ${it}")
                resetRecurrencePeriodAdapter(it)
            }

        // set current value again because it might be outside of newly set limits
        et_recurrence_count.setText(et_recurrence_count.text)
    }


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

    private fun stash() {


        var dateValue: LocalDate? = LocalDate.now() // TODO read this from VM, or null


        // TODO
        /*press_recurrence_until.setOnClickListener {

            val initiallySelectedIndex = 0 // TODO take it from vm
            val initialCountValue = FormValidation.OCCURRENCE_COUNT_DEFAULT // TODO nullable ?
            val initialDateValue: LocalDate? = null//LocalDate.now() // nullable

            val adapter =
                object : ArrayAdapter<String>(requireContext(), R.layout.item_calendar_picker) {

                    lateinit var dialog: DialogInterface
                    var selectedIndex = initiallySelectedIndex
                    var countValue = initialCountValue
                    var dateValue = initialDateValue

                    override fun getCount(): Int = 3

                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {

                        val clickListener = View.OnClickListener {
                            selectedIndex = it.tag as Int
                            notifyDataSetChanged()
                            if (selectedIndex == 1) {
                                AndroidUtils.displayDatePicker(requireContext(), if (dateValue != null) DatePickerData(dateValue!!.year, dateValue!!.monthValue, dateValue!!.dayOfMonth) else null) {
                                    TimberLogger.d("date: $it")
                                }
                            } else {
//                                dialog.dismiss()
                            }


                        }
                        //
                        return when (position) {
                            0 -> {
                                LayoutInflater.from(context)
                                    .inflate(R.layout.checked_text_view, parent, false).apply {
                                    (this as CheckedTextView).apply {
                                        text = getString(R.string.event_recurrence_ends_never)
                                        setChecked(position == selectedIndex)
                                    }
                                    setTag(position)
                                    setOnClickListener(clickListener)
                                }
                            }
                            1 -> {
                                LayoutInflater.from(context)
                                    .inflate(R.layout.checked_text_view, parent, false).apply {
                                    (this as CheckedTextView).apply {
                                        text = if (dateValue != null) getString(R.string.event_recurrence_ends_on_date, dateValue!!.format()) else getString(R.string.event_recurrence_ends_on_date_empty)
                                        setChecked(position == selectedIndex)
                                    }
                                    setTag(position)
                                    setOnClickListener(clickListener)
                                }
                            }
                            2 -> {
                                val textView = LayoutInflater.from(context)
                                    .inflate(R.layout.checked_text_view, parent, false).apply {
                                    (this as CheckedTextView).apply {
                                        text = getString(R.string.event_recurrence_ends_after_count)
                                        setChecked(position == selectedIndex)
                                        setTag(position)
                                        setOnClickListener(clickListener)
                                    }
                                }
                                val editText = LayoutInflater.from(context)
                                    .inflate(R.layout.text_input_layout_simple, parent, false).apply {
                                        findViewById<EditText>(R.id.edit_text).apply {
                                            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                                            doAfterFilteredIntValueChanged(
                                                FormValidation.OCCURRENCE_COUNT_DEFAULT,
                                                FormValidation.OCCURRENCE_COUNT_MIN,
                                                FormValidation.OCCURRENCE_COUNT_MAX
                                            ) {
                                                TimberLogger.d("ends after $it occurences")
                                            }
                                            if (position == selectedIndex) {
                                                requestFocus()
                                                //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                                            }
                                        }
                                    // TODO handle click and text change
                                }
                                val editTextLayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                editTextLayoutParams.setMargins(resources.getDimensionPixelSize(R.dimen.abc_select_dialog_padding_start_material), 0, resources.getDimensionPixelSize(R.dimen.abc_select_dialog_padding_start_material), 0)

                                LinearLayout(requireContext()).apply {
                                    orientation = LinearLayout.VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    addView(textView)
                                    addView(editText, editTextLayoutParams)
                                    setTag(position)
                                    setOnClickListener(clickListener)
                                }
                            }
                            else -> {
                                throw IndexOutOfBoundsException("Adapter does not support position $position")
                            }
                        }






//                    var view = convertView
//                    if (view == null) {
//                        view = LayoutInflater.from(context).inflate(R.layout.item_calendar_picker, parent, false)
//                    }
//
//                    view!!.findViewById<AppCompatCheckedTextView>(R.id.ctv_calendar_name).apply {
//                        text = items[position].name
//                        tag = position
//                        setChecked(position == selectedIndex)
//
//                    }
//
//                    view.findViewById<ImageView>(R.id.iv_calendar_circle).drawable.setTint(Color.parseColor(items[position].color))

//                    return view
                    }

                }

            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
            builder.setTitle(getString(R.string.event_recurrence_ends_on))
            builder.setAdapter(adapter, null)
            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            builder.setPositiveButton(R.string.dialog_button_set) { _, _ ->
                TODO("Not yet implemented")
            }
            val dialog = builder.create()
            adapter.dialog = dialog
            dialog.show()

        }*/
    }


}
