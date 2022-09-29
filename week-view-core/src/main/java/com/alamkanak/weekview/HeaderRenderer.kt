package com.alamkanak.weekview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.StaticLayout
import android.text.TextPaint
import android.util.SparseArray
import androidx.collection.ArrayMap
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.roundToInt

internal class HeaderRenderer(
    context: Context,
    viewState: ViewState,
    eventChipsCacheProvider: EventChipsCacheProvider,
    onHeaderHeightChanged: () -> Unit
) : Renderer, DateFormatterDependent {

    private val allDayEventLabels = ArrayMap<EventChip, StaticLayout>()
    private val dateLabelLayouts = SparseArray<Pair<StaticLayout, StaticLayout>>()

    private val headerUpdater = HeaderUpdater(
        viewState = viewState,
        labelLayouts = dateLabelLayouts,
        onHeaderHeightChanged = onHeaderHeightChanged
    )

    private val eventsUpdater = AllDayEventsUpdater(
        viewState = viewState,
        eventsLabelLayouts = allDayEventLabels,
        eventChipsCacheProvider = eventChipsCacheProvider
    )

    private val dateLabelDrawer = DateLabelsDrawer(
        viewState = viewState,
        dateLabelLayouts = dateLabelLayouts
    )

    private val eventsDrawer = AllDayEventsDrawer(
        viewState = viewState,
        allDayEventLayouts = allDayEventLabels
    )

    private val headerDrawer = HeaderDrawer(
        context = context,
        viewState = viewState,
        eventChipsCacheProvider = eventChipsCacheProvider
    )

    override fun onSizeChanged(width: Int, height: Int) {
        allDayEventLabels.clear()
        dateLabelLayouts.clear()
    }

    override fun onDateFormatterChanged(formatter: DateFormatter) {
        allDayEventLabels.clear()
        dateLabelLayouts.clear()
    }

    override fun render(canvas: Canvas) {
        eventsUpdater.update()
        headerUpdater.update()

        headerDrawer.draw(canvas)
        dateLabelDrawer.draw(canvas)
        eventsDrawer.draw(canvas)
    }
}

private class HeaderUpdater(
    private val viewState: ViewState,
    private val labelLayouts: SparseArray<Pair<StaticLayout, StaticLayout>>,
    private val onHeaderHeightChanged: () -> Unit
) : Updater {

    private val animator = ValueAnimator()

    override fun update() {
        val missingDates = viewState.dateRange.filterNot { labelLayouts.hasKey(it.toEpochDays()) }
        for (date in missingDates) {
            val key = date.toEpochDays()
            labelLayouts.put(key, calculateStaticLayoutForDate(date))
        }

        val dateLabels = viewState.dateRange.map { labelLayouts[it.toEpochDays()] }
        updateHeaderHeight(dateLabels)
    }

    private fun updateHeaderHeight(
        dateLabels: List<Pair<StaticLayout, StaticLayout>>
    ) {
        val maximumLayoutHeight = dateLabels.map {
            if (viewState.numberOfVisibleDays == 1) {
                it.first.height.toFloat() + it.second.height.toFloat()
            } else {
                it.first.height.toFloat()
            }
        }.maxOrNull() ?: 0f
        viewState.dateLabelHeight = maximumLayoutHeight

        val currentHeaderHeight = viewState.headerHeight
        val newHeaderHeight = viewState.calculateHeaderHeight()

        if (currentHeaderHeight == 0f || currentHeaderHeight == newHeaderHeight) {
            // The height hasn't been set yet or didn't change; simply update without an animation
            viewState.updateHeaderHeight(newHeaderHeight)
            return
        }

        if (animator.isRunning) {
            // We're already running the animation to change the header height
            return
        }

        animator.animate(
            fromValue = currentHeaderHeight,
            toValue = newHeaderHeight,
            onUpdate = { height ->
                viewState.updateHeaderHeight(height)
                onHeaderHeightChanged()
            }
        )
    }

    private fun calculateStaticLayoutForDate(date: Calendar): Pair<StaticLayout, StaticLayout> {
        val weekDayLabel = viewState.weekDayFormatter(date)
        val dateLabel = viewState.dateFormatter(date)

        val textPaint = when {
            date.isToday -> viewState.todayHeaderTextPaint
            date.isWeekend -> viewState.weekendHeaderTextPaint
            else -> viewState.headerTextPaint
        }
        val weekDayPaint = TextPaint(textPaint).apply {
            if (viewState.numberOfVisibleDays > 1) textAlign = Paint.Align.LEFT
            else textAlign = Paint.Align.CENTER
            if (!date.isToday) color = viewState.weakHeaderTextColor
        }
        val datePaint = TextPaint(textPaint).apply {
            textAlign = Paint.Align.CENTER
            if (viewState.numberOfVisibleDays == 1) textSize = viewState.singleDayNumberHeaderTextSize
        }
        return Pair(
            weekDayLabel.toTextLayout(textPaint = weekDayPaint, width = viewState.dayWidth.toInt()),
            dateLabel.toTextLayout(textPaint = datePaint, width = viewState.dayWidth.toInt()),
        )
    }

    private fun <E> SparseArray<E>.hasKey(key: Int): Boolean = indexOfKey(key) >= 0
}

