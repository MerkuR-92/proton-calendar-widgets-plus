package me.proton.android.calendar.presentation.calendar.customView

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import me.proton.android.calendar.R
import me.proton.android.calendar.common.MAX_MINI_EVENT_COUNT
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.spToPixel

class MonthView : ViewGroup {

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        setWillNotDraw(false)

        eventRadius = context.dpToPixel(4).toFloat()

        titlePaint.style = Paint.Style.FILL
        titlePaint.textSize = context.spToPixel(10F)
        titlePaint.typeface = Typeface.createFromAsset(context.assets, "fonts/Roboto-Medium.ttf")

        plusIconPaint.color = ContextCompat.getColor(context, R.color.interaction_strong_norm)
        plusIconPaint.style = Paint.Style.FILL
    }

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0) { }

    private var monthViewEventsMap: HashMap<Int, List<MonthViewEvent>> = hashMapOf()

    private var gridItemWidth = 0
    private var gridItemHeight = 0
    private var parentWidth = 0
    private var parentHeight = 0

    private var eventRadius: Float = 0F

    private var titlePaint: TextPaint = TextPaint()
    private var plusIconPaint: Paint = Paint()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        TimberLogger.e("Test test MonthView onDraw")

        // TODO Clear Canvas
//        canvas?.drawColor(context.getColor(R.color.background_norm))

        // TODO Optimise to avoid redrawing everything
        drawEvents(canvas)
    }

    private fun drawEvents(canvas: Canvas?) {
        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                if (monthViewEvent.isPlusIcon) drawPlusIcon(canvas, monthViewEvent)
                else drawEventRect(canvas, monthViewEvent)
            }
        }

        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                monthViewEvent.eventTitle?.let { eventTitle ->
                    drawEventTitle(canvas, monthViewEvent, eventTitle)
                }
            }
        }
    }

    /**
     * Draws the event rects with side strip
     */
    private fun drawEventRect(canvas: Canvas?, monthViewEvent: MonthViewEvent) {
        // Draw side strip first to cover the right side of it with background
        if (monthViewEvent.extendLeftSideStrip) {
            canvas?.drawRect(
                monthViewEvent.eventLeftSideStripRect,
                monthViewEvent.eventLeftSideStripPaint
            )
        } else {
            canvas?.drawRoundRect(
                monthViewEvent.eventLeftSideStripRect,
                eventRadius,
                eventRadius,
                monthViewEvent.eventLeftSideStripPaint
            )
        }

        // Draw main rect containing text
        canvas?.drawRect(
            monthViewEvent.eventRect,
            monthViewEvent.eventRectPaint
        )

        // Draw rect for right radius
        if (monthViewEvent.extendRightSideStrip) {
            canvas?.drawRect(
                monthViewEvent.eventRightSideStripRect,
                monthViewEvent.eventRectPaint
            )
        } else {
            canvas?.drawRoundRect(
                monthViewEvent.eventRightSideStripRect,
                eventRadius,
                eventRadius,
                monthViewEvent.eventRectPaint
            )
        }
    }

    /**
     * Draws the event title
     */
    private fun drawEventTitle(canvas: Canvas?, monthViewEvent: MonthViewEvent, eventTitle: String) {
        // Draw text for event title
        if (eventTitle.isNotBlank()) {
            val paint = titlePaint
            paint.color =
                if (monthViewEvent.pastEvent) ContextCompat.getColor(context, R.color.text_weak)
                else ContextCompat.getColor(context, R.color.text_on_calendar_color)
            canvas?.drawText(
                eventTitle,
                0,
                monthViewEvent.ellipsizedTitle.length,
                monthViewEvent.titleX,
                monthViewEvent.titleY,
                paint
            )
        }
    }

    /**
     * Draws the plus icon for more events
     */
    private fun drawPlusIcon(canvas: Canvas?, monthViewEvent: MonthViewEvent) {

        canvas?.drawRect(
            monthViewEvent.plusIconVerticalRect,
            plusIconPaint
        )

        canvas?.drawRect(
            monthViewEvent.plusIconHorizontalRect,
            plusIconPaint
        )

    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        TimberLogger.e("Test test MonthView onLayout changed $changed left $l top $t right $r bottom $b")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        TimberLogger.e("Test test MonthView onMeasure widthMeasureSpec $widthMeasureSpec heightMeasureSpec $heightMeasureSpec")

        parentWidth = measuredWidth
        parentHeight = measuredHeight

        TimberLogger.e("Test test MonthView onMeasure parentWidth $parentWidth parentHeight $parentHeight")

        gridItemWidth = parentWidth / 7
        gridItemHeight = parentHeight / 6

        TimberLogger.e("Test test MonthView onMeasure gridItemWidth $gridItemWidth gridItemHeight $gridItemHeight")

        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    fun getMaxEventCount(): Int {
        val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
        val eventHeight = context.dpToPixel(16) + context.dpToPixel(1) // TODO EXTRACT DIMENS // 1 dp for event blob top margin
        val miniEventsHeight = context.dpToPixel(10)
        return (gridItemHeight - headerHeight - miniEventsHeight - context.dpToPixel(2)) / eventHeight // 2 dp for top grid item top + bottom margin
    }

    fun setMonthViewEvents(monthViewEventsMap: Map<Int, List<MonthViewEvent>>) {

        this.monthViewEventsMap.clear()

        this.monthViewEventsMap.putAll(monthViewEventsMap)

        TimberLogger.e("Test test MonthView setMonthViewEvents monthViewEventsMap size ${this.monthViewEventsMap.size}")
        TimberLogger.e("Test test MonthView setMonthViewEvents eventViews size ${this.monthViewEventsMap.values.map { it.size }}")

        val maxEventCount = getMaxEventCount()
        TimberLogger.e("Test test maxEventCount $maxEventCount")
        this.monthViewEventsMap.forEach {
            val dayIndex = it.key
            // 7 is number of column
            val row = dayIndex / 7
            val column = dayIndex - (row * 7)
            it.value.forEachIndexed { index, monthViewEvent ->
                if (monthViewEvent.indexInDay >= maxEventCount) {
                    val miniEventIndex = monthViewEvent.indexInDay - maxEventCount
                    val miniEventCount = it.value.size - maxEventCount
                    if (miniEventIndex > MAX_MINI_EVENT_COUNT - 1) { // Extract max number of mini events
                        monthViewEvent.preparePlusIcon(
                            context,
                            gridItemWidth,
                            gridItemHeight,
                            column,
                            row,
                            maxEventCount
                        )
                    } else {
                        monthViewEvent.prepareMiniEventBlob(
                            context,
                            gridItemWidth,
                            gridItemHeight,
                            column,
                            row,
                            maxEventCount,
                            miniEventCount,
                            miniEventIndex
                        )
                    }
                } else {
                    monthViewEvent.prepareEventBlob(
                        context,
                        titlePaint,
                        gridItemWidth,
                        gridItemHeight,
                        column,
                        row
                    )
                }
            }
        }

        invalidate()
    }

    data class MonthViewEvent(
        val eventView: View?,
        val indexInDay: Int,
        val daySpanCount: Int,
        val daySpanIndex: Int,
        val calendarColor: Int,
        val pastEvent: Boolean,
        val eventTitle: String?
    ) {

        var eventRect: RectF = RectF()
        var eventLeftSideStripRect: RectF = RectF()
        var eventRightSideStripRect: RectF = RectF()

        var eventLeftSideStripPaint: Paint = Paint()
        var eventRectPaint: Paint = Paint()

        var ellipsizedTitle: CharSequence = ""

        var titleX: Float = 0F
        var titleY: Float = 0F

        var extendLeftSideStrip = false
        var extendRightSideStrip = false

        var isPlusIcon: Boolean = false

        var plusIconHorizontalRect: RectF = RectF()
        var plusIconVerticalRect: RectF = RectF()

        fun prepareEventBlob(context: Context, titlePaint: TextPaint, gridItemWidth: Int, gridItemHeight: Int, column: Int, row: Int) {
            val start = (column * gridItemWidth + context.dpToPixel(1)).toFloat()
            val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
            val eventHeight = context.dpToPixel(16)
            val top = ((row * gridItemHeight) + headerHeight + (indexInDay * eventHeight) + (indexInDay * context.dpToPixel(1))).toFloat()
            val end = ((column * gridItemWidth) + gridItemWidth - context.dpToPixel(2)).toFloat()
            val bottom = top + eventHeight

            if (daySpanCount > 1) {
                if (daySpanIndex == 1) {
                    TimberLogger.e("Test test multiday daySpanCount $daySpanCount daySpanIndex $daySpanIndex extend right strip")
                    extendRightSideStrip = column < 6
                } else if (daySpanIndex == daySpanCount) {
                    TimberLogger.e("Test test multiday daySpanCount $daySpanCount daySpanIndex $daySpanIndex display right side strip, extend left side")
                    extendLeftSideStrip = column > 0
                } else {
                    TimberLogger.e("Test test multiday daySpanCount $daySpanCount daySpanIndex $daySpanIndex extend both sides")
                    extendLeftSideStrip = column > 0
                    extendRightSideStrip = column < 6
                }
            }

            // Prepare the event blob rects

            eventLeftSideStripPaint.style = Paint.Style.FILL
            if (extendLeftSideStrip) {
                eventLeftSideStripPaint.color =
                    if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else calendarColor

                eventLeftSideStripRect.set(
                    start - context.dpToPixel(1),
                    top,
                    (start + context.dpToPixel(6)),
                    bottom
                )
            } else {
                eventLeftSideStripPaint.color = Color.parseColor(AndroidUtils.darkenCalendarColor(
                    "#${Integer.toHexString(calendarColor and 0x00ffffff)}")
                )

                eventLeftSideStripRect.set(
                    start,
                    top,
                    (start + context.dpToPixel(6)),
                    bottom
                )
            }

            eventRectPaint.color =
                if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                else calendarColor
            eventRectPaint.style = Paint.Style.FILL

            eventRect.set(
                start + context.dpToPixel(3),
                top,
                end - context.dpToPixel(3),
                bottom
            )

            if (extendRightSideStrip) {
                eventRightSideStripRect.set(
                    end - context.dpToPixel(6),
                    top,
                    end + context.dpToPixel(2),
                    bottom
                )
            } else {
                eventRightSideStripRect.set(
                    end - context.dpToPixel(6),
                    top,
                    end,
                    bottom
                )
            }

            TimberLogger.e("Test test multiday daySpanIndex $daySpanIndex")
            if (eventTitle != null && (daySpanIndex == 1 || column == 0)) {
                // Prepare the event blob title

                titleX = eventRect.left + context.dpToPixel(2)
                titleY = eventRect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2

                val availableColumns = 7 - column
                val titleWidth = eventRect.width() * (
                        if (availableColumns < daySpanCount) availableColumns
                        else daySpanCount)

                ellipsizedTitle = TextUtils.ellipsize(
                    eventTitle,
                    titlePaint,
                    titleWidth,
                    TextUtils.TruncateAt.END
                )
            }
        }

        fun prepareMiniEventBlob(context: Context, gridItemWidth: Int, gridItemHeight: Int, column: Int, row: Int, maxEventCount: Int, miniEventCount: Int, miniEventIndex: Int) {
            TimberLogger.e("Test test prepareMiniEventBlob gridItemWidth $gridItemWidth gridItemHeight $gridItemHeight column $column row $row maxEventCount $maxEventCount miniEventCount $miniEventCount miniEventIndex $miniEventIndex")

            val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
            val eventHeight = context.dpToPixel(16)
            val miniEventHeight = context.dpToPixel(6)

            // Prepare the mini event blob rects

            val start: Float
            val end: Float

            val top = ((row * gridItemHeight) + headerHeight + (maxEventCount * eventHeight) + (maxEventCount * context.dpToPixel(1)) + context.dpToPixel(2)).toFloat()
            val bottom = top + miniEventHeight

            val columnStart = (column * gridItemWidth + context.dpToPixel(1)).toFloat()
            if (miniEventCount == 1) {
                // Display only one mini blob
                start = columnStart
                end = ((column * gridItemWidth) + gridItemWidth - context.dpToPixel(2)).toFloat()
            } else if (miniEventCount > MAX_MINI_EVENT_COUNT) {
                // Display + icon
                val plusIconWidth = context.dpToPixel(8)
                val miniEventWidth = (gridItemWidth - plusIconWidth - (MAX_MINI_EVENT_COUNT * context.dpToPixel(2))) / MAX_MINI_EVENT_COUNT

                start = columnStart + (miniEventWidth * miniEventIndex)
                end = start + miniEventWidth - context.dpToPixel(2)
            } else {
                // Split width to display x number of mini event (x being constant for max number of mini event)
                val endMargin =
                    if (miniEventCount - 1 == miniEventIndex) 0
                    else context.dpToPixel(2)
                val miniEventWidth = (gridItemWidth - context.dpToPixel(3) - ((MAX_MINI_EVENT_COUNT - 1) * endMargin)) / MAX_MINI_EVENT_COUNT

                start = columnStart + (miniEventWidth * miniEventIndex)
                end = start + miniEventWidth - endMargin
            }

            eventLeftSideStripPaint.color = Color.parseColor(AndroidUtils.darkenCalendarColor(
                "#${Integer.toHexString(calendarColor and 0x00ffffff)}")
            )

            eventLeftSideStripRect.set(
                start,
                top,
                (start + context.dpToPixel(6)),
                bottom
            )

            eventRectPaint.color =
                if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                else calendarColor
            eventRectPaint.style = Paint.Style.FILL

            eventRect.set(
                start + context.dpToPixel(3),
                top,
                end - context.dpToPixel(3),
                bottom
            )

            eventRightSideStripRect.set(
                end - context.dpToPixel(6),
                top,
                end,
                bottom
            )
        }

        fun preparePlusIcon(context: Context, gridItemWidth: Int, gridItemHeight: Int, column: Int, row: Int, maxEventCount: Int) {
            isPlusIcon = true

            val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
            val eventHeight = context.dpToPixel(16)
            val miniEventHeight = context.dpToPixel(6)

            val top = ((row * gridItemHeight) + headerHeight + (maxEventCount * eventHeight) + (maxEventCount * context.dpToPixel(1)) + context.dpToPixel(2)).toFloat()
            val end = (column * gridItemWidth + context.dpToPixel(1) + gridItemWidth - context.dpToPixel(5)).toFloat()
            val start = end - context.dpToPixel(6)
            val bottom = top + miniEventHeight

            val horizontalHeight = (bottom - top) / 2 - context.dpToPixel(1)
            plusIconHorizontalRect.set(
                start,
                top + horizontalHeight,
                end,
                bottom - horizontalHeight
            )

            val verticalWidth = (end - start) / 2 - context.dpToPixel(1)
            plusIconVerticalRect.set(
                start + verticalWidth,
                top,
                end - verticalWidth,
                bottom
            )
        }
    }
}
