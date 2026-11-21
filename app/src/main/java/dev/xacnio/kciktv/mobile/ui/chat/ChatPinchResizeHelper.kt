/**
 * File: ChatPinchResizeHelper.kt
 *
 * Description: Handles two-finger pinch-to-resize gestures inside the chat RecyclerView
 * to adjust chat message scale in 5% steps.
 *
 * Author: Xacnio
 *
 */
package dev.xacnio.kciktv.mobile.ui.chat

import android.view.MotionEvent
import android.widget.Toast
import dev.xacnio.kciktv.R
import dev.xacnio.kciktv.mobile.MobilePlayerActivity
import dev.xacnio.kciktv.shared.ui.adapter.ChatSizingMath
import kotlin.math.hypot

class ChatPinchResizeHelper(private val activity: MobilePlayerActivity) {
    private var active = false
    private var startSpan = 0.0
    private var startScale = ChatSizingMath.DEFAULT_SCALE
    private var toast: Toast? = null

    fun onDispatchTouchEvent(ev: MotionEvent, dispatchCancel: (MotionEvent) -> Unit): Boolean {
        if (!active) {
            if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                ev.pointerCount == 2 &&
                activity.prefs.chatPinchToResizeEnabled &&
                !activity.chatSettingsSheetManager.isPanelShowing() &&
                areBothPointersInsideRecyclerView(ev)
            ) {
                startSpan = hypot(
                    (ev.getX(0) - ev.getX(1)).toDouble(),
                    (ev.getY(0) - ev.getY(1)).toDouble()
                ).coerceAtLeast(1.0)
                startScale = activity.prefs.chatMessageScale
                active = true
                val cancelEvent = MotionEvent.obtain(ev)
                cancelEvent.action = MotionEvent.ACTION_CANCEL
                try {
                    dispatchCancel(cancelEvent)
                } finally {
                    cancelEvent.recycle()
                }
                return true
            }
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount >= 2) {
                    val span = hypot(
                        (ev.getX(0) - ev.getX(1)).toDouble(),
                        (ev.getY(0) - ev.getY(1)).toDouble()
                    )
                    val newScale = ChatSizingMath.snapScaleToStep(startScale * (span / startSpan))
                    if (newScale != activity.prefs.chatMessageScale) {
                        activity.prefs.chatMessageScale = newScale
                        activity.chatUiManager.chatAdapter.setChatMessageScale(newScale)
                        toast?.cancel()
                        toast = Toast.makeText(
                            activity,
                            activity.getString(R.string.chat_pinch_scale_toast, newScale),
                            Toast.LENGTH_SHORT
                        ).apply { show() }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.pointerCount <= 2) {
                    active = false
                }
            }
        }
        return true
    }

    private fun areBothPointersInsideRecyclerView(ev: MotionEvent): Boolean {
        if (ev.pointerCount < 2) return false
        val rv = activity.binding.chatRecyclerView
        if (!rv.isShown || rv.width <= 0 || rv.height <= 0) return false
        val loc = IntArray(2)
        rv.getLocationInWindow(loc)
        val left = loc[0].toFloat()
        val top = loc[1].toFloat()
        val right = left + rv.width
        val bottom = top + rv.height
        for (i in 0..1) {
            val x = ev.getX(i)
            val y = ev.getY(i)
            if (x < left || x >= right || y < top || y >= bottom) {
                return false
            }
        }
        return true
    }
}