private class DateLabelsDrawer(
    private val viewState: ViewState,
    private val dateLabelLayouts: SparseArray<Pair<StaticLayout, StaticLayout>>
) : Drawer {

    override fun draw(canvas: Canvas) {
        if (viewState.numberOfVisibleDays > 1) {
            canvas.drawDateLabelInMultiDayView()
        } else {
            canvas.drawDateLabelInSingleDayView()
        }
    }

    private fun Canvas.drawDateLabelInSingleDayView() {
        val bounds = viewState.weekNumberBounds
        val date = viewState.dateRange.first()

        val key = date.toEpochDays()
        val textLayout = dateLabelLayouts[key]

        val weekDayTextLayout = textLayout.first
        val dateTextLayout = textLayout.second
        withTranslation(
            x = bounds.centerX(),
            y = viewState.headerPadding,
        ) {
            draw(weekDayTextLayout)
        }
        withTranslation(
            x = bounds.centerX(),
            y = viewState.headerPadding + weekDayTextLayout.height,
        ) {
            draw(dateTextLayout)
        }
    }

    private fun Canvas.drawDateLabelInMultiDayView() {
        drawInBounds(viewState.headerBounds) {
            viewState.dateRangeWithStartPixels.forEach { (date, startPixel) ->
                drawLabel(date, startPixel)
            }
        }
    }

    private fun Canvas.drawLabel(date: Calendar, startPixel: Float) {
        val key = date.toEpochDays()
        val textLayout = dateLabelLayouts[key]

        val weekDayTextLayout = textLayout.first
        val dateTextLayout = textLayout.second

        val weekDayBounds = weekDayTextLayout.paint.getTextBounds(weekDayTextLayout.text.toString())
        val dateBounds = dateTextLayout.paint.getTextBounds(dateTextLayout.text.toString())
        val weekDayWidth = weekDayBounds.right - weekDayBounds.left
        val spaceWidth = viewState.headerLabelsInnerMargin
        val dateWidth = dateBounds.right - dateBounds.left
        val sumLabelWidth = weekDayWidth + dateWidth + spaceWidth
        val weekDayStartX = startPixel + (viewState.dayWidth - sumLabelWidth) / 2f

        val squareStartX = weekDayStartX + weekDayWidth + spaceWidth
        if (date.withTimeZone(viewState.customTimeZone).isSameDate(nowAtTimezone(viewState.customTimeZone))) {
            val rect = RectF(
                squareStartX,
                viewState.headerTodaySquareMarginTop,
                squareStartX + viewState.headerTodaySquareSize,
                viewState.headerTodaySquareMarginTop + viewState.headerTodaySquareSize
            )
            drawRoundRect(
                rect,
                viewState.headerTodaySquareRadius,
                viewState.headerTodaySquareRadius,
                Paint().apply {
                    color = weekDayTextLayout.paint.color
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = viewState.headerTodaySquareStrokeWidth
                }
            )
        }

        viewState.headerDateLabelHeight = viewState.headerPadding + weekDayTextLayout.height + viewState.headerPadding
        withTranslation(
            x = weekDayStartX,
            y = viewState.headerPadding,
        ) {
            draw(weekDayTextLayout)
        }

        val dateStartX = squareStartX + viewState.headerTodaySquareSize / 2
        withTranslation(
            x = dateStartX,
            y = viewState.headerPadding,
        ) {
            draw(dateTextLayout)
        }
    }
}

