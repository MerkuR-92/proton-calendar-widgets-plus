package me.proton.android.calendar.presentation.calendar.adapter

import android.graphics.RectF
import android.util.Log
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.jsr310.WeekViewPagingAdapterJsr310
import me.proton.android.calendar.common.FeatureFlag.DRAG_AND_DROP
import me.proton.android.calendar.common.utils.AndroidUtils.showToast
import me.proton.android.calendar.domain.model.WeekViewCalendarEntity
import me.proton.android.calendar.domain.model.toWeekViewEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

class WeekViewAdapter(
    private val dragHandler: (String, LocalDateTime, LocalDateTime) -> Unit,
    private val loadMoreHandler: (List<YearMonth>) -> Unit,
    private val rangeChangedHandler: (LocalDate, LocalDate) -> Unit,
    private val viewClickHandler: (LocalDateTime, Boolean) -> Unit,
    private val eventClickHandler: (WeekViewCalendarEntity.Event) -> Unit,
    private val dateHeaderClickHandler: (LocalDate) -> Unit
) : WeekViewPagingAdapterJsr310<WeekViewCalendarEntity>() {

    private val defaultDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.MEDIUM,
        FormatStyle.SHORT
    )

    override fun onCreateEntity(item: WeekViewCalendarEntity): WeekViewEntity = item.toWeekViewEntity(context)

    override fun onEventClick(data: WeekViewCalendarEntity, bounds: RectF) {
        if (data is WeekViewCalendarEntity.Event) {
            eventClickHandler(data)
        }
    }

    override fun onRangeChanged(firstVisibleDate: LocalDate, lastVisibleDate: LocalDate) {
        super.onRangeChanged(firstVisibleDate, lastVisibleDate)
        rangeChangedHandler(firstVisibleDate, lastVisibleDate)
    }

    override fun onEmptyViewClick(time: LocalDateTime, isAllDay: Boolean) {
        viewClickHandler(time, isAllDay)
    }

    override fun onDateHeaderClick(time: LocalDate) {
        dateHeaderClickHandler(time)
    }

    override fun onDragAndDropFinished(data: WeekViewCalendarEntity, newStartTime: LocalDateTime, newEndTime: LocalDateTime) {
        if (data is WeekViewCalendarEntity.Event) {
            dragHandler(data.id, newStartTime, newEndTime)
        }
    }

    override fun onEmptyViewLongClick(time: LocalDateTime) {
        context.showToast("Empty view long-clicked at ${defaultDateTimeFormatter.format(time)}")
    }

    override fun onLoadMore(startDate: LocalDate, endDate: LocalDate) {
        loadMoreHandler(yearMonthsBetween(startDate, endDate))
    }

    override fun onVerticalScrollPositionChanged(currentOffset: Float, distance: Float) {
        Log.d("BasicActivity", "Scrolling vertically (distance: ${distance.toInt()}, current offset ${currentOffset.toInt()})")
    }

    override fun onVerticalScrollFinished(currentOffset: Float) {
        Log.d("BasicActivity", "Vertical scroll finished (current offset ${currentOffset.toInt()})")
    }

    override fun onEventLongClick(data: WeekViewCalendarEntity, bounds: RectF): Boolean {
        return !DRAG_AND_DROP // Return true to disable drag and drop
    }

    private fun yearMonthsBetween(startDate: LocalDate, endDate: LocalDate): List<YearMonth> {
        val yearMonths = mutableListOf<YearMonth>()
        val maxYearMonth = endDate.yearMonth
        var currentYearMonth = startDate.yearMonth

        while (currentYearMonth <= maxYearMonth) {
            yearMonths += currentYearMonth
            currentYearMonth = currentYearMonth.plusMonths(1)
        }

        return yearMonths
    }

    private val LocalDate.yearMonth: YearMonth
        get() = YearMonth.of(year, month)
}

