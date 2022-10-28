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
import kotlin.math.abs
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
            it.first.height.toFloat() + viewState.headerTodaySquareMarginTop + it.second.height.toFloat() + viewState.headerPadding / 2f
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
            if (!date.isToday) color = viewState.weakHeaderTextColor
        }
        val datePaint = TextPaint(textPaint).apply {
            textSize = viewState.dateHeaderTextSize
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

        val squareTop = viewState.headerPadding + weekDayTextLayout.height + viewState.headerTodaySquareMarginTop
        val dateTop = squareTop + viewState.headerTodaySquareSize / 2f - dateTextLayout.height / 2f
        withTranslation(
            x = bounds.centerX(),
            y = dateTop,
        ) {
            draw(dateTextLayout)
        }

        if (date.withTimeZone(viewState.customTimeZone).isSameDate(nowAtTimezone(viewState.customTimeZone))) {
            val rect = RectF(
                bounds.centerX() - viewState.headerTodaySquareSize / 2f,
                squareTop,
                bounds.centerX() + viewState.headerTodaySquareSize / 2f,
                squareTop + viewState.headerTodaySquareSize
            )
            // Draw today square around date
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

        val weekDayStartX = startPixel + viewState.dayWidth / 2f
        val centerSquareX = startPixel + viewState.dayWidth / 2f
        val squareTop = viewState.headerPadding + viewState.headerTodaySquareMarginTop + weekDayTextLayout.height
        val dateY = squareTop + viewState.headerTodaySquareSize / 2f - dateTextLayout.height / 2f

        if (date.withTimeZone(viewState.customTimeZone).isSameDate(nowAtTimezone(viewState.customTimeZone))) {
            val rect = RectF(
                centerSquareX - viewState.headerTodaySquareSize / 2f,
                squareTop,
                centerSquareX + viewState.headerTodaySquareSize / 2f,
                squareTop + viewState.headerTodaySquareSize
            )
            // Draw today square around date
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
        // Draw weekday
        withTranslation(
            x = weekDayStartX,
            y = viewState.headerPadding,
        ) {
            draw(weekDayTextLayout)
        }

        val dateStartX = startPixel + viewState.dayWidth / 2f
        // Draw date
        withTranslation(
            x = dateStartX,
            y = dateY,
        ) {
            draw(dateTextLayout)
        }

        // Draw labels separator line
//        drawLine(
//            startPixel + viewState.dayWidth,
//            0f,
//            startPixel + viewState.dayWidth,
//            viewState.headerPadding + weekDayTextLayout.height,
//            viewState.headerBottomLinePaint
//        )
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
        // Keep previous date vertical index that are taken
        val previousIndexTaken = arrayListOf<Int>()
        for ((date, startPixel) in datesWithStartPixels) {
            // If we use a horizontal margin in the day view, we need to offset the start pixel.
            val modifiedStartPixel = when {
                viewState.isSingleDay -> startPixel + viewState.singleDayHorizontalPadding.toFloat()
                else -> startPixel
            }

            // Sort events by days count and then alphabetically
            val eventChips = eventChipsCacheProvider()?.allDayEventChipsByDate(date).orEmpty().sortedWith(
                compareByDescending<EventChip> { it.event.daysCount }.thenBy { it.event.title.toString() })
            // Keep current date vertical index that are taken
            val currentIndexTaken = arrayListOf<Int>()
            eventChips.forEachIndexed { index, eventChip ->
                if (viewState.isSingleDay) {
                    eventChip.verticalIndex = index
                    eventChip.updateBounds(startPixel = modifiedStartPixel)
                } else {

                    var modifiedIndex: Int = index
                    var topPixel: Float? = null
                    for (i in eventChips.indices) {
                        // Jump to next vertical index if space is already taken
                        if (previousIndexTaken.any { it == modifiedIndex }) modifiedIndex++
                        else break
                    }

                    eventsLabelLayouts.forEach {
                        if (it.key.eventId == eventChip.eventId) {
                            topPixel = it.key.bounds.top
                            modifiedIndex = it.key.verticalIndex
                        }
                    }
                    eventChip.verticalIndex = modifiedIndex
                    if (eventChip.isNotLastIndex) currentIndexTaken.add(modifiedIndex)
                    if (eventChip.event.isMultiDay) {
                        // Get start index of last date of multi day
                        val firstPixelForStartDate = getClosestDatePixelStart(datesWithStartPixels, eventChip.event.startTime.atStartOfDay.timeInMillis)
                        val firstPixelForEndDate = getClosestDatePixelStart(datesWithStartPixels, eventChip.event.endTime.atStartOfDay.timeInMillis)
                        val lastPixelForEndDate = firstPixelForEndDate?.let {
                            firstPixelForEndDate + viewState.dayWidth
                        }
                        eventChip.updateBounds(modifiedStartPixel, firstPixelForStartDate, lastPixelForEndDate, topPixel)
                    } else {
                        eventChip.updateBounds(startPixel = modifiedStartPixel, topPixel = topPixel)
                    }
                }
                if (eventChip.bounds.isNotEmpty) {
                    eventsLabelLayouts[eventChip] = textFitter.fitAllDayEvent(eventChip)
                } else {
                    eventsLabelLayouts.remove(eventChip)
                }
            }
            previousIndexTaken.clear()
            previousIndexTaken.addAll(currentIndexTaken)
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

    private fun getClosestDatePixelStart(dates: List<Pair<Calendar, Float>>, dateToFind: Long): Float? {
        var minDiff: Long = -1
        var pixel: Float? = null
        dates.forEach {
            val date = it.first
            val diffStart: Long = abs(dateToFind - date.timeInMillis)
            if (minDiff == -1L || diffStart < minDiff) {
                minDiff = diffStart
                pixel = it.second
            }
        }
        return pixel
    }

    private fun EventChip.updateBounds(
        startPixel: Float,
        firstPixelForStartDate: Float? = null,
        lastPixelForEndDate: Float? = null,
        topPixel: Float? = null
    ) {
        val candidate = boundsCalculator.calculateAllDayEvent(
            eventChip = this,
            startPixel,
            firstPixelForStartDate,
            lastPixelForEndDate,
            topPixel
        )
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
        val previousEvents = arrayListOf<Pair<EventChip, StaticLayout>>()
        for (date in viewState.dateRange) {
            val events = allDayEventLayouts
                .filter { it.key.startTime.isSameDate(date) }
                .toList()

            if (viewState.arrangeAllDayEventsVertically) {
                renderEventsVertically(events.sortedBy { it.first.bounds.top }, previousEvents)
            } else {
                renderEventsHorizontally(events)
            }
            previousEvents.clear()
            previousEvents.addAll(events)
        }
    }

    private fun Canvas.renderEventsHorizontally(events: List<Pair<EventChip, StaticLayout>>) {
        for ((eventChip, textLayout) in events) {
            eventChipDrawer.draw(eventChip, canvas = this, textLayout)
        }
    }

    private fun Canvas.renderEventsVertically(events: List<Pair<EventChip, StaticLayout>>, previousEvents: List<Pair<EventChip, StaticLayout>>) {
        // Un-hide all events. To prevent any click handler from mapping a click to a hidden event,
        // we set isHidden to true for all events that aren't shown in the collapsed state.
        events.forEach { event ->
            if (event.first.event.isMultiDay) {
                // Hide multi day event occurrence if previous one was hidden
                val previousEvent = previousEvents.firstOrNull { it.first.eventId == event.first.eventId }
                event.first.isHidden = previousEvent?.first?.isHidden ?: false
            } else event.first.isHidden = false
        }

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

        val datesWithStartPixels = viewState.dateRangeWithStartPixels
        val left =
            if (priorEventChip.event.isMultiDay) datesWithStartPixels.firstOrNull { it.first.isSameDate(priorEventChip.startTime) }?.second ?: priorEventChip.bounds.left
            else priorEventChip.bounds.left
        val right =
            if (priorEventChip.event.isMultiDay) left + viewState.dayWidth - viewState.columnGap
            else priorEventChip.bounds.right

        val x = if (viewState.isLtr) {
            left + viewState.eventPaddingHorizontal.toFloat()
        } else {
            right - viewState.eventPaddingHorizontal.toFloat()
        }

        // Draw text background
        val radius = viewState.eventCornerRadius.toFloat()
        val top = priorEventChip.bounds.bottom + viewState.eventMarginVertical
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

        if (viewState.isSingleDay && !viewState.showLoadingEvents && eventChipsCacheProvider()?.allEventChipsInDateRange(viewState.dateRange)?.isEmpty() == true) {
            canvas.drawDayViewHeaderText(viewState.noEventsLabel)
        }

        if (viewState.isSingleDay && viewState.showLoadingEvents) {
            canvas.drawDayViewHeaderText(viewState.loadingEventsLabel)
        }

        if (viewState.showHeaderBottomLine) {
            val y = viewState.headerHeight - viewState.headerBottomLinePaint.strokeWidth
            canvas.drawLine(0f, y, width, y, viewState.headerBottomLinePaint)
        }
    }

    private fun Canvas.drawDayViewHeaderText(text: String) {

        val textPaint = TextPaint(viewState.headerTextPaint).apply {
            textAlign = Paint.Align.LEFT
            color = viewState.hintHeaderTextColor
        }

        withTranslation(
            x = viewState.timeColumnWidth + viewState.headerNoEventsMarginStart,
            y = viewState.headerPadding
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

        val startY = 0f
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
