package com.example.remotetrackpad

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class KeyboardPanel(private val root: View) {
    private val textInput: TextInputEditText = root.findViewById(R.id.kbTextInput)

    fun bind(sinkProvider: () -> TrackpadSink?) {
        fun tap(key: Byte, mod: Byte = 0) {
            sinkProvider()?.keyTap(key, mod)
        }
        fun hot(mod: Byte, key: Byte) {
            sinkProvider()?.hotkey(mod, key)
        }

        root.findViewById<MaterialButton>(R.id.kbEsc).setOnClickListener { tap(HidKeyCodes.KEY_ESC) }
        root.findViewById<MaterialButton>(R.id.kbTab).setOnClickListener { tap(HidKeyCodes.KEY_TAB) }
        root.findViewById<MaterialButton>(R.id.kbWin).setOnClickListener {
            hot(HidKeyCodes.MOD_WIN, HidKeyCodes.KEY_D)
        }
        root.findViewById<MaterialButton>(R.id.kbCopy).setOnClickListener {
            hot(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_C)
        }
        root.findViewById<MaterialButton>(R.id.kbPaste).setOnClickListener {
            hot(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_V)
        }
        root.findViewById<MaterialButton>(R.id.kbUndo).setOnClickListener {
            hot(HidKeyCodes.MOD_CTRL, HidKeyCodes.KEY_Z)
        }
        root.findViewById<MaterialButton>(R.id.kbUp).setOnClickListener { tap(HidKeyCodes.KEY_UP) }
        root.findViewById<MaterialButton>(R.id.kbDown).setOnClickListener { tap(HidKeyCodes.KEY_DOWN) }
        root.findViewById<MaterialButton>(R.id.kbLeft).setOnClickListener { tap(HidKeyCodes.KEY_LEFT) }
        root.findViewById<MaterialButton>(R.id.kbRight).setOnClickListener { tap(HidKeyCodes.KEY_RIGHT) }
        root.findViewById<MaterialButton>(R.id.kbBackspace).setOnClickListener { tap(HidKeyCodes.KEY_BACKSPACE) }
        root.findViewById<MaterialButton>(R.id.kbEnter).setOnClickListener { tap(HidKeyCodes.KEY_ENTER) }
        root.findViewById<MaterialButton>(R.id.kbSpace).setOnClickListener { tap(HidKeyCodes.KEY_SPACE) }

        textInput.setOnEditorActionListener { v, actionId, event ->
            val send = actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (send) {
                val text = v.text?.toString().orEmpty()
                if (text.isNotEmpty()) {
                    sinkProvider()?.typeText(text)
                    v.text = ""
                } else {
                    tap(HidKeyCodes.KEY_ENTER)
                }
                true
            } else {
                false
            }
        }
    }
}
