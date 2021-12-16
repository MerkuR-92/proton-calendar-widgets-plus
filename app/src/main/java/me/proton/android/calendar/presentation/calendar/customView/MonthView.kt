package me.proton.android.calendar.presentation.calendar.customView

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.item_month_view_grid.view.*
import me.proton.android.calendar.R
import me.proton.android.calendar.common.MAX_MINI_EVENT_COUNT
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.spToPixel
import java.time.LocalDate
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos

class MonthView : ViewGroup {

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        setWillNotDraw(false)

        eventRadius = context.dpToPixel(4).toFloat()

        val robotoMediumTypeface = Typeface.createFromAsset(context.assets, "fonts/Roboto-Medium.ttf")

        basicTitlePaint = TextPaint().apply {
            style = Paint.Style.FILL
            textSize = context.spToPixel(10F)
            typeface = robotoMediumTypeface
            color = ContextCompat.getColor(context, R.color.text_on_calendar_color)
            isAntiAlias = true
        }

        dayTitlePaint = TextPaint().apply {
            style = Paint.Style.FILL
            textSize = context.spToPixel(12F)
            typeface = robotoMediumTypeface
            color = ContextCompat.getColor(context, R.color.text_norm)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        highlightDayTitlePaint = TextPaint(dayTitlePaint).apply {
            color = ContextCompat.getColor(context, R.color.brand_norm)
        }

        offsetDayTitlePaint = TextPaint(dayTitlePaint).apply {
            color = ContextCompat.getColor(context, R.color.text_hint)
        }

        gridItemSeparatorPaint = Paint().apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.separator_norm)
            strokeWidth = context.dpToPixel(1).toFloat()
            isAntiAlias = true
        }
    }

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0) { }

    private var monthViewEventsMap: HashMap<Int, List<MonthViewEvent>> = hashMapOf()

    private var gridItemWidth = 0F
    private var gridItemHeight = 0F
    private var parentWidth = 0F
    private var parentHeight = 0F

    private var eventRadius: Float = 0F

    private var basicTitlePaint: TextPaint

    private var dayTitlePaint: TextPaint
    private var offsetDayTitlePaint: TextPaint
    private var highlightDayTitlePaint: TextPaint

    private var gridItemSeparatorPaint: Paint

    private var dayList: List<LocalDate>? = null
    private var month: Month? = null

    private var showWeekNumbers: Boolean = false

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        // TODO Clear Canvas
//        canvas?.drawColor(context.getColor(R.color.background_norm))

        drawMonthGrid(canvas)

        // TODO Optimise to avoid redrawing everything
        drawEvents(canvas)
    }

    private fun drawMonthGrid(canvas: Canvas?) {

        val dayList = this.dayList
        val month = this.month
        if (dayList == null || month == null) return

        val topMargin = context.dpToPixel(19)
        val gridItemSpacing = context.dpToPixel(1)

        var listIndex = 0
        for (rowIndex in 0 until 6) {
            for (columnIndex in 0 until 7) {
                val date = dayList[listIndex]
                val text = date.dayOfMonth.toString()

                val gridItemStart = (columnIndex * gridItemWidth)
                val gridItemEnd = gridItemStart + gridItemWidth
                val gridItemTop = (rowIndex * gridItemHeight) + topMargin + gridItemSpacing

                canvas?.drawText(
                    text,
                    0,
                    text.length,
                    gridItemStart + (gridItemWidth / 2),
                    gridItemTop,
                    if (date == LocalDate.now()) highlightDayTitlePaint
                    else if (date.month != month) offsetDayTitlePaint
                    else dayTitlePaint
                )

                listIndex++
            }
        }

        // Draw lines to split grid items
        val gridSeparatorList = arrayListOf<Float>()
        for (rowIndex in 0 until 6) {
            gridSeparatorList.add(0F) // startX
            gridSeparatorList.add(rowIndex * gridItemHeight) // startY
            gridSeparatorList.add(parentWidth) // stopX
            gridSeparatorList.add(rowIndex * gridItemHeight) // stopY
        }
        val columnStart = if (showWeekNumbers) 0 else 1
        val columnEnd = if (showWeekNumbers) 8 else 7
        for (columnIndex in columnStart until columnEnd) {
            gridSeparatorList.add(columnIndex * gridItemWidth) // startX
            gridSeparatorList.add(0F) // startY
            gridSeparatorList.add(columnIndex * gridItemWidth) // stopX
            gridSeparatorList.add(parentHeight) // stopY
        }
        canvas?.drawLines(
            // startX, startY, stopX, stopY
            gridSeparatorList.toFloatArray(),
            gridItemSeparatorPaint
        )
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

        if (!monthViewEvent.drawRect) return

        // Draw main rect containing text
        canvas?.drawRoundRect(
            monthViewEvent.eventRect,
            eventRadius,
            eventRadius,
            monthViewEvent.eventRectPaint
        )

        // Draw side strip after to cover the left side of original rect
        canvas?.drawRoundRect(
            monthViewEvent.eventLeftSideStripRect,
            eventRadius,
            eventRadius,
            monthViewEvent.eventLeftSideStripPaint
        )

        // Draw line to cover right half of side strip rect
        canvas?.drawLines(
            monthViewEvent.eventStripSeparationLine,
            monthViewEvent.eventStripSeparationPaint
        )

        if (monthViewEvent.isUnanswered) {
            canvas?.drawLines(
                monthViewEvent.unansweredStripes,
                monthViewEvent.unansweredStripesPaint
            )
        }

        if (monthViewEvent.decryptionFailed && !monthViewEvent.isMiniEvent) { // TODO Do we also apply it to mini blobs ?
            canvas?.drawRoundRect(
                monthViewEvent.decryptionFailedRect,
                eventRadius,
                eventRadius,
                monthViewEvent.decryptionFailedPaint
            )
        }
    }

    /**
     * Draws the event title
     */
    private fun drawEventTitle(canvas: Canvas?, monthViewEvent: MonthViewEvent, eventTitle: String) {
        // Draw text for event title
        if (eventTitle.isNotBlank() && monthViewEvent.ellipsizedTitle.isNotBlank()) {
            canvas?.drawText(
                eventTitle,
                0,
                monthViewEvent.ellipsizedTitle.length,
                monthViewEvent.titleX,
                monthViewEvent.titleY,
                monthViewEvent.titlePaint
            )
        }
    }

    /**
     * Draws the plus icon for more events
     */
    private fun drawPlusIcon(canvas: Canvas?, monthViewEvent: MonthViewEvent) {

        canvas?.drawRect(
            monthViewEvent.plusIconVerticalRect,
            monthViewEvent.plusIconPaint
        )

        canvas?.drawRect(
            monthViewEvent.plusIconHorizontalRect,
            monthViewEvent.plusIconPaint
        )

    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Nothing to do here
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val refreshView = (parentWidth != 0F && parentWidth != measuredWidth.toFloat()) &&
                this.monthViewEventsMap.isNotEmpty()

        parentWidth = measuredWidth.toFloat()
        parentHeight = measuredHeight.toFloat()

        gridItemWidth = parentWidth / 7
        gridItemHeight = parentHeight / 6

        if (refreshView) prepareAndDrawMonthViewEvents()

        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    fun prepareMonthGrid(skeletonList: List<LocalDate>, forMonth: Month) {
        dayList = skeletonList
        month = forMonth
    }

    fun getMaxEventCount(): Int {
        val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
        val eventHeight = context.dpToPixel(16) + context.dpToPixel(1) // TODO EXTRACT DIMENS // 1 dp for event blob top margin
        val miniEventsHeight = context.dpToPixel(10)
        return ((gridItemHeight - headerHeight - miniEventsHeight - context.dpToPixel(2)) / eventHeight).toInt() // 2 dp for top grid item top + bottom margin
    }

    fun setMonthViewEvents(monthViewEventsMap: Map<Int, List<MonthViewEvent>>, showWeekNumbers: Boolean) {

        this.showWeekNumbers = showWeekNumbers

        this.monthViewEventsMap.clear()

        this.monthViewEventsMap.putAll(monthViewEventsMap)

        prepareAndDrawMonthViewEvents()
    }

    private fun prepareAndDrawMonthViewEvents() {
        val maxEventCount = getMaxEventCount()
        this.monthViewEventsMap.forEach {
            val dayIndex = it.key
            // 7 is number of column
            val row = dayIndex / 7
            val column = dayIndex - (row * 7)
            var miniEventIndex = 0 // Workaround for monthViewEvent.indexInDay skipping some indexes because of previous day multi day events
            it.value.forEach { monthViewEvent ->
                if (monthViewEvent.indexInDay >= maxEventCount) {
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
                    miniEventIndex++
                } else {
                    monthViewEvent.prepareEventBlob(
                        context,
                        basicTitlePaint,
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

    fun setShowWeekNumbers(showWeekNumbers: Boolean) {

        this.showWeekNumbers = showWeekNumbers
    }

    data class MonthViewEvent(
        val eventView: View?,
        val indexInDay: Int,
        val daySpanCount: Int,
        val daySpanIndex: Int,
        val calendarColor: Int,
        val pastEvent: Boolean,
        val isUnanswered: Boolean,
        val strikeThroughTitle: Boolean,
        val decryptionFailed: Boolean,
        val eventTitle: String?
    ) {

        lateinit var eventRect: RectF
        lateinit var eventRectPaint: Paint

        lateinit var eventStripSeparationPaint: Paint
        lateinit var eventStripSeparationLine: FloatArray

        lateinit var eventLeftSideStripRect: RectF
        lateinit var eventLeftSideStripPaint: Paint

        lateinit var titlePaint: TextPaint

        var ellipsizedTitle: CharSequence = ""

        var titleX: Float = 0F
        var titleY: Float = 0F

        lateinit var plusIconHorizontalRect: RectF
        lateinit var plusIconVerticalRect: RectF

        lateinit var plusIconPaint: Paint

        lateinit var decryptionFailedRect: RectF
        lateinit var decryptionFailedPaint: Paint

        lateinit var unansweredStripes: FloatArray

        lateinit var unansweredStripesPaint: Paint

        var drawRect = true
        var isMiniEvent = false
        var isPlusIcon = false

        fun prepareEventBlob(context: Context, basicTitlePaint: TextPaint, gridItemWidth: Float, gridItemHeight: Float, column: Int, row: Int) {

            // For multi day events, we only draw one blob per row, skip the others
            if (daySpanCount > 1 && daySpanIndex > 1 && column > 0) {
                drawRect = false
                return
            }

            val start = (column * gridItemWidth + context.dpToPixel(1))
            val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
            val eventHeight = context.dpToPixel(16)
            val top = ((row * gridItemHeight) + headerHeight + (indexInDay * eventHeight) + (indexInDay * context.dpToPixel(1)))
            val gridItemExtensionCount =
                if (daySpanIndex == 1 && column + daySpanCount > 6) 6 - column
                else if (daySpanIndex > 1 && daySpanCount - daySpanIndex > 6) 6
                else daySpanCount - daySpanIndex
            val end = ((column * gridItemWidth) + (gridItemExtensionCount * gridItemWidth) + gridItemWidth - context.dpToPixel(2))
            val bottom = top + eventHeight

            val sideStripWidth = context.dpToPixel(3).toFloat()
            val textStart = start + sideStripWidth + context.dpToPixel(2)
            val eventRectEndMargin = context.dpToPixel(1)
            val textEndMargin = context.dpToPixel(2)

            // Prepare the event left strip with darkened color
            eventLeftSideStripPaint = Paint().apply {
                style = Paint.Style.FILL
                color = Color.parseColor(AndroidUtils.darkenCalendarColor(
                    "#${Integer.toHexString(calendarColor and 0x00ffffff)}")
                )
                isAntiAlias = true
            }
            eventLeftSideStripRect = RectF(
                start,
                top,
                start + (sideStripWidth * 2), // We double the width to have a proper rounded top & bottom
                bottom
            )

            // Prepare the event main rect
            eventRectPaint = Paint().apply {
                style =
                    if (strikeThroughTitle) Paint.Style.STROKE
                    else Paint.Style.FILL
                color =
                    if (isUnanswered) ContextCompat.getColor(context, R.color.background_norm)
                    else if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else calendarColor
                isAntiAlias = true
            }
            eventRect = RectF(
                start,
                top,
                end - eventRectEndMargin,
                bottom
            )

            eventStripSeparationPaint = Paint(eventRectPaint).apply {
                strokeWidth = sideStripWidth + eventRectEndMargin
                style = Paint.Style.FILL
                if (strikeThroughTitle) color = ContextCompat.getColor(context, R.color.background_norm)
            }
            eventStripSeparationLine = floatArrayOf(
                eventLeftSideStripRect.centerX() + (sideStripWidth / 2), top, eventLeftSideStripRect.centerX() + (sideStripWidth / 2), bottom
            )

            // Prepare the event title if needed
            if (eventTitle != null && !decryptionFailed) {
                titlePaint = TextPaint(basicTitlePaint).apply {
                    color =
                        if (isUnanswered || (!pastEvent && strikeThroughTitle)) ContextCompat.getColor(context, R.color.text_norm)
                        else if (pastEvent) ContextCompat.getColor(context, R.color.text_weak)
                        else ContextCompat.getColor(context, R.color.text_on_calendar_color)
                    isStrikeThruText = strikeThroughTitle
                }

                titleX = textStart
                titleY = eventRect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2

                val titleWidth = eventRect.width() - textEndMargin - sideStripWidth

                ellipsizedTitle = TextUtils.ellipsize(
                    eventTitle,
                    titlePaint,
                    titleWidth,
                    TextUtils.TruncateAt.END
                )
            }

            if (decryptionFailed) {
                decryptionFailedPaint = Paint().apply {
                    val colorToBrighten =
                        if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                        else calendarColor
                    color = Color.parseColor(AndroidUtils.brightenCalendarColor(
                        "#${Integer.toHexString(colorToBrighten and 0x00ffffff)}", if (pastEvent) 0.04f else 0.18f)
                    )
                    isAntiAlias = true
                }

                decryptionFailedRect = RectF(
                    start + context.dpToPixel(5),
                    top + context.dpToPixel(5),
                    end - context.dpToPixel(5),
                    bottom - context.dpToPixel(5)
                )
            }

            if (isUnanswered) {
                prepareStripes(context, sideStripWidth)
            }
        }

        private fun prepareStripes(context: Context, sideStripWidth: Float) {
            val lineWidth = context.dpToPixel(1) / 2F

            unansweredStripesPaint = Paint().apply {
                color =
                    if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else Color.parseColor(AndroidUtils.brightenCalendarColor(
                        "#${Integer.toHexString(calendarColor and 0x00ffffff)}", 0.18f)
                    )
                strokeWidth = lineWidth
                isAntiAlias = true
            }

            val lineGap = context.dpToPixel(4)

            val eventWidth = eventRect.width() - sideStripWidth
            val totalDistance = eventWidth + eventRect.height()

            val stripesArrayList = arrayListOf<Float>()
            var distance = 0.0
            val rectStartX = eventRect.left + sideStripWidth
            while (distance < totalDistance) {

                val startX =
                    if (distance < eventWidth) rectStartX + distance.toFloat()
                    else rectStartX + eventWidth
                val startY =
                    if (distance < eventWidth) eventRect.top
                    else eventRect.top + (distance.toFloat() - eventWidth)
                val stopX =
                    if (distance < eventRect.height()) rectStartX
                    else rectStartX + (distance.toFloat() - eventRect.height())
                val stopY =
                    if (distance < eventRect.height()) eventRect.top + distance.toFloat()
                    else eventRect.top + eventRect.height()

                stripesArrayList.add(startX)
                stripesArrayList.add(startY)
                stripesArrayList.add(stopX)
                stripesArrayList.add(stopY)

                distance += ((lineGap + lineWidth) / cos(PI / 4))

            }
            unansweredStripes = stripesArrayList.toFloatArray()
        }

        fun prepareMiniEventBlob(context: Context, gridItemWidth: Float, gridItemHeight: Float, column: Int, row: Int, maxEventCount: Int, miniEventCount: Int, miniEventIndex: Int) {

            isMiniEvent = true

            val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
            val eventHeight = context.dpToPixel(16)
            val miniEventHeight = context.dpToPixel(6)
            val eventRectEndMargin = context.dpToPixel(1)

            // Prepare the mini event blob rects

            val start: Float
            val end: Float

            val top = ((row * gridItemHeight) + headerHeight + (maxEventCount * eventHeight) + (maxEventCount * context.dpToPixel(1)) + context.dpToPixel(2))
            val bottom = top + miniEventHeight

            val columnStart = (column * gridItemWidth + context.dpToPixel(1))
            if (miniEventCount == 1) {
                // Display only one mini blob
                start = columnStart
                end = ((column * gridItemWidth) + gridItemWidth - context.dpToPixel(2))
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

            val sideStripWidth = context.dpToPixel(3).toFloat()

            eventLeftSideStripPaint = Paint().apply {
                color = Color.parseColor(AndroidUtils.darkenCalendarColor(
                    "#${Integer.toHexString(calendarColor and 0x00ffffff)}")
                )
                isAntiAlias = true
            }

            eventLeftSideStripRect = RectF(
                start,
                top,
                start + (sideStripWidth * 2), // We double the width to have a proper rounded top & bottom
                bottom
            )

            eventRectPaint = Paint().apply {
                color =
                    if (isUnanswered) ContextCompat.getColor(context, R.color.background_norm)
                    else if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else calendarColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            eventRect = RectF(
                start,
                top,
                end - eventRectEndMargin,
                bottom
            )

            eventStripSeparationPaint = Paint(eventRectPaint).apply {
                strokeWidth = sideStripWidth + eventRectEndMargin
            }
            eventStripSeparationLine = floatArrayOf(
                eventLeftSideStripRect.centerX() + (sideStripWidth / 2), top, eventLeftSideStripRect.centerX() + (sideStripWidth / 2), bottom
            )

            if (isUnanswered) {
                prepareStripes(context, sideStripWidth)
            }
        }

        fun preparePlusIcon(context: Context, gridItemWidth: Float, gridItemHeight: Float, column: Int, row: Int, maxEventCount: Int) {
            isPlusIcon = true

            val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
            val eventHeight = context.dpToPixel(16)
            val miniEventHeight = context.dpToPixel(6)

            val top = ((row * gridItemHeight) + headerHeight + (maxEventCount * eventHeight) + (maxEventCount * context.dpToPixel(1)) + context.dpToPixel(2))
            val end = (column * gridItemWidth + context.dpToPixel(1) + gridItemWidth - context.dpToPixel(5))
            val start = end - context.dpToPixel(6)
            val bottom = top + miniEventHeight

            val horizontalHeight = (bottom - top) / 2 - context.dpToPixel(1)
            plusIconHorizontalRect = RectF(
                start,
                top + horizontalHeight,
                end,
                bottom - horizontalHeight
            )

            val verticalWidth = (end - start) / 2 - context.dpToPixel(1)
            plusIconVerticalRect = RectF(
                start + verticalWidth,
                top,
                end - verticalWidth,
                bottom
            )

            plusIconPaint = Paint().apply {
                color =
                    if (eventTitle == null) {
                        val color = ContextCompat.getColor(context, R.color.interaction_weak_norm)
                        Color.parseColor(AndroidUtils.darkenCalendarColor(
                            "#${Integer.toHexString(color and 0x00ffffff)}")
                        )
                    } else ContextCompat.getColor(context, R.color.icon_weak)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }
    }
}
