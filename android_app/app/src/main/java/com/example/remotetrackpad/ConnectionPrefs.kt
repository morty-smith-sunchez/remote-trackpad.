package com.example.remotetrackpad

import android.content.Context

object ConnectionPrefs {
    private const val PREFS = "remote_trackpad"
    private const val KEY_HOST = "last_host"
    private const val KEY_PORT = "last_port"
    private const val KEY_BT_ADDRESS = "last_bt_address"
    private const val KEY_BT_ADDRESS_PC = "last_bt_address_pc"
    private const val KEY_BT_ADDRESS_TV = "last_bt_address_tv"
    private const val KEY_BT_AUTO_CONNECT = "bt_auto_connect"
    private const val KEY_BT_SENSITIVITY = "bt_sensitivity"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        migrateBtAddresses()
    }

    private fun migrateBtAddresses() {
        val ctx = appContext ?: return
        val p = prefs(ctx)
        if (p.contains(KEY_BT_ADDRESS_PC) || p.contains(KEY_BT_ADDRESS_TV)) return
        val legacy = p.getString(KEY_BT_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return
        p.edit()
            .putString(KEY_BT_ADDRESS_PC, legacy)
            .apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(host: String, port: Int) {
        val ctx = appContext ?: return
        prefs(ctx).edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
    }

    fun peekHost(): String? {
        val ctx = appContext ?: return null
        return prefs(ctx).getString(KEY_HOST, null)?.takeIf { it.isNotBlank() }
    }

    fun peekPort(default: Int = 8765): Int {
        val ctx = appContext ?: return default
        return prefs(ctx).getInt(KEY_PORT, default)
    }

    fun saveBtDevice(address: String) {
        saveBtDeviceForPc(address)
    }

    fun peekBtAddress(): String? = peekBtAddressForPc()

    fun saveBtDeviceForPc(address: String) {
        val ctx = appContext ?: return
        prefs(ctx).edit().putString(KEY_BT_ADDRESS_PC, address).apply()
    }

    fun saveBtDeviceForTv(address: String) {
        val ctx = appContext ?: return
        prefs(ctx).edit().putString(KEY_BT_ADDRESS_TV, address).apply()
    }

    fun peekBtAddressForPc(): String? {
        val ctx = appContext ?: return null
        return prefs(ctx).getString(KEY_BT_ADDRESS_PC, null)?.takeIf { it.isNotBlank() }
    }

    fun peekBtAddressForTv(): String? {
        val ctx = appContext ?: return null
        return prefs(ctx).getString(KEY_BT_ADDRESS_TV, null)?.takeIf { it.isNotBlank() }
    }

    fun isBtAutoConnect(): Boolean {
        val ctx = appContext ?: return true
        return prefs(ctx).getBoolean(KEY_BT_AUTO_CONNECT, true)
    }

    fun setBtAutoConnect(enabled: Boolean) {
        val ctx = appContext ?: return
        prefs(ctx).edit().putBoolean(KEY_BT_AUTO_CONNECT, enabled).apply()
    }

    fun peekBtSensitivity(): Float {
        val ctx = appContext ?: return 1.15f
        return prefs(ctx).getFloat(KEY_BT_SENSITIVITY, 1.15f).coerceIn(0.5f, 3f)
    }

    fun setBtSensitivity(value: Float) {
        val ctx = appContext ?: return
        prefs(ctx).edit().putFloat(KEY_BT_SENSITIVITY, value.coerceIn(0.5f, 3f)).apply()
    }
}
