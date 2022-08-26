package me.proton.android.calendar.domain.model

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.jsr310.setEndTime
import com.alamkanak.weekview.jsr310.setStartTime
import me.proton.android.calendar.R
import java.time.LocalDateTime

sealed class WeekViewCalendarEntity {

    data class Event(
        val id: String,
        val title: CharSequence,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val location: CharSequence,
        val color: Int,
        val isAllDay: Boolean,
        val isCanceled: Boolean
    ) : WeekViewCalendarEntity()

    data class BlockedTimeSlot(
        val id: String,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime
    ) : WeekViewCalendarEntity()
}

fun WeekViewCalendarEntity.toWeekViewEntity(): WeekViewEntity {
    return when (this) {
        is WeekViewCalendarEntity.Event -> toWeekViewEntity()
        is WeekViewCalendarEntity.BlockedTimeSlot -> toWeekViewEntity()
    }
}

fun WeekViewCalendarEntity.Event.toWeekViewEntity(): WeekViewEntity {
    val backgroundColor = if (!isCanceled) color else Color.WHITE
    val textColor = if (!isCanceled) Color.WHITE else color
    val borderWidthResId = if (!isCanceled) R.dimen.week_view_no_border_width else R.dimen.week_view_border_width

    val style = WeekViewEntity.Style.Builder()
        .setTextColor(textColor)
        .setBackgroundColor(backgroundColor)
        .setBorderWidthResource(borderWidthResId)
        .setBorderColor(color)
        .build()

    val title = SpannableStringBuilder(title).apply {
        val titleSpan = TypefaceSpan("sans-serif-medium")
        setSpan(titleSpan, 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (isCanceled) {
            setSpan(StrikethroughSpan(), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    val subtitle = SpannableStringBuilder(location).apply {
        if (isCanceled) {
            setSpan(StrikethroughSpan(), 0, location.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    return WeekViewEntity.Event.Builder(this)
        .setId(id)
        .setTitle(title)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setSubtitle(subtitle)
        .setAllDay(isAllDay)
        .setStyle(style)
        .build()
}

fun WeekViewCalendarEntity.BlockedTimeSlot.toWeekViewEntity(): WeekViewEntity {
    val style = WeekViewEntity.Style.Builder()
        .setBackgroundColorResource(R.color.background_secondary)
        .setCornerRadius(0)
        .build()

    return WeekViewEntity.BlockedTime.Builder()
        .setId(id)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setStyle(style)
        .build()
}

