package me.proton.android.calendar.common

import android.animation.ValueAnimator
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.Transformation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.HtmlCompat
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.BindingAdapter
import biweekly.component.VAlarm
import biweekly.parameter.ParticipationStatus
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.Recurrence
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.dialog_calendar_list.view.*
import kotlinx.android.synthetic.main.event_attendees_view.*
import kotlinx.android.synthetic.main.item_popup_error.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.model.Event
import me.proton.core.presentation.utils.InputValidationResult
import okhttp3.internal.toHexString
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.WeekFields
import java.util.*
import java.util.Locale.getDefault
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min

class AndroidUtils(context: Context) {

    companion object {

        fun displayTimePicker(
            context: Context,
            initialTime: LocalTime?,
            is24Hour: Boolean,
            callback: (result: LocalTime) -> Unit
        ) {
            val immutableInitialTime = initialTime?: LocalTime.now()
            val timePickerDialog = TimePickerDialog(context, 0,
                { view, hourOfDay, minute ->
                    callback(LocalTime.of(hourOfDay, minute))
                },
                immutableInitialTime.hour,
                immutableInitialTime.minute,
                is24Hour)
            timePickerDialog.show()
        }

        fun displayDatePicker(
            context: Context,
            firstDayOfWeek: java.time.DayOfWeek,
            initialDate: LocalDate?,
            minDate: LocalDate? = null,
            maxDate: LocalDate? = null,
            callback: (result: LocalDate) -> Unit
        ) {
            val immutableInitialDate = initialDate?: LocalDate.now()
            val datePickerDialog = DatePickerDialog(context, 0,
                { view, year, month, dayOfMonth ->
                    callback(LocalDate.of(year, month + 1, dayOfMonth))
                },
                immutableInitialDate.year,
                immutableInitialDate.monthValue - 1,
                immutableInitialDate.dayOfMonth)
            minDate?.let {
                datePickerDialog.datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            maxDate?.let {
                datePickerDialog.datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            datePickerDialog.datePicker.firstDayOfWeek = firstDayOfWeek.toBiweeklyDayOfWeek().calendarConstant
            datePickerDialog.show()
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
                        setOnSingleClickListener() {
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

        fun Context.displayCalendarListMaterialDialog(
            title: Int,
            message: Int,
            cancellable: Boolean,
            items: List<CalendarEntity>,
            callback: DialogInterface.OnClickListener
        ) {
            val materialDialogBuilder = MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setCancelable(cancellable)
                .setPositiveButton(R.string.bootstrap_error_continue_button, callback)

            val adapter = object : ArrayAdapter<CalendarEntity>(this, R.layout.item_calendar_dialog, items) {

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    var view = convertView
                    if (view == null) {
                        view = LayoutInflater.from(context)
                            .inflate(R.layout.item_calendar_dialog, parent, false)
                    }

                    view!!.findViewById<TextView>(R.id.item_calendar_dialog_title).apply {
                        text = getItem(position)?.name
                        tag = position
                    }

                    view.findViewById<ImageView>(R.id.item_calendar_dialog_icon).drawable.setTint(
                        Color.parseColor(
                            getItem(position)?.color
                        )
                    )

                    view.isClickable = false

                    return view
                }

            }

            val view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_calendar_list, null, false)

            view.dialog_calendar_list_header.text = getString(message)

            view.dialog_calendar_list_recycler_view.adapter = adapter
            view.dialog_calendar_list_recycler_view.divider = null

            materialDialogBuilder.setView(view)
            materialDialogBuilder.show()
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

                                val dayOfWeekAsWord = byDay.day.toDayOfWeek().format()

                                val dayNumber: Int? = if (recurrence.bySetPos.isNotEmpty()) {
                                    recurrence.bySetPos[index]
                                } else byDay.num

                                val dayOrdinal = if (dayNumber == null) {
                                    null
                                } else if (dayNumber > 0) {
                                    context.resources.getStringArray(R.array.ordinals_as_words)
                                        .getOrNull(dayNumber)
                                } else {
                                    context.resources.getStringArray(R.array.ordinals_as_words_backwards)
                                        .getOrNull(dayNumber * -1)
                                }

                                "${if (dayOrdinal != null) "$dayOrdinal " else ""}$dayOfWeekAsWord"

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
                                context.getString(
                                    R.string.event_recurrence_occurs_on_day_of_week,
                                    repeat,
                                    if (onDaysOfWeek.isNullOrBlank()) {
                                        (event.iCalEvent.getStart(timeZoneId)!!.dayOfWeek).format()
                                    } else onDaysOfWeek
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
                        context.getString(
                            R.string.event_recurrence_until,
                            it.toZonedDateTime(timeZoneId).formatDate(timeZoneId)
                        )
                    },
                ).joinToString(separator = ", ")

                val startTimeZone = event.iCalendar.timezoneInfo?.getTimezone(event.iCalendar.events.first().dateStart)?.timeZone
                val formatTimeZone = TimeZone.getTimeZone(timeZoneId)

                TimberLogger.d("timezone start=${startTimeZone} format=${formatTimeZone}")

                if (shouldShowRecurrenceTimeZone(recurrence) && startTimeZone?.id != null && formatTimeZone.id != startTimeZone.id) {
                    return "${label} (${formatTimeZone.id})"
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
            is24Hour: Boolean,
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
                var daysFormatted: Int? = if (trigger.days != null) { // add 1 day if time of day exists and is different than midnight
                    trigger.days + (if ((trigger.hours != null && trigger.hours.toInt() != 0) || (trigger.minutes != null && trigger.minutes?.toInt() != 0)) 1 else 0)
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

                val alarmTime = startDate.toLocalTime().format(is24Hour)

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
                        press_popup.setOnSingleClickListener {
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

        fun darkenCalendarColor(colorString: String): String {
            val outHSL = FloatArray(3)
            ColorUtils.colorToHSL(Integer.valueOf(colorString.substringAfter("#"), 16), outHSL)

            // magic number, reducing lightness by 12
            val color = ColorUtils.HSLToColor(floatArrayOf(outHSL[0], outHSL[1], kotlin.math.max(0f, kotlin.math.min(outHSL[2] - 0.12f, 1.0f))))
            return "#${color.toHexString()}"
        }

        /**
         * If supplied TimeZone is not supported, fallback retaining UTC offset.
         */
        fun fallbackTimeZone(timeZone: String): String {

            return if (allowedTimezoneIds.contains(timeZone)) {
                timeZone
            } else {

                if (TimeZone.getAvailableIDs().contains(timeZone)) {

                    val offset = TimeZone.getTimeZone(timeZone).getOffset(Date.from(Instant.now()).time)
                    val alternativeTimezones = TimeZone.getAvailableIDs(offset).filter { allowedTimezoneIds.contains(it) }

                    val alternative = alternativeTimezones.firstOrNull { it.startsWith(timeZone.substringBefore("/")) } ?: alternativeTimezones.firstOrNull()

                    alternative ?: TimeZone.getDefault().id
                } else {
                    TimeZone.getDefault().id
                }

            }
        }

    }

}

fun Activity.clearFocusAndHideKeyboard(view: View?) {
    val windowToken = view?.rootView?.windowToken
    val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
    view?.clearFocus()
}

fun Context.showKeyboard() {
    (this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
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

fun View.animateHeightChange(toHeightPx: Int, onAnimationEnd: () -> Unit) {
    if (this.measuredHeight != toHeightPx) {
        val valueAnimator = ValueAnimator.ofInt(this.measuredHeight, toHeightPx)
        valueAnimator.duration = 300L
        valueAnimator.addUpdateListener {
            val animatedValue = valueAnimator.animatedValue as Int
            val layoutParams = this.layoutParams.apply {
                height = animatedValue
            }
            this.layoutParams = layoutParams
        }
        valueAnimator.start()
        valueAnimator.doOnEnd { onAnimationEnd.invoke() }
    }
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

fun LocalTime.format(is24Hour: Boolean?): String {
    return if (is24Hour == true) {
        this.format(DateTimeFormatter.ofPattern("HH:mm").withLocale(getLocaleForFormatting()))
    } else if (is24Hour == false) {
        this.format(DateTimeFormatter.ofPattern("hh:mm a").withLocale(getLocaleForFormatting()))
    } else {
        this.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(getLocaleForFormatting()))
    }
}

// TODO add and change parameters for customisation
fun LocalDate.format(showDayOfWeek: Boolean = false): String {

    val dateTimeFormatter = if (showDayOfWeek) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(getLocaleForFormatting())
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(getLocaleForFormatting())
    }

    return this.format(dateTimeFormatter)
}

fun LocalDate.isLastDayOfWeekInMonth() = this.plusDays(7).monthValue != this.monthValue

// TODO add function for calculating how many days-of-week are there in a given month, we can use it for "backwards" formatting then

fun LocalDate.weekInMonth() = this.get(ChronoField.ALIGNED_WEEK_OF_MONTH)

/**
 * Returns "January", etc.
 */
fun LocalDate.formatMonth(capitalize: Boolean = false): String {
    val dateFormat = SimpleDateFormat("LLLL", getLocaleForFormatting())
    val formattedMonth = dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    if (capitalize)
        return formattedMonth.substring(0, 1).toUpperCase(getDefault()) +
                formattedMonth.substring(1).toLowerCase(getDefault())
    return formattedMonth
}

fun LocalDate.formatDayOfWeek(short: Boolean = false): String {
    val dateFormat = SimpleDateFormat(if (short) "E" else "EEEE", getLocaleForFormatting())
    return dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
}

/**
 * We only allow Locales used to format date & time that our application is translated to.
 */
fun getLocaleForFormatting(): Locale {
    return when (getDefault()) {
        // add mapping for other supported Locales
        else -> Locale.US
    }
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

    return "$ordinal ${this.dayOfWeek.format()}"

}

/**
 * @param id string resource formatted with CDATA if support for basic formatting is needed
 */
fun Context.getText(@StringRes id: Int, vararg args: Any?): CharSequence {
    val text = String.format(getString(id), *args)
    return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
}

fun <T> concatenate(vararg lists: List<T>): List<T> {
    return listOf(*lists).flatten()
}

fun getCheckedRadioButtonIndex(radioGroup: RadioGroup): Int {
    // Found a bug where id of radio custom was 10 instead of 5, this makes sure we have the right id
    return radioGroup.indexOfChild(radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId))
}

class CustomOnCheckedChangeListener(private val onCustomOnCheckedChange: (RadioGroup, Int) -> Unit) : RadioGroup.OnCheckedChangeListener {
    override fun onCheckedChanged(radioGroup: RadioGroup, checkedId: Int) {
        radioGroup.children.forEach {
            if (getCheckedRadioButtonIndex(radioGroup) != radioGroup.indexOfChild(it))
                it.jumpDrawablesToCurrentState()
        }
        onCustomOnCheckedChange(radioGroup, checkedId)
    }
}

fun RadioGroup.setCustomOnCheckedChangeListener(onCustomOnCheckedChange: (RadioGroup, Int) -> Unit) {
    val customOnCheckedChangeListener = CustomOnCheckedChangeListener { radioGroup, index ->
        onCustomOnCheckedChange(radioGroup, index)
    }
    setOnCheckedChangeListener(customOnCheckedChangeListener)
}

fun getInitials(name: String): String {
    if (name.isBlank()) return ""
    val initials = name.toUpperCase().split(' ')
        .mapNotNull { it.firstOrNull()?.toString() }
        .reduce { acc, s -> acc + s }
    //Keep only the first and last initials
    return if (initials.length > 2) initials[0].toString() + initials[initials.lastIndex] else initials
}

fun expand(v: View, duration: Long? = null, height: Int? = null) {
    val matchParentMeasureSpec = View.MeasureSpec.makeMeasureSpec((v.parent as View).width, View.MeasureSpec.EXACTLY)
    val wrapContentMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    v.measure(matchParentMeasureSpec, wrapContentMeasureSpec)
    val targetHeight = height?: v.measuredHeight
    if (targetHeight == 0) {
        TimberLogger.d("animation expand skipped")
        v.visibility = View.VISIBLE
        return
    }
    TimberLogger.d("animation expand : targetHeight = ${targetHeight}")

    // Older versions of android (pre API 21) cancel animations for views with a height of 0.
    v.layoutParams.height = 1
    v.visibility = View.VISIBLE
    val animation = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            // We make sure height cannot be set to 0 to avoid UI glitch when starting expand animation
            v.layoutParams.height =
                if (targetHeight == 0 || interpolatedTime == 0f) 1 else if (interpolatedTime == 1f) targetHeight else (targetHeight * interpolatedTime).toInt()
            TimberLogger.d("animation expand : applyTransformation interpolatedTime = ${interpolatedTime} & height = ${v.layoutParams.height}")
            v.requestLayout()
        }

        override fun willChangeBounds(): Boolean {
            return true
        }
    }

    // Expansion speed of 1dp/ms
    val animationDuration = (targetHeight / v.context.resources.displayMetrics.density).toLong()
    animation.duration = duration ?: min(animationDuration, MAX_ANIM_DURATION)
    TimberLogger.d("animation expand : duration = ${animation.duration}")
    v.startAnimation(animation)
}

fun collapse(v: View, duration: Long? = null): Int {
    val initialHeight = v.measuredHeight
    TimberLogger.d("animation collapse : initialHeight = ${initialHeight}")
    val animation = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            if (interpolatedTime == 1f) {
                v.visibility = View.GONE
            } else {
                v.layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                v.requestLayout()
            }
            TimberLogger.d("animation collapse : applyTransformation interpolatedTime = ${interpolatedTime} & height = ${v.layoutParams.height}")
        }

        override fun willChangeBounds(): Boolean {
            return true
        }
    }

    // Collapse speed of 1dp/ms
    val animationDuration = (initialHeight / v.context.resources.displayMetrics.density).toLong()
    animation.duration = duration ?: min(animationDuration, MAX_ANIM_DURATION)
    TimberLogger.d("animation collapse : duration = ${animation.duration}")
    v.startAnimation(animation)
    return initialHeight
}

fun rotateArrowDownward(v: View, duration: Long = 100) {
    val rotate =
        RotateAnimation(
            180F,
            0F,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
    rotate.interpolator = LinearInterpolator()
    rotate.fillAfter = true
    rotate.duration = duration
    v.startAnimation(rotate)
}

fun rotateArrowUpward(v: View, duration: Long = 100) {
    val rotate =
        RotateAnimation(
            0F,
            180F,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
    rotate.interpolator = LinearInterpolator()
    rotate.fillAfter = true
    rotate.duration = duration
    v.startAnimation(rotate)
}

fun setStripedBackground(view: View, context: Context, stripeColor: Int) {
    val colorDrawable = ColorDrawable(ContextCompat.getColor(context, R.color.background_norm)) // bg color3
    val vDrawable = AppCompatResources.getDrawable(context, R.drawable.vector_stripes) // vector drawable
    vDrawable?.setTint(stripeColor)
    vDrawable?.alpha = 51 // decimal value for 20% opacity

    if (vDrawable != null) {
        val bitmap = Bitmap.createBitmap(
            vDrawable.intrinsicWidth, vDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vDrawable.draw(canvas)
        val bitmapDrawable = BitmapDrawable(context.resources, bitmap)
        bitmapDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) // set repeat
        val drawable = LayerDrawable(arrayOf(colorDrawable, bitmapDrawable))
        view.background = drawable
    }
}

fun getParticipationStatusPriorityValue(participationStatus: ParticipationStatus): Int {
    // Lower value means higher priority in list, sort by ascending order
    return when(participationStatus) {
        ParticipationStatus.ACCEPTED -> 0
        ParticipationStatus.TENTATIVE -> 1
        ParticipationStatus.DECLINED -> 2
        ParticipationStatus.NEEDS_ACTION -> 3
        else -> 4
    }
}

fun Context.showToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

// Call this method to display SnackBar in a Fragment
fun Activity.displaySnackBar(message: String) {
    Snackbar.make(
        this.findViewById<View>(android.R.id.content),
        message,
        Snackbar.LENGTH_SHORT
    ).show()
}

// Call this method to display SnackBar in a DialogFragment
fun View.displaySnackBar(message: String) {
    Snackbar.make(
        this,
        message,
        Snackbar.LENGTH_SHORT
    ).show()
}

@BindingAdapter("onSingleClick")
fun View.setOnSingleClickListener(clickListener: View.OnClickListener?) {
    clickListener?.also {
        setOnClickListener(OnSingleClickListener(it))
    } ?: setOnClickListener(null)
}

class OnSingleClickListener(
    private val clickListener: View.OnClickListener,
) : View.OnClickListener {
    private var canClick = AtomicBoolean(true)

    override fun onClick(v: View?) {
        if (canClick.getAndSet(false)) {
            v?.run {
                postDelayed({
                    canClick.set(true)
                }, CLICK_INTERVAL_MS)
                clickListener.onClick(v)
            }
        }
    }
}

fun Array<String>.sortFormattedTimeZoneIds() {
    this.sortWith { a, b ->
        val aFloat = a.formattedTimeZoneToFloat()
        val bFloat = b.formattedTimeZoneToFloat()
        when {
            (aFloat < bFloat) -> 1
            (aFloat > bFloat) -> -1
            (a < b) -> -1
            (a > b) -> 1
            else -> 0
        }
    }
}

/**
 * Returns timezone offset as float from formatted timezone with offset
 */
private fun String.formattedTimeZoneToFloat(): Float {
    val pattern = Pattern.compile("^.*GMT([+-]\\d{1,2}):?(\\d{1,2})?\\).*\$")
    val matcher = pattern.matcher(this)
    matcher.find()
    val hours = matcher.group(1)?.toFloat()
    val minutes = matcher.group(2)?.toFloat()?.div(100) ?: 0F
    return (hours ?: 0F) + minutes
}

/**
 * Returns timezone id from formatted timezone with offset
 */
fun String.formattedTimeZoneToId(): String {
    return this.replace(
        Regex(" (\\(GMT[+-]\\d{1,2}:?(\\d{1,2})?\\))"),
        ""
    )
}

fun getCurrentLocale(context: Context): Locale? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        context.resources.configuration.locale
    }
}

fun Context.dpToPixel(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}

fun Context.pixelToDp(pixel: Int): Int {
    return (pixel / resources.displayMetrics.density).toInt()
}

fun getWeekStartDayOfWeek(index: Int): java.time.DayOfWeek = when (index) {
    1 -> java.time.DayOfWeek.MONDAY
    6 -> java.time.DayOfWeek.SATURDAY
    7 -> java.time.DayOfWeek.SUNDAY
    else -> WeekFields.of(getDefault()).firstDayOfWeek
}

fun validateEmail(email: CharSequence): Boolean {
    val regex = InputValidationResult.EMAIL_VALIDATION_PATTERN.toRegex(RegexOption.IGNORE_CASE)
    return regex.matches(email)
}

fun removeAccents(string: CharSequence): String {
    val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    val temp = Normalizer.normalize(string, Normalizer.Form.NFD)
    return regex.replace(temp, "")
}

inline fun <reified T> Any?.tryCast(block: T.() -> Unit) {
    if (this is T) block()
}

fun canonicalizeProtonEmails(emails: List<String>): Map<String, String> {
    val canonicalEmails = hashMapOf<String, String>()
    emails.forEach {
        val canonicalEmail = canonicalizeProtonEmail(it)
        canonicalEmails[it] = canonicalEmail
    }
    return canonicalEmails
}

fun canonicalizeProtonEmail(email:String): String {
    // If user uses a custom domain, we don't apply any canonicalization
    if (!isProtonDomain(email)) return email

    val regex = Regex("(?:\\.|\\-|\\_|\\+.*)(?=.*@)")
    return email.replace(regex, "").toLowerCase(getDefault())
}

fun isProtonDomain(email: String): Boolean {
    return PROTON_MAIL_DOMAINS.any {
        email.endsWith("@$it", true)
    }
}

fun Int.toParticipationStatus(): ParticipationStatus {
    return when (this) {
        1 -> ParticipationStatus.TENTATIVE
        2 -> ParticipationStatus.DECLINED
        3 -> ParticipationStatus.ACCEPTED
        else -> ParticipationStatus.NEEDS_ACTION
    }
}

fun ParticipationStatus.toInt(): Int {
    return when (this) {
        ParticipationStatus.TENTATIVE -> 1
        ParticipationStatus.DECLINED -> 2
        ParticipationStatus.ACCEPTED -> 3
        else -> 0
    }
}

/** Execute the [listener] on [TextWatcher.onTextChanged] */
inline fun EditText.onTextChange(crossinline listener: (CharSequence) -> Unit): TextWatcher {
    val watcher = object : TextWatcher {
        override fun afterTextChanged(editable: Editable) {
            /* Do nothing */
        }
        override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
            /* Do nothing */
        }
        override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
            listener(text)
        }
    }
    addTextChangedListener(watcher)
    return watcher
}
