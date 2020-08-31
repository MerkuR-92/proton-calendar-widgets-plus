package me.proton.android.calendar.common

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.core.text.HtmlCompat
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import biweekly.component.VAlarm
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.Recurrence
import me.proton.android.calendar.R
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.model.Event
import java.text.DateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.*


class AndroidUtils(context: Context) {

    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0].toString()
    } else {
        context.resources.configuration.locale.toString()
    }

    companion object {

        fun displayTimePicker(
            context: Context,
            initialTime: LocalTime?,
            is24Hour: Boolean,
            callback: (result: LocalTime) -> Unit
        ) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            val customLayout: View =
                LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
            val timePicker = customLayout.findViewById<TimePicker>(R.id.time_picker)
            timePicker.setIs24HourView(is24Hour)
            initialTime?.let {
                timePicker.hour = initialTime.hour
                timePicker.minute = initialTime.minute
            }
            builder.setView(customLayout)
            builder.setPositiveButton(R.string.dialog_button_set) { _, _ ->
                callback(LocalTime.of(timePicker.hour, timePicker.minute))
            }
            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            builder.create().show()
        }

        fun displayDatePicker(
            context: Context,
            initialDate: LocalDate?,
            minDate: LocalDate? = null,
            maxDate: LocalDate? = null,
            callback: (result: LocalDate) -> Unit
        ) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            val customLayout: View =
                LayoutInflater.from(context).inflate(R.layout.dialog_date_picker, null)
            val datePicker = customLayout.findViewById<DatePicker>(R.id.date_picker)
            initialDate?.let {
                datePicker.init(it.year, it.monthValue - 1, it.dayOfMonth, null)
            }
            minDate?.let {
                datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            maxDate?.let {
                datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            builder.setView(customLayout)
            builder.setPositiveButton(R.string.dialog_button_set) { _, _ ->
                callback(LocalDate.of(datePicker.year, datePicker.month + 1, datePicker.dayOfMonth))
            }
            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            builder.create().show()
        }

        fun displaySingleChoicePicker(
            context: Context,
            title: String?,
            items: Array<String>,
            selectedIndex: Int,
            callback: (selectedIndex: Int) -> Unit
        ) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            title?.apply { builder.setTitle(this) }
            builder.setSingleChoiceItems(items, selectedIndex) { dialog, item ->
                callback(item)
                dialog.dismiss()
            }
            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            builder.create().show()
        }

        fun displaySingleChoiceConfirmationPicker(
            context: Context,
            title: String?,
            items: Array<String>,
            selectedIndex: Int,
            callback: (selectedIndex: Int) -> Unit
        ) {
            var selectedItem: Int = 0
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            title?.apply { builder.setTitle(this) }
            builder.setSingleChoiceItems(items, selectedIndex) { dialog, item ->
                selectedItem = item
            }
            builder.setPositiveButton(R.string.dialog_button_ok) { dialog, _ ->
                callback(selectedItem)
                dialog.dismiss()
            }
            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            builder.create().show()
        }

        fun displaySimpleOkAlert(
            context: Context,
            message: String,
            title: String? = null
        ) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            title?.apply { builder.setTitle(this) }
            builder.setMessage(message)
            builder.setPositiveButton(R.string.dialog_button_ok, null)
            builder.create().show()
        }

        fun displayCalendarPicker(
            context: Context,
            title: String?,
            items: Array<CalendarEntity>,
            initiallySelectedIndex: Int,
            callback: (selectedIndex: Int) -> Unit
        ) {

            val adapter = object : ArrayAdapter<String>(context, R.layout.item_calendar_picker) {

                lateinit var dialog: DialogInterface
                var selectedIndex = initiallySelectedIndex

                override fun getCount(): Int = items.size

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    var view = convertView
                    if (view == null) {
                        view = LayoutInflater.from(context)
                            .inflate(R.layout.item_calendar_picker, parent, false)
                    }

                    view!!.findViewById<AppCompatCheckedTextView>(R.id.ctv_calendar_name).apply {
                        text = items[position].name
                        tag = position
                        isChecked = position == selectedIndex
//                        compoundDrawablesRelative?.first().setTint(Color.parseColor(items[position].color))
                        setOnClickListener {
                            selectedIndex = it.tag as Int
                            notifyDataSetChanged()
                            callback(selectedIndex)
                            dialog.dismiss()
                        }
                    }

                    view.findViewById<ImageView>(R.id.iv_calendar_circle).drawable.setTint(
                        Color.parseColor(
                            items[position].color
                        )
                    )

                    return view
                }

            }

            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            title?.apply { builder.setTitle(this) }
            builder.setAdapter(adapter, null)
            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            val dialog = builder.create()
            adapter.dialog = dialog
            dialog.show()
        }

        fun formatRecurrence(context: Context, event: Event, timeZoneId: String): String? {

            // TODO
            val startWeekOnMonday = true

            val recurrence = event.iCalEvent.recurrenceRule?.value
            if (recurrence != null) {




                val label = listOfNotNull(
                    recurrence.frequency?.let { // non-custom recurrence

// val format = "{0,ordinal}"
//
//    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//        android.icu.text.MessageFormat.format(format, this)
//    } else {
//        com.ibm.icu.text.MessageFormat.format(format, this)
//    }

                        val onDaysOfWeek = if (recurrence.byDay?.size == 7) {
                            context.getString(R.string.event_recurrence_weekly_on_all_days)
                        } else recurrence.byDay?.sortedBy({ if (it.day == DayOfWeek.SUNDAY) 7 else it.day.ordinal /*TODO take start day of week into account*/ })
                            ?.mapIndexedNotNull { index, byDay ->

                                val dayOfWeekAsWord =
                                    context.resources.getStringArray(R.array.days_of_week)[byDay.day.ordinal]

                                if (recurrence.bySetPos.isNotEmpty()) {
                                    val setPos = recurrence.bySetPos[index]

                                    val ordinal = if (setPos > 0) {
                                        context.resources.getStringArray(R.array.ordinals_as_words)
                                            .getOrNull(setPos)
                                    } else {
                                        context.resources.getStringArray(R.array.ordinals_as_words_backwards)
                                            .getOrNull(setPos * -1)
                                    }

                                    "${ordinal ?: recurrence.bySetPos[index]} $dayOfWeekAsWord"
//                                }
                                } else {
                                    dayOfWeekAsWord
                                }
                            }?.joinToString(separator = ", ")
                        // TODO handle .byMonthDay, .byYearDay when needed

                        when (it) {
                            Frequency.DAILY -> {
                                val repeat =
                                    if (recurrence.interval == null || recurrence.interval == 1) {
                                        context.getString(R.string.event_recurrence_daily)
                                    } else {
                                        context.getString(
                                            R.string.event_recurrence_every_some_period,
                                            recurrence.interval,
                                            context.resources.getQuantityString(
                                                R.plurals.plural_day,
                                                recurrence.interval,
                                                recurrence.interval
                                            ) /*TODO remove double quantity param, it's not needed anymore because we're not formatting plural string*/
                                        )
                                    }
                                repeat
                            }
                            Frequency.WEEKLY -> {
                                val repeat =
                                    if (recurrence.interval == null || recurrence.interval == 1) {
                                        context.getString(R.string.event_recurrence_weekly)
                                    } else {
                                        context.getString(
                                            R.string.event_recurrence_every_some_period,
                                            recurrence.interval,
                                            context.resources.getQuantityString(
                                                R.plurals.plural_week,
                                                recurrence.interval,
                                                recurrence.interval
                                            )
                                        )
                                    }
                                val dayOfWeekJavaTimeOrdinal =
                                    ((event.iCalEvent.getStart(timeZoneId)!!.dayOfWeek.ordinal) + 1 % 7)
                                context.getString(
                                    R.string.event_recurrence_occurs_on_day_of_week,
                                    repeat,
                                    if (onDaysOfWeek.isNullOrBlank()) context.resources.getStringArray(
                                        R.array.days_of_week
                                    )[dayOfWeekJavaTimeOrdinal] else onDaysOfWeek
                                )
                            }
                            Frequency.MONTHLY -> {
                                val repeat =
                                    if (recurrence.interval == null || recurrence.interval == 1) {
                                        context.getString(R.string.event_recurrence_monthly)
                                    } else {
                                        context.getString(
                                            R.string.event_recurrence_every_some_period,
                                            recurrence.interval,
                                            context.resources.getQuantityString(
                                                R.plurals.plural_month,
                                                recurrence.interval,
                                                recurrence.interval
                                            )
                                        )
                                    }

                                if (onDaysOfWeek.isNullOrBlank()) {
                                    context.getString(
                                        R.string.event_recurrence_occurs_on_day_of_month,
                                        repeat,
                                        event.iCalEvent.getStart(timeZoneId)!!
                                            .toLocalDate().dayOfMonth
                                    )
                                } else {
                                    context.getString(
                                        R.string.event_recurrence_occurs_on_day_of_week_full_words,
                                        repeat,
                                        onDaysOfWeek
                                    )
                                }
                            }
                            Frequency.YEARLY -> {
                                val repeat =
                                    if (recurrence.interval == null || recurrence.interval == 1) {
                                        context.getString(R.string.event_recurrence_yearly)
                                    } else {
                                        context.getString(
                                            R.string.event_recurrence_every_some_period,
                                            recurrence.interval,
                                            context.resources.getQuantityString(
                                                R.plurals.plural_year,
                                                recurrence.interval,
                                                recurrence.interval
                                            )
                                        )
                                    }
                                repeat
                            }
                            else -> null
                        }
                    },
                    recurrence.count?.let {
                        "${if (it > 1) "$it " else ""}${
                            context.resources.getQuantityString(
                                R.plurals.plural_recurrence_count,
                                it,
                                it
                            )
                        }"
                    },
                    recurrence.until?.let {

//                        if (timeZoneId != event.GET TIMEZONE FROM RRULE OR DTSTART)

                        context.getString(
                            R.string.event_recurrence_until, DateFormat.getDateInstance(
                                DateFormat.LONG
                            ).format(it)
                        )
                    },
                ).joinToString(separator = ", ")

                val startTimeZone = event.iCalendar.timezoneInfo?.getTimezone(event.iCalendar.events.first().dateStart)?.timeZone
                val startJavaTime = event.iCalendar.events.first().dateStart.value.time
                val formatTimeZone = TimeZone.getTimeZone(timeZoneId)

                TimberLogger.d("timezone start=${startTimeZone} format=${formatTimeZone}")

                if (shouldShowRecurrenceTimeZone(recurrence) && startTimeZone?.id != null && formatTimeZone.getOffset(
                        startJavaTime
                    ) != startTimeZone.getOffset(startJavaTime)) {
                    return "${label} (${startTimeZone.id})"
                } else {
                    return label
                }

            }

            return null
        }

        private fun shouldShowRecurrenceTimeZone(recurrence: Recurrence): Boolean {
            return when (recurrence.frequency) {
                Frequency.DAILY -> {
                    recurrence.until != null
                }
                Frequency.WEEKLY -> true
                Frequency.MONTHLY -> true
                Frequency.YEARLY -> {
                    recurrence.until != null
                }
                else -> false
            }
        }


        fun formatAlarm(
            resources: Resources,
            isAllDay: Boolean,
            startZonedDateTime: ZonedDateTime,
            alarm: VAlarm
        ): String? {

            val trigger = alarm.trigger.duration
            return if (isAllDay) { // example: "1 day before at 9:00"

                val startDate = if (trigger.isPrior) {
                    startZonedDateTime
                        .minus(Period.ofWeeks(trigger.weeks ?: 0))
                        .minus(Period.ofDays(trigger.days ?: 0))
                        .minus(Duration.ofHours(trigger.hours?.toLong() ?: 0L))
                        .minus(Duration.ofMinutes(trigger.minutes?.toLong() ?: 0L))
                } else {
                    startZonedDateTime
                        .plus(Period.ofWeeks(trigger.weeks ?: 0))
                        .plus(Period.ofDays(trigger.days ?: 0))
                        .plus(Duration.ofHours(trigger.hours?.toLong() ?: 0L))
                        .plus(Duration.ofMinutes(trigger.minutes?.toLong() ?: 0L))
                }

                TimberLogger.d("trigger weeks: ${trigger.weeks}, days: ${trigger.days}")

                val onTheSameDay = !trigger.isPrior // technically this means "not before" but we don't support "after" alarms

                // magic number 1 is needed for days, because 5 hours before midnight will actually be "1 day before" in "human speak"
                var daysFormatted: Int? = if (trigger.days != null) { // (trigger.days?.toInt() ?: 0) + 1
                        trigger.days + 1
                } else {
                    if (trigger.hours != null || trigger.minutes != null) {
                        1
                    } else null
                }

                if (onTheSameDay) daysFormatted = null

                var weeksFormatted = trigger.weeks?.toInt()
                if (daysFormatted == 7) {
                    weeksFormatted = (weeksFormatted ?: 0) + 1
                    daysFormatted = null
                }

                val label = listOfNotNull(
                    if (onTheSameDay) {
                        resources.getString(R.string.event_alarm_label_on_the_same_day)
                    } else null,
                    weeksFormatted?.let {
                        "${it} ${
                            resources.getQuantityString(
                                R.plurals.plural_week,
                                it,
                                it
                            )
                        }"
                    },
                    daysFormatted?.let {
                        "${it} ${
                            resources.getQuantityString(
                                R.plurals.plural_day,
                                it,
                                it
                            )
                        }"
                    }
                ).joinToString(separator = ", ")

                val alarmTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(
                    Date.from(
                        startDate.toInstant()
                    )
                )

                if (label.isBlank()) {
                    null
                } else if (alarm.action?.isEmail == true) {
                    resources.getString(
                        if (onTheSameDay) R.string.event_alarm_label_not_before_with_time_by_email else R.string.event_alarm_label_before_with_time_by_email,
                        label,
                        alarmTime
                    )
                } else if (alarm.action?.isDisplay == true) {
                    resources.getString(
                        if (onTheSameDay) R.string.event_alarm_label_not_before_with_time else R.string.event_alarm_label_before_with_time,
                        label,
                        alarmTime
                    )
                } else {
                    null
                }

            } else { // example: "15 minutes before"

                // Proton support only 1 component for partial-day alarms

                val label = listOfNotNull(
                    trigger.weeks?.let {
                        "$it ${
                            resources.getQuantityString(
                                R.plurals.plural_week,
                                it,
                                it
                            )
                        }"
                    },
                    trigger.days?.let {
                        "$it ${
                            resources.getQuantityString(
                                R.plurals.plural_day,
                                it,
                                it
                            )
                        }"
                    },
                    trigger.hours?.let {
                        "$it ${
                            resources.getQuantityString(
                                R.plurals.plural_hour,
                                it,
                                it
                            )
                        }"
                    },
                    trigger.minutes?.let {
                        "$it ${
                            resources.getQuantityString(
                                R.plurals.plural_minute,
                                it,
                                it
                            )
                        }"
                    }
                ).joinToString(separator = ", ")

                if (label.isBlank()) {
                    if (!trigger.isPrior && (trigger.seconds != null && trigger.seconds == 0)) {
                        resources.getString(R.string.event_alarm_label_at_event_time)
                    } else {
                        null
                    }
                } else if (alarm.action?.isEmail == true) {
                    resources.getString(R.string.event_alarm_label_before_by_email, label)
                } else if (alarm.action?.isDisplay == true) {
                    resources.getString(R.string.event_alarm_label_before, label)
                } else {
                    null
                }
            }

        }

        /**
         * @param labels Pair<String Resource ID, Color Resource ID>
         * @param icons Pair<Drawable Resource ID, Color Resource ID>
         */
        fun displayPopupMenu(
            view: View,
            labels: List<Pair<Int, Int?>>,
            icons: List<Pair<Int?, Int?>>,
            onItemClicked: (position: Int) -> Unit
        ) {

            // TODO we need custom adapter to apply custom colors to icon and text, this is workaround for now

            val data = ArrayList<HashMap<String, Any>>()
            data.add(
                hashMapOf(
                    "text" to view.resources.getText(R.string.action_delete),
                    "icon" to R.drawable.ic_trash
                )
            )

            val popupWindow = ListPopupWindow(view.context)

            val adapter: SimpleAdapter = object: SimpleAdapter(
                view.context,
                data,
                R.layout.item_popup_error, // TODO
                arrayOf("text", "icon"),
                intArrayOf(R.id.tv_text, R.id.iv_icon)
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    return super.getView(position, convertView, parent).apply {
                        this.setOnClickListener {
                            onItemClicked(position)
                            popupWindow.dismiss()
                        }
                    }
                }
            }

            with(popupWindow) {
                isModal = true
                anchorView = view
                verticalOffset = view.resources.getDimensionPixelSize(R.dimen.spacing_element_small)
                horizontalOffset = view.resources.getDimensionPixelSize(R.dimen.spacing_element_small)
                width = measureContentWidth(view.context, adapter)
                height = ListPopupWindow.WRAP_CONTENT
                setAdapter(adapter)
                show()
            }

        }

        // https://stackoverflow.com/questions/14200724/listpopupwindow-not-obeying-wrap-content-width-spec/26814964#26814964
        private fun measureContentWidth(context: Context, listAdapter: ListAdapter): Int {
            var mMeasureParent: ViewGroup? = null
            var maxWidth = 0
            var itemView: View? = null
            var itemType = 0
            val widthMeasureSpec: Int = View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
            val heightMeasureSpec: Int = View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
            val count = listAdapter.count
            for (i in 0 until count) {
                val positionType = listAdapter.getItemViewType(i)
                if (positionType != itemType) {
                    itemType = positionType
                    itemView = null
                }
                if (mMeasureParent == null) {
                    mMeasureParent = FrameLayout(context)
                }
                itemView = listAdapter.getView(i, itemView, mMeasureParent)
                itemView.measure(widthMeasureSpec, heightMeasureSpec)
                val itemWidth = itemView.measuredWidth
                if (itemWidth > maxWidth) {
                    maxWidth = itemWidth
                }
            }
            return maxWidth
        }

    }

}

