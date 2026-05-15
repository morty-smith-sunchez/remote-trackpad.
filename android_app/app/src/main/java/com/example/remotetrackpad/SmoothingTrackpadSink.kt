package com.example.remotetrackpad

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Batches touch deltas to display refresh rate, keeps sub-pixel remainders,
 * and eases output so the cursor / scroll wheel move smoothly.
 */
class SmoothingTrackpadSink : TrackpadSink {

    var delegate: TrackpadSink? = null

    private val choreographer = Choreographer.getInstance()
    private var frameScheduled = false

    private var pendingMoveX = 0f
    private var pendingMoveY = 0f
    private var pendingScrollX = 0f
    private var pendingScrollY = 0f
    private var smoothMoveX = 0f
    private var smoothMoveY = 0f

    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled = false
        flushMove()
        flushScroll()
        if (hasPending()) scheduleFrame()
    }

    override fun move(dx: Float, dy: Float) {
        smoothMoveX = smoothMoveX * (1f - INPUT_BLEND) + dx * INPUT_BLEND
        smoothMoveY = smoothMoveY * (1f - INPUT_BLEND) + dy * INPUT_BLEND
        pendingMoveX += smoothMoveX
        pendingMoveY += smoothMoveY
        scheduleFrame()
    }

    override fun scroll(dy: Float) {
        pendingScrollY += dy * SCROLL_GAIN
        scheduleFrame()
    }

    override fun scroll(dx: Float, dy: Float) {
        pendingScrollX += dx * SCROLL_GAIN
        pendingScrollY += dy * SCROLL_GAIN
        scheduleFrame()
    }

    override fun click(button: TrackpadSink.Button) {
        flushAll()
        delegate?.click(button)
    }

    override fun down(button: TrackpadSink.Button) {
        flushAll()
        delegate?.down(button)
    }

    override fun up(button: TrackpadSink.Button) {
        flushAll()
        delegate?.up(button)
    }

    override fun keyTap(key: Byte, modifiers: Byte) {
        delegate?.keyTap(key, modifiers)
    }

    override fun hotkey(modifiers: Byte, key: Byte) {
        delegate?.hotkey(modifiers, key)
    }

    override fun typeText(text: String) {
        delegate?.typeText(text)
    }

    override fun resetMotion() {
        pendingMoveX = 0f
        pendingMoveY = 0f
        pendingScrollX = 0f
        pendingScrollY = 0f
        smoothMoveX = 0f
        smoothMoveY = 0f
    }

    private fun flushAll() {
        while (hasPending()) {
            flushMove(force = true)
            flushScroll(force = true)
        }
    }

    private fun flushMove(force: Boolean = false) {
        val sink = delegate ?: return
        val factor = if (force) 1f else MOVE_EASE
        var ix = (pendingMoveX * factor).toInt().coerceIn(-MAX_MOVE_STEP, MAX_MOVE_STEP)
        var iy = (pendingMoveY * factor).toInt().coerceIn(-MAX_MOVE_STEP, MAX_MOVE_STEP)
        if (!force && ix == 0 && iy == 0) {
            if (abs(pendingMoveX) < 0.35f && abs(pendingMoveY) < 0.35f) {
                pendingMoveX = 0f
                pendingMoveY = 0f
            }
            return
        }
        if (force) {
            ix = pendingMoveX.roundToInt().coerceIn(-127, 127)
            iy = pendingMoveY.roundToInt().coerceIn(-127, 127)
        }
        if (ix == 0 && iy == 0) return
        sink.move(ix.toFloat(), iy.toFloat())
        pendingMoveX -= ix
        pendingMoveY -= iy
    }

    private fun flushScroll(force: Boolean = false) {
        val sink = delegate ?: return
        val factor = if (force) 1f else SCROLL_EASE
        var sx = (pendingScrollX * factor).roundToInt().coerceIn(-MAX_SCROLL_STEP, MAX_SCROLL_STEP)
        var sy = (pendingScrollY * factor).roundToInt().coerceIn(-MAX_SCROLL_STEP, MAX_SCROLL_STEP)
        if (!force && sx == 0 && sy == 0) {
            if (abs(pendingScrollX) < 0.4f && abs(pendingScrollY) < 0.4f) {
                pendingScrollX = 0f
                pendingScrollY = 0f
            }
            return
        }
        if (force) {
            sx = pendingScrollX.roundToInt()
            sy = pendingScrollY.roundToInt()
        }
        if (sx == 0 && sy == 0) return
        when {
            sx != 0 && sy != 0 -> sink.scroll(sx.toFloat(), sy.toFloat())
            sy != 0 -> sink.scroll(sy.toFloat())
            else -> sink.scroll(sx.toFloat(), 0f)
        }
        pendingScrollX -= sx
        pendingScrollY -= sy
    }

    private fun hasPending(): Boolean =
        abs(pendingMoveX) >= 0.2f ||
            abs(pendingMoveY) >= 0.2f ||
            abs(pendingScrollX) >= 0.2f ||
            abs(pendingScrollY) >= 0.2f

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        choreographer.postFrameCallback(frameCallback)
    }

    companion object {
        private const val INPUT_BLEND = 0.42f
        private const val MOVE_EASE = 0.72f
        private const val SCROLL_EASE = 0.55f
        private const val SCROLL_GAIN = 0.28f
        private const val MAX_MOVE_STEP = 36
        private const val MAX_SCROLL_STEP = 2
    }
}
