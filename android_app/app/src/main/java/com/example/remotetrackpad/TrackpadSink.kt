package com.example.remotetrackpad

interface TrackpadSink {
    enum class Button { LEFT, RIGHT, MIDDLE }

    fun move(dx: Float, dy: Float)
    fun scroll(dy: Float)
    fun scroll(dx: Float, dy: Float) {
        if (dy != 0f) scroll(dy)
    }

    fun click(button: Button)
    fun down(button: Button)
    fun up(button: Button)

    /** Single key tap (press + release). */
    fun keyTap(key: Byte, modifiers: Byte = 0)

    /** Modifier keys held during key press. */
    fun hotkey(modifiers: Byte, key: Byte)

    /** Type ASCII text (USB/Wi‑Fi; BT when connected as combo HID). */
    fun typeText(text: String) {}

    /** Clear buffered pointer / scroll deltas (e.g. when the finger lifts). */
    fun resetMotion() {}
}
