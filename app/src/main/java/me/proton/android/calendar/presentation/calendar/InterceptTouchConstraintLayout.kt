package me.proton.android.calendar.presentation.calendar

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import me.proton.android.calendar.common.TimberLogger

class InterceptTouchConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var allowScrolling: Boolean = false
    private var mIsScrolling: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        return when (ev.actionMasked) {
            // Always handle the case of the touch gesture being complete.
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                TimberLogger.e("Test test onInterceptTouchEvent ACTION_CANCEL / ACTION_UP")
                // Release the scroll.
                mIsScrolling = false
                false // Do not intercept touch event, let the child handle it
            }
            MotionEvent.ACTION_MOVE -> {
                TimberLogger.e("Test test onInterceptTouchEvent ACTION_MOVE")
                if (allowScrolling) {
                    false
                } else if (mIsScrolling) {
                    // We're currently scrolling, so yes, intercept the
                    // touch event!
                    true
                } else {
                    mIsScrolling = true
                    true
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