fun Activity.hideKeyboard() {
    val view = this.currentFocus
    if (view != null ) {
        val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

data class TimePickerData(
    /**
     * 0-23
     */
    val hour: Int,
    /**
     * 0-59
     */
    val minute: Int,
    val is24Hour: Boolean
)

/**
 * Month is normalized to be 1-based.
 */
data class DatePickerData(
    val year: Int,
    /**
     * 1-12
     */
    val month: Int,
    val day: Int
)

fun View.visibleOrGone(visible: Boolean) {
    this.visibility = if (visible) View.VISIBLE else View.GONE
}

fun View.visibleOrInvisible(visible: Boolean) {
    this.visibility = if (visible) View.VISIBLE else View.INVISIBLE
}

/**
 * Listens for changes in EditText, only propagates values within range or forces default when
 * value is non-empty, but incorrect.
 *
 * @return TextWatcher so we can disable listening to that EditText
 */
fun EditText.doAfterFilteredIntValueChanged(
    default: Int,
    min: Int,
    max: Int,
    onValueChanged: (value: Int) -> Unit
): TextWatcher {
    return this.doAfterTextChanged {
        if (!it.isNullOrBlank()) {
            val count = it.toString().toIntOrNull()
            when {
                count == null || it.toString().startsWith("0") -> {
                    this.setText(default.toString())
                }
                count < min -> {
                    this.setText(min.toString())
                }
                count > max -> {
                    this.setText(max.toString())
                }
                else -> {
                    onValueChanged(count)
                }
            }
        }
    }
}

/**
 *
 */
fun RadioGroup.checkIndex(index: Int) {
    this.children.filter { it is RadioButton }.elementAtOrNull(index)?.let {
        this.check(it.id)
    }
}

fun LocalTime.format(/* force AM/PM */): String {
    return this.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

// TODO add and change parameters for customisation
fun LocalDate.format(showDayOfWeek: Boolean = false): String {

    val dateTimeFormatter = if (showDayOfWeek) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }

//    if (locale != null) {
//        dateTimeFormatter.withLocale(locale)
//    }

    return this.format(dateTimeFormatter)
}

fun LocalDate.isLastDayOfWeekInMonth() = this.plusDays(7).monthValue != this.monthValue

// TODO add function for calculating how many days-of-week are there in a given month, we can use it for "backwards" formatting then

fun LocalDate.weekInMonth() = this.get(ChronoField.ALIGNED_WEEK_OF_MONTH)

/**
 * Returns "January", etc.
 */
fun LocalDate.formatMonth(): String {
    return DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()).format(this)
}

/**
 * Returns "first Monday", "second Friday" etc.
 *
 * @param reversed when we need reversed ordinals, like "last Friday"
 */
fun LocalDate.formatMonthlyDayOfWeek(resources: Resources, backwards: Boolean = false): String {

    val ordinal = if (backwards) {
        resources.getStringArray(R.array.ordinals_as_words_backwards)[1] // TODO we support only the last weekdays in month
    } else {
        resources.getStringArray(R.array.ordinals_as_words)[this.weekInMonth()]
    }

    return "$ordinal ${this.dayOfWeek.getDisplayName(
        TextStyle.FULL,
        Locale.getDefault()
    )}"

//
//
//    val dateTimeFormatter = if (showDayOfWeek) {
//        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
//    } else {
//        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
//    }

//    if (locale != null) {
//        dateTimeFormatter.withLocale(locale)
//    }

    //return ""//this.format(dateTimeFormatter)
}

// TODO add switch for forced AM/PM when showing time
fun LocalDateTime.format(showDayOfWeek: Boolean = false): String {
    return "TODO"
}

/**
 * @param id string resource formatted with CDATA if support for basic formatting is needed
 */
fun Context.getText(@StringRes id: Int, vararg args: Any?): CharSequence {
    val text = String.format(getString(id), *args)
    return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
}
