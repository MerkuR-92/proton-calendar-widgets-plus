package me.proton.android.calendar.presentation.calendar.customView

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel

class MonthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    /*
    Events order is as follow:
    - All day spans multiple days
    - Part day (ordered by time) spans multiple days
    - All day
    - Part day (ordered by time)
     */

    private var monthViewEventsMap: HashMap<Int, List<MonthViewEvent>> = hashMapOf()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        // TODO DRAW STUFF HERE
        TimberLogger.e("Test test MonthView onDraw")
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        TimberLogger.e("Test test MonthView onLayout changed $changed left $l top $t right $r bottom $b")
        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                val directionalRect = monthViewEvent.getDirectionalRect()
                monthViewEvent.eventView.layout(
                    directionalRect.left,
                    directionalRect.top,
                    directionalRect.right,
                    directionalRect.bottom
                )
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        TimberLogger.e("Test test MonthView onMeasure widthMeasureSpec $widthMeasureSpec heightMeasureSpec $heightMeasureSpec")

        val parentWidth = measuredWidth
        val parentHeight = measuredHeight

        TimberLogger.e("Test test MonthView onMeasure parentWidth $parentWidth parentHeight $parentHeight")

        val gridItemWidth = parentWidth / 7
        val gridItemHeight = parentHeight / 6

        TimberLogger.e("Test test MonthView onMeasure gridItemWidth $gridItemWidth gridItemHeight $gridItemHeight")

        measureEventsRect(parentWidth, gridItemHeight, gridItemWidth)
        measureEvents(gridItemHeight, gridItemWidth)

        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    fun setMonthViewEvents(monthViewEventsMap: Map<Int, List<MonthViewEvent>>) {
        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                removeView(monthViewEvent.eventView)
            }
        }

        this.monthViewEventsMap.clear()

        this.monthViewEventsMap.putAll(monthViewEventsMap)

        TimberLogger.e("Test test MonthView setMonthViewEvents monthViewEventsMap size ${this.monthViewEventsMap.size}")
        TimberLogger.e("Test test MonthView setMonthViewEvents eventViews size ${this.monthViewEventsMap.values.map { it.size }}")

        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                addView(monthViewEvent.eventView)
            }
        }
    }

    fun removeMonthViewEvents(): List<View> {
        val eventViews = this.monthViewEventsMap.values.flatten().map { it.eventView }

        setMonthViewEvents(hashMapOf())

        return eventViews
    }

    private fun measureEventsRect(parentWidth: Int, gridItemHeight: Int, gridItemWidth: Int) {
        // TODO Measure positions left top right bottom
        TimberLogger.e("Test test MonthView measureEventsRect")
        this.monthViewEventsMap.forEach {
            val dayIndex = it.key
            // 7 is number of column
            val row = dayIndex / 7
            val column = dayIndex - (row * 7)
            it.value.forEachIndexed { index, monthViewEvent ->
                val start = column * gridItemWidth + context.dpToPixel(1)
                val headerHeight = context.dpToPixel(26) // TODO EXTRACT DIMENS
                val eventHeight = context.dpToPixel(16)
                val top = (row * gridItemHeight) + headerHeight + (index * eventHeight) + (index * context.dpToPixel(1))
                val end = (column * gridItemWidth) + gridItemWidth - context.dpToPixel(2)
                val bottom = top + eventHeight
                val directionalRect = DirectionalRect()
                directionalRect.set(
                    false,
                    parentWidth,
                    start,
                    top,
                    end,
                    bottom
                )
                monthViewEvent.setDirectionalRect(
                    directionalRect
                )
            }
        }
    }

    private fun measureEvents(gridItemHeight: Int, gridItemWidth: Int) {
        // TODO Measure events height and width here
        //  extend width for multi day events
        TimberLogger.e("Test test MonthView measureEvents")
        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                monthViewEvent.eventView.measure(
                    MeasureSpec.makeMeasureSpec(gridItemWidth - context.dpToPixel(3), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(context.dpToPixel(16), MeasureSpec.EXACTLY)
                )
            }
        }
    }

    data class MonthViewEvent(
        val eventView: View,
        val daySpanCount: Int
    ) {
        // TODO Utils
        private var directionalRect: DirectionalRect = DirectionalRect()

        fun setDirectionalRect(directionalRect: DirectionalRect) {
            this.directionalRect = directionalRect
        }

        fun getDirectionalRect(): DirectionalRect {
            return this.directionalRect
        }
    }
}