private class AllDayEventsUpdater(
    private val viewState: ViewState,
    private val eventsLabelLayouts: ArrayMap<EventChip, StaticLayout>,
    private val eventChipsCacheProvider: EventChipsCacheProvider
) : Updater {

    private val boundsCalculator = EventChipBoundsCalculator(viewState)
    private val textFitter = TextFitter(viewState)

    private var previousHorizontalOrigin: Float? = null

    private val isRequired: Boolean
        get() {
            val didScrollHorizontally = previousHorizontalOrigin != viewState.currentOrigin.x
            val dateRange = viewState.dateRange
            val eventChips = eventChipsCacheProvider()?.allDayEventChipsInDateRange(dateRange).orEmpty()
            val containsNewChips = eventChips.any { it.bounds.isEmpty }
            return didScrollHorizontally || containsNewChips
        }

    override fun update() {
        if (!isRequired) {
            return
        }

        eventsLabelLayouts.clear()

        val datesWithStartPixels = viewState.dateRangeWithStartPixels
        for ((date, startPixel) in datesWithStartPixels) {
            // If we use a horizontal margin in the day view, we need to offset the start pixel.
            val modifiedStartPixel = when {
                viewState.isSingleDay -> startPixel + viewState.singleDayHorizontalPadding.toFloat()
                else -> startPixel
            }

            val eventChips = eventChipsCacheProvider()?.allDayEventChipsByDate(date).orEmpty()
            eventChips.forEachIndexed { index, eventChip ->
                eventChip.updateBounds(index = index, startPixel = modifiedStartPixel)
                if (eventChip.bounds.isNotEmpty) {
                    eventsLabelLayouts[eventChip] = textFitter.fitAllDayEvent(eventChip)
                } else {
                    eventsLabelLayouts.remove(eventChip)
                }
            }
        }

        val maximumChipHeight = eventsLabelLayouts.keys
            .map { it.bounds.height().roundToInt() }
            .maxOrNull() ?: 0

        viewState.currentAllDayEventHeight = maximumChipHeight

        val maximumChipsPerDay = eventsLabelLayouts.keys
            .groupBy { it.startTime.toEpochDays() }
            .values
            .maxByOrNull { it.size }?.size ?: 0

        viewState.maxNumberOfAllDayEvents = maximumChipsPerDay
    }

    private fun EventChip.updateBounds(index: Int, startPixel: Float) {
        val candidate = boundsCalculator.calculateAllDayEvent(index, eventChip = this, startPixel)
        if (candidate.isValid) {
            bounds.set(candidate)
        } else {
            bounds.setEmpty()
        }
    }

    private val RectF.isValid: Boolean
        get() {
            val hasNonZeroWidth = left < right
            val calendarArea = viewState.calendarGridBounds
            val isVisibleHorizontally = right > calendarArea.left && left < calendarArea.right
            return hasNonZeroWidth && isVisibleHorizontally
        }
}

