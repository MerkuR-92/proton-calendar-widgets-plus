package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import me.proton.android.calendar.common.TimberLogger
import java.util.*

class InterceptTouchConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var allowScrolling: Boolean = false
    var agendaPager: View? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        return when (ev.actionMasked) {
            // Always handle the case of the touch gesture being complete.
            MotionEvent.ACTION_DOWN -> {
                TimberLogger.e("Test test onInterceptTouchEvent ACTION_DOWN")
                if (allowScrolling) {
                    false
                } else {
                    val delegateArea = Rect()
                    agendaPager?.getHitRect(delegateArea)
                    TimberLogger.e("Test test onInterceptTouchEvent ACTION_DOWN delegateArea $delegateArea")
                    delegateArea.contains(ev.x.toInt(), ev.y.toInt())
                }
            }
            MotionEvent.ACTION_UP -> {
                TimberLogger.e("Test test onInterceptTouchEvent ACTION_UP")
                false // Do not intercept touch event, let the child handle it
            }
            MotionEvent.ACTION_MOVE -> {
                TimberLogger.e("Test test onInterceptTouchEvent ACTION_MOVE")
                if (allowScrolling) {
                    false
                } else {
                    val delegateArea = Rect()
                    agendaPager?.getHitRect(delegateArea)
                    TimberLogger.e("Test test onInterceptTouchEvent ACTION_MOVE delegateArea $delegateArea")
                    delegateArea.contains(ev.x.toInt(), ev.y.toInt())
                }
            }
            else -> {
                // In general, we don't want to intercept touch events. They should be
                // handled by the child view.
                false
            }
        }
    }

}