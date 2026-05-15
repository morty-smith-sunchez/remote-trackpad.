package com.example.remotetrackpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var sink: TrackpadSink? = null
    var holdEnabled: Boolean = false
        set(value) {
            field = value
            if (value) sink?.down(TrackpadSink.Button.LEFT)
            else sink?.up(TrackpadSink.Button.LEFT)
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#253041")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private data class Pt(val x: Float, val y: Float)

    private var last1: Pt? = null
    private var last2: Pt? = null
    private var moved = false
    private var downTime = 0L
    private var maxPointers = 0
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var longPressPosted = false
    private var lastPinchAt = 0L
    private val longPressRunnable = Runnable {
        if (!moved && maxPointers == 1) {
            longPressPosted = true
            sink?.down(TrackpadSink.Button.LEFT)
        }
    }

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(6f, 6f, width - 6f, height - 6f, 24f, 24f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    downTime = SystemClock.uptimeMillis()
                    moved = false
                    longPressPosted = false
                    gestureStartX = event.x
                    gestureStartY = event.y
                    removeCallbacks(longPressRunnable)
                    postDelayed(longPressRunnable, 450)
                }
                maxPointers = maxOf(maxPointers, event.pointerCount)
                updatePoints(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                processMove(event)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                maxPointers = maxOf(maxPointers, event.pointerCount + 1)
                updatePoints(event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                sink?.resetMotion()
                val elapsed = SystemClock.uptimeMillis() - downTime
                val pointersAtEnd = maxPointers

                if (longPressPosted) {
                    sink?.up(TrackpadSink.Button.LEFT)
                } else if (!moved && elapsed < 280) {
                    handleTap(event, pointersAtEnd)
                } else if (pointersAtEnd >= 3) {
                    handleMultiFingerSwipe(event)
                } else if (pointersAtEnd == 2) {
                    handleTwoFingerGestureEnd(event)
                }

                maxPointers = 0
                last1 = null
                last2 = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                sink?.resetMotion()
                if (longPressPosted) sink?.up(TrackpadSink.Button.LEFT)
                maxPointers = 0
                last1 = null
                last2 = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun processMove(event: MotionEvent) {
        val history = event.historySize
        for (h in 0 until history) {
            dispatchMoveAt(event, h, historical = true)
        }
        dispatchMoveAt(event, history, historical = false)
    }

    private fun dispatchMoveAt(event: MotionEvent, index: Int, historical: Boolean) {
        when (event.pointerCount) {
            1 -> {
                val cur = Pt(
                    hx(event, 0, index, historical),
                    hy(event, 0, index, historical),
                )
                val prev = last1 ?: cur
                val dx = cur.x - prev.x
                val dy = cur.y - prev.y
                if (abs(dx) + abs(dy) > 0.35f) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                }
                sink?.move(dx, dy)
                last1 = cur
            }
            2 -> {
                val p1 = Pt(hx(event, 0, index, historical), hy(event, 0, index, historical))
                val p2 = Pt(hx(event, 1, index, historical), hy(event, 1, index, historical))
                val midX = (p1.x + p2.x) / 2f
                val midY = (p1.y + p2.y) / 2f
                val prevMidX = ((last1?.x ?: midX) + (last2?.x ?: midX)) / 2f
                val prevMidY = ((last1?.y ?: midY) + (last2?.y ?: midY)) / 2f
                val dMidY = midY - prevMidY
                val dMidX = midX - prevMidX
                if (abs(dMidX) + abs(dMidY) > 0.35f) moved = true

                if (abs(dMidY) >= abs(dMidX)) {
                    sink?.scroll(dMidY)
                } else {
                    sink?.scroll(dMidX, 0f)
                }

                val dist = hypot(p2.x - p1.x, p2.y - p1.y)
                val prevDist = if (last1 != null && last2 != null) {
                    hypot(last2!!.x - last1!!.x, last2!!.y - last1!!.y)
                } else dist
                val pinch = dist - prevDist
                val now = SystemClock.uptimeMillis()
                if (abs(pinch) > 14f && now - lastPinchAt > 300) {
                    lastPinchAt = now
                    if (pinch > 0) {
                        sink?.hotkey(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_EQUAL)
                    } else {
                        sink?.hotkey(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_MINUS)
                    }
                }

                last1 = p1
                last2 = p2
            }
            else -> updatePointsFromEvent(event, index, historical)
        }
    }

    private fun handleTap(event: MotionEvent, pointersAtEnd: Int) {
        when {
            pointersAtEnd >= 2 -> sink?.click(TrackpadSink.Button.RIGHT)
            isDoubleTap(event.x, event.y) -> {
                sink?.click(TrackpadSink.Button.LEFT)
                sink?.click(TrackpadSink.Button.LEFT)
            }
            else -> {
                sink?.click(TrackpadSink.Button.LEFT)
                lastTapTime = SystemClock.uptimeMillis()
                lastTapX = event.x
                lastTapY = event.y
            }
        }
    }

    private fun isDoubleTap(x: Float, y: Float): Boolean {
        val dt = SystemClock.uptimeMillis() - lastTapTime
        val dist = hypot(x - lastTapX, y - lastTapY)
        return dt in 1..350 && dist < 48f
    }

    private fun handleMultiFingerSwipe(event: MotionEvent) {
        val dx = event.x - gestureStartX
        val dy = event.y - gestureStartY
        if (abs(dx) < 72f && abs(dy) < 72f) return
        if (abs(dx) > abs(dy)) {
            if (dx > 0) {
                sink?.hotkey(
                    (HidKeyCodes.MOD_ALT.toInt() or HidKeyCodes.MOD_SHIFT.toInt()).toByte(),
                    HidKeyCodes.KEY_TAB,
                )
            } else {
                sink?.hotkey(HidKeyCodes.MOD_ALT, HidKeyCodes.KEY_TAB)
            }
        } else {
            if (dy < 0) sink?.keyTap(HidKeyCodes.KEY_PAGE_UP)
            else sink?.keyTap(HidKeyCodes.KEY_PAGE_DOWN)
        }
    }

    private fun handleTwoFingerGestureEnd(event: MotionEvent) {
        val dx = event.x - gestureStartX
        val dy = event.y - gestureStartY
        if (!moved || abs(dx) + abs(dy) < 90f) return
        if (abs(dy) > abs(dx)) return
        if (dx > 70f) {
            sink?.hotkey(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_RIGHT)
        } else if (dx < -70f) {
            sink?.hotkey(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_LEFT)
        }
    }

    private fun hx(event: MotionEvent, ptr: Int, index: Int, historical: Boolean): Float =
        if (historical) event.getHistoricalX(ptr, index) else event.getX(ptr)

    private fun hy(event: MotionEvent, ptr: Int, index: Int, historical: Boolean): Float =
        if (historical) event.getHistoricalY(ptr, index) else event.getY(ptr)

    private fun updatePoints(event: MotionEvent) {
        updatePointsFromEvent(event, event.historySize, false)
    }

    private fun updatePointsFromEvent(event: MotionEvent, index: Int, historical: Boolean) {
        last1 = if (event.pointerCount >= 1) {
            Pt(hx(event, 0, index, historical), hy(event, 0, index, historical))
        } else null
        last2 = if (event.pointerCount >= 2) {
            Pt(hx(event, 1, index, historical), hy(event, 1, index, historical))
        } else null
    }
}
