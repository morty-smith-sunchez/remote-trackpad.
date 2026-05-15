package com.example.remotetrackpad

/** USB HID key codes (Usage Page 0x07). */
object HidKeyCodes {
    const val MOD_CTRL: Byte = 0x01
    const val MOD_SHIFT: Byte = 0x02
    const val MOD_ALT: Byte = 0x04
    const val MOD_WIN: Byte = 0x08

    const val KEY_A: Byte = 0x04
    const val KEY_B: Byte = 0x05
    const val KEY_C: Byte = 0x06
    const val KEY_D: Byte = 0x07
    const val KEY_E: Byte = 0x08
    const val KEY_F: Byte = 0x09
    const val KEY_G: Byte = 0x0A
    const val KEY_H: Byte = 0x0B
    const val KEY_I: Byte = 0x0C
    const val KEY_J: Byte = 0x0D
    const val KEY_K: Byte = 0x0E
    const val KEY_L: Byte = 0x0F
    const val KEY_M: Byte = 0x10
    const val KEY_N: Byte = 0x11
    const val KEY_O: Byte = 0x12
    const val KEY_P: Byte = 0x13
    const val KEY_Q: Byte = 0x14
    const val KEY_R: Byte = 0x15
    const val KEY_S: Byte = 0x16
    const val KEY_T: Byte = 0x17
    const val KEY_U: Byte = 0x18
    const val KEY_V: Byte = 0x19
    const val KEY_W: Byte = 0x1A
    const val KEY_X: Byte = 0x1B
    const val KEY_Y: Byte = 0x1C
    const val KEY_Z: Byte = 0x1D

    const val KEY_1: Byte = 0x1E
    const val KEY_2: Byte = 0x1F
    const val KEY_3: Byte = 0x20
    const val KEY_4: Byte = 0x21
    const val KEY_5: Byte = 0x22
    const val KEY_6: Byte = 0x23
    const val KEY_7: Byte = 0x24
    const val KEY_8: Byte = 0x25
    const val KEY_9: Byte = 0x26
    const val KEY_0: Byte = 0x27

    const val KEY_ENTER: Byte = 0x28
    const val KEY_ESC: Byte = 0x29
    const val KEY_BACKSPACE: Byte = 0x2A
    const val KEY_TAB: Byte = 0x2B
    const val KEY_SPACE: Byte = 0x2C

    const val KEY_MINUS: Byte = 0x2D
    const val KEY_EQUAL: Byte = 0x2E
    const val KEY_LEFT_BRACKET: Byte = 0x2F
    const val KEY_RIGHT_BRACKET: Byte = 0x30
    const val KEY_BACKSLASH: Byte = 0x31
    const val KEY_SEMICOLON: Byte = 0x33
    const val KEY_APOSTROPHE: Byte = 0x34
    const val KEY_GRAVE: Byte = 0x35
    const val KEY_COMMA: Byte = 0x36
    const val KEY_DOT: Byte = 0x37
    const val KEY_SLASH: Byte = 0x38

    const val KEY_F1: Byte = 0x3A
    const val KEY_F2: Byte = 0x3B
    const val KEY_F3: Byte = 0x3C
    const val KEY_F4: Byte = 0x3D
    const val KEY_F5: Byte = 0x3E
    const val KEY_F6: Byte = 0x3F
    const val KEY_F7: Byte = 0x40
    const val KEY_F8: Byte = 0x41
    const val KEY_F9: Byte = 0x42
    const val KEY_F10: Byte = 0x43
    const val KEY_F11: Byte = 0x44
    const val KEY_F12: Byte = 0x45

    const val KEY_INSERT: Byte = 0x49
    const val KEY_HOME: Byte = 0x4A
    const val KEY_PAGE_UP: Byte = 0x4B
    const val KEY_DELETE: Byte = 0x4C
    const val KEY_END: Byte = 0x4D
    const val KEY_PAGE_DOWN: Byte = 0x4E

    const val KEY_RIGHT: Byte = 0x4F
    const val KEY_LEFT: Byte = 0x50
    const val KEY_DOWN: Byte = 0x51
    const val KEY_UP: Byte = 0x52

    // Consumer page (many TVs map these as volume / media)
    const val KEY_VOLUME_UP: Byte = 0x80.toByte()
    const val KEY_VOLUME_DOWN: Byte = 0x81.toByte()

    fun letter(c: Char): Byte? = when (c.lowercaseChar()) {
        'a' -> KEY_A; 'b' -> KEY_B; 'c' -> KEY_C; 'd' -> KEY_D
        'e' -> KEY_E; 'f' -> KEY_F; 'g' -> KEY_G; 'h' -> KEY_H
        'i' -> KEY_I; 'j' -> KEY_J; 'k' -> KEY_K; 'l' -> KEY_L
        'm' -> KEY_M; 'n' -> KEY_N; 'o' -> KEY_O; 'p' -> KEY_P
        'q' -> KEY_Q; 'r' -> KEY_R; 's' -> KEY_S; 't' -> KEY_T
        'u' -> KEY_U; 'v' -> KEY_V; 'w' -> KEY_W; 'x' -> KEY_X
        'y' -> KEY_Y; 'z' -> KEY_Z
        '1' -> KEY_1; '2' -> KEY_2; '3' -> KEY_3; '4' -> KEY_4
        '5' -> KEY_5; '6' -> KEY_6; '7' -> KEY_7; '8' -> KEY_8
        '9' -> KEY_9; '0' -> KEY_0
        ' ' -> KEY_SPACE
        else -> null
    }
}
