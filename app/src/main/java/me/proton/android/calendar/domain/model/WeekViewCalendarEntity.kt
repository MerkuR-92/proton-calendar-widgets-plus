package me.proton.android.calendar.domain.model

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import androidx.core.content.ContextCompat
import biweekly.parameter.ParticipationStatus
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.jsr310.setEndTime
import com.alamkanak.weekview.jsr310.setStartTime
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.presentation.calendar.customView.MonthView
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
        val strikeThroughTitle: Boolean,
        val isPastEvent: Boolean,
        val isUnanswered: Boolean,
        val decryptionFailed: Boolean,
        val calendarId: String,
        val decrypted: Boolean,
        val isRecurring: Boolean,
        val occurrenceNumber: Int? = null
    ) : WeekViewCalendarEntity()

    data class BlockedTimeSlot(
        val id: String,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime
    ) : WeekViewCalendarEntity()
}

fun WeekViewCalendarEntity.Event.getActualEventId(): String {
    val occurrenceNumberIndex = id.indexOf("&occurrenceNumber=")
    return id.substring(0, if (occurrenceNumberIndex >= 0) occurrenceNumberIndex else id.length)
}

fun Event.toWeekViewCalendarEntityEvent(userEmails: List<String>?, timeZoneId: String, defaultEventTitle: String): WeekViewCalendarEntity.Event {
    val occurrenceNumberSuffix = this.occurrence?.occurrenceNumber?.let {
        "&occurrenceNumber=" + this.occurrence?.occurrenceNumber
    } ?: ""
    val weekViewEventId = this.id + occurrenceNumberSuffix
    val participationStatus =
        if (userEmails != null) this.getParticipationStatus(userEmails)
        else null
    return WeekViewCalendarEntity.Event(
        id = weekViewEventId,
        title = this.summary ?: defaultEventTitle,
        location = "", // TODO Do we want to display event location as subtitle ?
        startTime = this.getStart(timeZoneId).toLocalDateTime(),
        endTime = this.getEnd(timeZoneId).toLocalDateTime(),
        color = Color.parseColor(this.calendar.color),
        isAllDay = this.isAllDay(),
        strikeThroughTitle = this.isCancelled() || participationStatus == ParticipationStatus.DECLINED,
        isPastEvent = this.isInThePast(timeZoneId),
        isUnanswered = !this.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
        decryptionFailed = this.decryptionStatus == Event.DecryptionStatus.FAILURE,
        calendarId = this.calendar.id,
        decrypted = this.decryptionStatus == Event.DecryptionStatus.SUCCESS,
        isRecurring = this.isRecurring(),
        occurrenceNumber = this.occurrence?.occurrenceNumber
    )
}

fun WeekViewCalendarEntity.toWeekViewEntity(context: Context): WeekViewEntity {
    return when (this) {
        is WeekViewCalendarEntity.Event -> toWeekViewEntity(context)
        is WeekViewCalendarEntity.BlockedTimeSlot -> toWeekViewEntity()
    }
}

fun WeekViewCalendarEntity.Event.toWeekViewEntity(context: Context): WeekViewEntity {
    val backgroundColor =
        if (isUnanswered || (strikeThroughTitle && !decryptionFailed)) ContextCompat.getColor(context, R.color.background_norm)
        else if (isPastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
        else color
    val textColor =
        if (isPastEvent) ContextCompat.getColor(context, R.color.text_weak)
        else if (isUnanswered || strikeThroughTitle) ContextCompat.getColor(context, R.color.text_norm)
        else ContextCompat.getColor(context, R.color.text_on_calendar_color)
    val borderWidthResId = if (!strikeThroughTitle) R.dimen.week_view_no_border_width else R.dimen.week_view_border_width
    val borderColor = Color.parseColor(
        AndroidUtils.darkenCalendarColor(
            "#${Integer.toHexString(color and 0x00ffffff)}"
        )
    )

    val styleBuilder = WeekViewEntity.Style.Builder()
        .setTextColor(textColor)
        .setBackgroundColor(backgroundColor)
        .setBorderWidthResource(borderWidthResId)
        .setBorderColor(borderColor)

    if (isUnanswered) {
        styleBuilder.setStripesColorResource(
            if (isPastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
            else Color.parseColor(
                AndroidUtils.brightenCalendarColor(
                    "#${Integer.toHexString(color and 0x00ffffff)}",
                    MonthView.MonthViewSettings.UNANSWERED_STRIPES_BRIGHTEN_COLOR_BY
                )
            )
        )
    }

    val style = styleBuilder.build()

    val title = SpannableStringBuilder(title).apply {
        val titleSpan = TypefaceSpan("sans-serif-medium")
        setSpan(titleSpan, 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (strikeThroughTitle) {
            setSpan(StrikethroughSpan(), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    val subtitle = SpannableStringBuilder(location).apply {
        if (strikeThroughTitle) {
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

