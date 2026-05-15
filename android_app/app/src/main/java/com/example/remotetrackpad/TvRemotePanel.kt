package com.example.remotetrackpad

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton

/** TV D-pad + embedded keyboard over Bluetooth HID. */
class TvRemotePanel(private val root: View) {

    private val handler = Handler(Looper.getMainLooper())
    private var repeatKey: Byte? = null
    private var repeatMod: Byte = 0
    private val keyboardPanelRoot: View = root.findViewById(R.id.tvKeyboardPanel)
    private val keyboardPanel = KeyboardPanel(keyboardPanelRoot)
    private val keyboardBtn: MaterialButton = root.findViewById(R.id.tvKeyboardBtn)

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = repeatKey ?: return
            sinkProvider?.invoke()?.keyTap(k, repeatMod)
            handler.postDelayed(this, 120)
        }
    }

    private var sinkProvider: (() -> TrackpadSink?)? = null

    fun bind(sinkProvider: () -> TrackpadSink?) {
        this.sinkProvider = sinkProvider
        keyboardPanel.bind(sinkProvider)

        keyboardBtn.setOnClickListener {
            val show = !keyboardPanelRoot.isVisible
            keyboardPanelRoot.isVisible = show
            keyboardBtn.text = if (show) "Скрыть клавиатуру" else "Клавиатура"
        }

        bindKey(root.findViewById(R.id.tvUp), HidKeyCodes.KEY_UP)
        bindKey(root.findViewById(R.id.tvDown), HidKeyCodes.KEY_DOWN)
        bindKey(root.findViewById(R.id.tvLeft), HidKeyCodes.KEY_LEFT)
        bindKey(root.findViewById(R.id.tvRight), HidKeyCodes.KEY_RIGHT)
        bindKey(root.findViewById(R.id.tvOk), HidKeyCodes.KEY_ENTER)
        bindKey(root.findViewById(R.id.tvBack), HidKeyCodes.KEY_ESC)
        bindKey(root.findViewById(R.id.tvHome), HidKeyCodes.KEY_H, HidKeyCodes.MOD_WIN)
        bindKey(root.findViewById(R.id.tvVolUp), HidKeyCodes.KEY_VOLUME_UP)
        bindKey(root.findViewById(R.id.tvVolDown), HidKeyCodes.KEY_VOLUME_DOWN)
        bindKey(root.findViewById(R.id.tvPlayPause), HidKeyCodes.KEY_SPACE)
    }

    fun stopRepeat() {
        handler.removeCallbacks(repeatRunnable)
        repeatKey = null
    }

    private fun bindKey(btn: MaterialButton, key: Byte, mod: Byte = 0) {
        btn.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeatKey = key
                    repeatMod = mod
                    sinkProvider?.invoke()?.keyTap(key, mod)
                    handler.postDelayed(repeatRunnable, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeat()
                    true
                }
                else -> false
            }
        }
    }
}