internal class AllDayEventsDrawer(
    private val viewState: ViewState,
    private val allDayEventLayouts: ArrayMap<EventChip, StaticLayout>
) : Drawer {

    private val eventChipDrawer = EventChipDrawer(viewState)

    override fun draw(canvas: Canvas) = canvas.drawInBounds(viewState.headerBounds) {
        for (date in viewState.dateRange) {
            val events = allDayEventLayouts
                .filter { it.key.startTime.isSameDate(date) }
                .toList()

            if (viewState.arrangeAllDayEventsVertically) {
                renderEventsVertically(events.sortedBy { it.first.bounds.top })
            } else {
                renderEventsHorizontally(events)
            }
        }
    }

    private fun Canvas.renderEventsHorizontally(events: List<Pair<EventChip, StaticLayout>>) {
        for ((eventChip, textLayout) in events) {
            eventChipDrawer.draw(eventChip, canvas = this, textLayout)
        }
    }

    private fun Canvas.renderEventsVertically(events: List<Pair<EventChip, StaticLayout>>) {
        // Un-hide all events. To prevent any click handler from mapping a click to a hidden event,
        // we set isHidden to true for all events that aren't shown in the collapsed state.
        events.forEach { it.first.isHidden = false }

        if (viewState.allDayEventsExpanded || events.size <= 3) {
            // Draw them all!
            for ((eventChip, textLayout) in events) {
                eventChipDrawer.draw(eventChip, canvas = this, textLayout)
            }
        } else {
            val (firstEventChip, firstTextLayout) = events[0]
            eventChipDrawer.draw(firstEventChip, canvas = this, firstTextLayout)
            val (secondEventChip, secondTextLayout) = events[1]
            eventChipDrawer.draw(secondEventChip, canvas = this, secondTextLayout)

            val needsExpandInfo = events.size > 3
            if (needsExpandInfo) {
                drawExpandInfo(eventsCount = events.size - 2, priorEventChip = secondEventChip)
                events.drop(2).forEach { it.first.isHidden = true }
            } else {
                val (thirdEventChip, thirdTextLayout) = events[2]
                eventChipDrawer.draw(thirdEventChip, canvas = this, thirdTextLayout)
                events.drop(3).forEach { it.first.isHidden = true }
            }
        }
    }

    private fun Canvas.drawExpandInfo(eventsCount: Int, priorEventChip: EventChip) {
        // Draw "+ X" blob
        val text = "+ $eventsCount"

        val textPaint = viewState.expandInfoTextPaint.apply {
            textAlign = if (viewState.isLtr) Paint.Align.LEFT else Paint.Align.RIGHT
        }

        val textLayout = text.semibold().toTextLayout(textPaint, priorEventChip.bounds.width().toInt())

        val x = if (viewState.isLtr) {
            priorEventChip.bounds.left + viewState.eventPaddingHorizontal.toFloat()
        } else {
            priorEventChip.bounds.right - viewState.eventPaddingHorizontal.toFloat()
        }

        // Draw text background
        val radius = viewState.eventCornerRadius.toFloat()
        val left = priorEventChip.bounds.left
        val top = priorEventChip.bounds.bottom + viewState.eventMarginVertical
        val right = priorEventChip.bounds.right
        val bottom = priorEventChip.bounds.bottom + viewState.eventPaddingVertical * 2 + textLayout.height
        val backgroundRectF = RectF(left, top, right, bottom)
        viewState.allDayMoreMap[priorEventChip.startTime] = backgroundRectF
        drawRoundRect(backgroundRectF, radius, radius, viewState.expandInfoBackgroundPaint)

        // Draw text label
        val verticalOffset = (backgroundRectF.height() - textLayout.height) / 2f
        val y = top + verticalOffset
        withTranslation(
            x = x,
            y = y
        ) {
            draw(textLayout)
        }
    }
}

