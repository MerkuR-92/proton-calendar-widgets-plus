package me.proton.android.calendar.common

import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import biweekly.util.Frequency
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


                        val onDaysOfWeek = recurrence.byDay?.mapIndexedNotNull { index, byDay ->
                            if (recurrence.bySetPos.isNotEmpty()) {
                                val setPos = recurrence.bySetPos[index]
                                val dayAsWord = if (setPos > 0) {
                                    context.resources.getStringArray(R.array.ordinals_as_words)
                                        .getOrNull(setPos)
                                } else {
                                    context.resources.getStringArray(R.array.ordinals_as_words_backwards)
                                        .getOrNull(setPos * -1)
                                }
                                "${dayAsWord ?: recurrence.bySetPos[index]} ${context.resources.getStringArray(
                                    R.array.days_of_week
                                )[byDay.day.ordinal]}"
                            } else {
                                context.resources.getStringArray(R.array.days_of_week)[byDay.day.ordinal]
                            }
                        }?.joinToString(separator = ", ")
                        // TODO handle .byMonthDay, .byYearDay when needed

                        when (it) {
                            Frequency.DAILY -> {
                                val repeat = if (recurrence.interval == null) {
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
                                val repeat = if (recurrence.interval == null) {
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
                                val dayOfWeekJavaTimeOrdinal = ((event.iCalEvent.getStart(timeZoneId)!!.dayOfWeek.ordinal) + 1 % 7)
                                context.getString(
                                    R.string.event_recurrence_occurs_on_day_of_week,
                                    repeat,
                                    if (onDaysOfWeek.isNullOrBlank()) context.resources.getStringArray(
                                        R.array.days_of_week
                                    )[dayOfWeekJavaTimeOrdinal] else onDaysOfWeek
                                )
                            }
                            Frequency.MONTHLY -> {
                                val repeat = if (recurrence.interval == null) {
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
                                        event.iCalEvent.getStart(timeZoneId)!!.toLocalDate().dayOfMonth
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
                                val repeat = if (recurrence.interval == null) {
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
//        recurrence.interval?.let {

//
//            TimberLogger.e("interval jest: ${it}")
//            TimberLogger.e("recurrence by day: ${recurrence.byDay}")
//            TimberLogger.e("recurrence by day: ${recurrence.byMonth}")
//            TimberLogger.e("recurrence by day: ${recurrence.byMonthDay}")
//            TimberLogger.e("recurrence by day: ${recurrence.bySetPos}")
//            TimberLogger.e("recurrence by day: ${recurrence.byWeekNo}")
//            TimberLogger.e("recurrence by day: ${recurrence.byYearDay}")
//            "INTERVAL: $it"
//        },
                    recurrence.count?.let {
                        "${if (it > 1) "$it " else ""}${context.resources.getQuantityString(R.plurals.plural_recurrence_count, it, it)}"
                    },
                    recurrence.until?.let {
                        context.getString(
                            R.string.event_recurrence_until, DateFormat.getDateInstance(
                                DateFormat.LONG
                            ).format(it)
                        )
                    }
                ).joinToString(separator = ", ")

return label
            }

return null
        }
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
                count == null -> {
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
        TextStyle.FULL_STANDALONE,
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