private class HeaderDrawer(
    context: Context,
    private val viewState: ViewState,
    private val eventChipsCacheProvider: EventChipsCacheProvider
) : Drawer {

    private val upArrow: Drawable by lazy {
        checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_arrow_up))
    }

    private val downArrow: Drawable by lazy {
        checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_arrow_down))
    }

    override fun draw(canvas: Canvas) {
        val width = viewState.viewWidth.toFloat()

        val backgroundPaint = if (viewState.showHeaderBottomShadow) {
            viewState.headerBackgroundWithShadowPaint
        } else {
            viewState.headerBackgroundPaint
        }

        canvas.drawRect(0f, 0f, width, viewState.headerHeight, backgroundPaint)

        if (viewState.showWeekNumber && viewState.numberOfVisibleDays > 1) {
            canvas.drawWeekNumber()
        }

        if (viewState.showTimeColumnSeparator) {
            canvas.drawTimeColumnSeparatorExtension()
        }

        if (viewState.showAllDayEventsToggleArrow) {
            canvas.drawAllDayEventsToggleArrow()
        }

        if (viewState.numberOfVisibleDays == 1 && eventChipsCacheProvider()?.allEventChipsInDateRange(viewState.dateRange)?.isEmpty() == true) {
            canvas.drawNoEventsLabel()
        }

        if (viewState.showHeaderBottomLine) {
            val y = viewState.headerHeight - viewState.headerBottomLinePaint.strokeWidth
            canvas.drawLine(0f, y, width, y, viewState.headerBottomLinePaint)
        }
    }

    private fun Canvas.drawNoEventsLabel() {

        val text = viewState.noEventsLabel
        val textPaint = TextPaint(viewState.headerTextPaint).apply {
            textAlign = Paint.Align.LEFT
            color = viewState.hintHeaderTextColor
        }

        withTranslation(
            x = viewState.timeColumnWidth + viewState.eventPaddingHorizontal.toFloat(),
            y = viewState.eventMarginVertical.toFloat() + viewState.eventPaddingVertical.toFloat()
        ) {
            draw(
                text.toTextLayout(textPaint, textPaint.measureText(text).toInt())
            )
        }
    }

    private fun Canvas.drawWeekNumber() {
        val weekNumber = viewState.weekNumber.toString()

        val bounds = viewState.weekNumberBounds
        val textPaint = viewState.weekNumberTextPaint

        val textHeight = textPaint.textHeight
        val textOffset = (textHeight / 2f).roundToInt() - textPaint.descent().roundToInt()

        val backgroundRect = RectF(
            bounds.centerX() - viewState.weekNumberBackgroundSize / 2,
            bounds.centerY() - viewState.weekNumberBackgroundSize / 2,
            bounds.centerX() + viewState.weekNumberBackgroundSize / 2,
            bounds.centerY() + viewState.weekNumberBackgroundSize / 2
        )

        drawRect(bounds, viewState.headerBackgroundPaint)

        val backgroundPaint = viewState.weekNumberBackgroundPaint
        val radius = viewState.weekNumberBackgroundCornerRadius
        drawRoundRect(backgroundRect, radius, radius, backgroundPaint)

        val textLayout = weekNumber.semibold().toTextLayout(textPaint, viewState.weekNumberBackgroundSize.toInt())
        withTranslation(
            x = bounds.centerX(),
            y = bounds.centerY() - textHeight / 2,
        ) {
            draw(textLayout)
        }
    }

    private fun Canvas.drawTimeColumnSeparatorExtension() {
        val startX = if (viewState.isLtr) {
            viewState.timeColumnWidth - viewState.timeColumnSeparatorPaint.strokeWidth / 2
        } else {
            viewState.viewWidth - viewState.timeColumnWidth
        }

        val startY = viewState.headerPadding
        val stopY = viewState.headerHeight

        drawLine(startX, startY, startX, stopY, viewState.timeColumnSeparatorPaint)
    }

    private fun Canvas.drawAllDayEventsToggleArrow() = with(viewState) {
        val bottom = (headerHeight - headerPadding).roundToInt()
        val top = bottom - currentAllDayEventHeight

        val width = weekNumberBounds.width().roundToInt()
        val height = bottom - top

        val left = weekNumberBounds.left.roundToInt() + (width - height) / 2
        val right = (left + height)

        if (allDayEventsExpanded) {
            upArrow.setBounds(left, top, right, bottom)
            upArrow.draw(this@drawAllDayEventsToggleArrow)
        } else if (showHeaderDownArrow) {
            downArrow.setBounds(left, top, right, bottom)
            downArrow.draw(this@drawAllDayEventsToggleArrow)
        }
    }
}

private val Paint.textHeight: Int
    get() = (descent() - ascent()).roundToInt()

private fun Paint.getTextBounds(text: String): Rect {
    val rect = Rect()
    getTextBounds(text, 0, text.length, rect)
    return rect
}
