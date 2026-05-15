package com.example.remotetrackpad

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Phone acts as a Bluetooth HID mouse (Android 9+ / API 28+).
 * Optimized for pairing with Windows PCs.
 */
class HidMouseManager(
    private val ctx: Context,
    private val onState: (HidState) -> Unit,
) : TrackpadSink {

    data class HidState(
        val phase: Phase,
        val message: String,
        val deviceLabel: String? = null,
    ) {
        val isConnected: Boolean get() = phase == Phase.CONNECTED
        val isReady: Boolean get() = phase == Phase.READY || phase == Phase.CONNECTED
    }

    enum class Phase {
        OFF, INIT, REGISTERING, READY, CONNECTING, CONNECTED, FAILED
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private var pendingHost: BluetoothDevice? = null
    private var phase = Phase.OFF
    private var connectAttempts = 0
    private var autoConnectEnabled = true

    /** Mouse (report 1) + keyboard (report 2) combo descriptor. */
    private val reportDescriptor = byteArrayOf(
        // Mouse
        0x05, 0x01,
        0x09, 0x02,
        0xA1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        0x09, 0x01,
        0xA1.toByte(), 0x00,
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x05,
        0x15, 0x00,
        0x25, 0x01,
        0x95.toByte(), 0x05,
        0x75, 0x01,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x01,
        0x75, 0x03,
        0x81.toByte(), 0x01,
        0x05, 0x01,
        0x09, 0x30,
        0x09, 0x31,
        0x09, 0x38,
        0x15, 0x81.toByte(),
        0x25, 0x7F,
        0x75, 0x08,
        0x95.toByte(), 0x03,
        0x81.toByte(), 0x06,
        0xC0.toByte(),
        0xC0.toByte(),
        // Keyboard
        0x05, 0x01,
        0x09, 0x06,
        0xA1.toByte(), 0x01,
        0x85.toByte(), 0x02,
        0x05, 0x07,
        0x19, 0xE0.toByte(),
        0x29, 0xE7.toByte(),
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95.toByte(), 0x08,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x01,
        0x75, 0x08,
        0x81.toByte(), 0x01,
        0x95.toByte(), 0x06,
        0x75, 0x08,
        0x15, 0x00,
        0x25, 0x65,
        0x05, 0x07,
        0x19, 0x00,
        0x29, 0x65,
        0x81.toByte(), 0x00,
        0xC0.toByte(),
    )

    private val connectTimeout = Runnable {
        if (phase == Phase.CONNECTING) {
            failConnect("Таймаут. На ПК: Параметры → Bluetooth → удалите телефон и сопрягите снова.")
        }
    }

    private val linkWatchdog = object : Runnable {
        override fun run() {
            val h = hid ?: return
            val d = host ?: return
            if (phase != Phase.CONNECTED) return
            if (!isDeviceConnected(h, d)) {
                failConnect("Связь потеряна")
                if (autoConnectEnabled) {
                    mainHandler.postDelayed({ connect(d) }, 1200)
                }
                return
            }
            mainHandler.postDelayed(this, 2500)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registeredNow: Boolean) {
            registered = registeredNow
            if (registeredNow) {
                phase = Phase.READY
                emit(Phase.READY, "Готово — нажмите «Подключить»")
                val target = pendingHost ?: resolveSavedDevice()
                if (target != null) {
                    pendingHost = null
                    if (autoConnectEnabled) connectTo(target)
                }
            } else if (phase != Phase.OFF) {
                phase = Phase.FAILED
                emit(Phase.FAILED, "HID сброшен системой")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (device == null) return
            val label = device.label()
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    phase = Phase.CONNECTED
                    connectAttempts = 0
                    ConnectionPrefs.saveBtDevice(device.address)
                    mainHandler.removeCallbacks(connectTimeout)
                    mainHandler.removeCallbacks(linkWatchdog)
                    mainHandler.post(linkWatchdog)
                    emit(Phase.CONNECTED, "Мышь активна — двигайте трекпад", label)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    host = device
                    phase = Phase.CONNECTING
                    emit(Phase.CONNECTING, "Подключение к ПК…", label)
                }
                BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING -> {
                    if (phase == Phase.CONNECTING || phase == Phase.CONNECTED) {
                        if (host == null || host?.address == device.address) {
                            failConnect(
                                "ПК отклонил HID ($label). " +
                                    "В Windows: удалите сопряжение → сопрягите снова → «Подключить»."
                            )
                        }
                    }
                }
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        autoConnectEnabled = enabled
        ConnectionPrefs.setBtAutoConnect(enabled)
    }

    fun start() {
        autoConnectEnabled = ConnectionPrefs.isBtAutoConnect()
        if (Build.VERSION.SDK_INT < 28) {
            phase = Phase.FAILED
            emit(Phase.FAILED, "Нужен Android 9+ для Bluetooth-мыши")
            return
        }
        val a = adapter
        if (a == null) {
            phase = Phase.FAILED
            emit(Phase.FAILED, "Нет Bluetooth на устройстве")
            return
        }
        if (!a.isEnabled) {
            phase = Phase.FAILED
            emit(Phase.FAILED, "Включите Bluetooth")
            return
        }
        if (hid != null && registered) {
            phase = Phase.READY
            emit(Phase.READY, "Готово — нажмите «Подключить»")
            return
        }
        phase = Phase.INIT
        emit(Phase.INIT, "Запуск Bluetooth-мыши…")
        a.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                registerApp()
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                registered = false
                pendingHost = null
                phase = Phase.OFF
                emit(Phase.OFF, "Bluetooth-сервис остановлен")
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    /** Disconnect from PC but keep HID registered for fast reconnect. */
    fun disconnect() {
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.removeCallbacks(linkWatchdog)
        connectAttempts = 0
        try {
            host?.let { hid?.disconnect(it) }
        } catch (_: Exception) {}
        host = null
        if (registered) {
            phase = Phase.READY
            emit(Phase.READY, "Отключено от ПК — можно подключить снова")
        } else {
            phase = Phase.OFF
            emit(Phase.OFF, "Отключено")
        }
    }

    /** Full teardown (mode switch / app exit). */
    fun stop() {
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.removeCallbacks(linkWatchdog)
        pendingHost = null
        connectAttempts = 0
        try {
            host?.let { hid?.disconnect(it) }
        } catch (_: Exception) {}
        try {
            if (registered) hid?.unregisterApp()
        } catch (_: Exception) {}
        registered = false
        host = null
        phase = Phase.OFF
        emit(Phase.OFF, "Выключено")
    }

    fun isConnected(): Boolean = phase == Phase.CONNECTED

    fun bondedDevices(): List<BluetoothDevice> {
        return try {
            val list = adapter?.bondedDevices?.toList().orEmpty()
            list.sortedWith(
                compareByDescending<BluetoothDevice> { isLikelyComputer(it) }
                    .thenBy { deviceSortKey(it) }
            )
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun deviceSortKey(device: BluetoothDevice): String {
        return try {
            device.name?.lowercase() ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
    }

    fun guessPcDevice(): BluetoothDevice? {
        resolveSavedDevice()?.let { return it }
        return bondedDevices().firstOrNull { isLikelyComputer(it) }
            ?: bondedDevices().firstOrNull()
    }

    fun connect(device: BluetoothDevice): Boolean {
        try {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                emit(Phase.FAILED, "Сначала сопрягите «${device.label()}» в настройках Bluetooth")
                return false
            }
        } catch (_: SecurityException) {
            emit(Phase.FAILED, "Разрешите Bluetooth для приложения")
            return false
        }
        host = device
        connectAttempts = 0
        try {
            adapter?.cancelDiscovery()
        } catch (_: SecurityException) {}
        if (!registered || hid == null) {
            pendingHost = device
            emit(Phase.INIT, "Подготовка → ${device.label()}…", device.label())
            start()
            return true
        }
        return connectTo(device)
    }

    fun connectBestBondedHost(): Boolean {
        val pc = guessPcDevice()
        if (pc == null) {
            emit(Phase.FAILED, "Нет сопряжённых устройств — сопрягите ноутбук в настройках")
            return false
        }
        return connect(pc)
    }

    private fun resolveSavedDevice(): BluetoothDevice? {
        val addr = ConnectionPrefs.peekBtAddress() ?: return null
        return bondedDevices().firstOrNull { it.address == addr }
    }

    private fun connectTo(device: BluetoothDevice): Boolean {
        val h = hid
        if (h == null) {
            pendingHost = device
            start()
            return false
        }
        if (!registered) {
            pendingHost = device
            emit(Phase.REGISTERING, "Регистрация HID…", device.label())
            return false
        }

        if (isDeviceConnected(h, device)) {
            host = device
            phase = Phase.CONNECTED
            emit(Phase.CONNECTED, "Уже подключено", device.label())
            return true
        }

        phase = Phase.CONNECTING
        emit(Phase.CONNECTING, "Запрос к ${device.label()}…", device.label())
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.postDelayed(connectTimeout, 20_000)

        return try {
            val ok = h.connect(device)
            if (!ok) retryOrFail(device, "Система отклонила connect()")
            ok
        } catch (e: SecurityException) {
            failConnect("Разрешите Bluetooth для приложения в настройках")
            false
        } catch (e: Exception) {
            failConnect("Ошибка: ${e.message}")
            false
        }
    }

    private fun retryOrFail(device: BluetoothDevice, reason: String) {
        connectAttempts++
        when {
            connectAttempts < 3 -> {
                emit(Phase.CONNECTING, "Повтор ${connectAttempts}/3…", device.label())
                mainHandler.postDelayed({ connectTo(device) }, 2000L)
            }
            connectAttempts == 3 -> softResetAndRetry(device)
            else -> failConnect("$reason. Попробуйте пересопрячь устройство.")
        }
    }

    private fun softResetAndRetry(device: BluetoothDevice) {
        emit(Phase.REGISTERING, "Перерегистрация HID…", device.label())
        pendingHost = device
        try {
            if (registered) hid?.unregisterApp()
        } catch (_: Exception) {}
        registered = false
        mainHandler.postDelayed({
            registerApp()
        }, 600)
    }

    private fun failConnect(message: String) {
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.removeCallbacks(linkWatchdog)
        phase = Phase.FAILED
        emit(Phase.FAILED, message, host?.label())
        mainHandler.postDelayed({
            if (registered) {
                phase = Phase.READY
                emit(Phase.READY, "Готово — попробуйте снова")
            }
        }, 5000)
    }

    private fun registerApp() {
        val h = hid ?: run {
            phase = Phase.FAILED
            emit(Phase.FAILED, "HID недоступен")
            return
        }
        if (registered) {
            phase = Phase.READY
            emit(Phase.READY, "Готово")
            return
        }
        phase = Phase.REGISTERING
        emit(Phase.REGISTERING, "Регистрация как мышь…")

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Remote Trackpad",
            "Bluetooth Trackpad",
            "RemoteTrackpad",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            reportDescriptor,
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, 11250,
        )
        val ok = h.registerApp(sdp, qos, qos, executor, callback)
        if (!ok) {
            phase = Phase.FAILED
            emit(Phase.FAILED, "Телефон не поддерживает режим BT-мыши (HID Device)")
        } else {
            mainHandler.postDelayed({
                if (phase == Phase.REGISTERING && !registered) {
                    val dev = pendingHost ?: guessPcDevice()
                    if (dev != null) softResetAndRetry(dev)
                    else failConnect("Регистрация HID не завершилась")
                }
            }, 10_000)
        }
    }

    private fun isDeviceConnected(h: BluetoothHidDevice, device: BluetoothDevice): Boolean {
        return try {
            h.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
    }

    private fun isLikelyComputer(device: BluetoothDevice): Boolean {
        return try {
            if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER) {
                return true
            }
            val n = device.name?.lowercase().orEmpty()
            n.contains("pc") || n.contains("laptop") || n.contains("notebook") ||
                n.contains("windows") || n.contains("desktop") || n.contains("ноут") ||
                n.contains("комп") || n.contains("lenovo") || n.contains("asus") ||
                n.contains("dell") || n.contains("hp") || n.contains("acer") ||
                n.contains("surface") || n.contains("thinkpad")
        } catch (_: SecurityException) {
            false
        }
    }

    private fun BluetoothDevice.label(): String {
        return try {
            name ?: address
        } catch (_: SecurityException) {
            address
        }
    }

    private fun sendReport(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        val h = hid ?: return
        val d = host ?: return
        if (!registered || phase != Phase.CONNECTED) return
        if (!isDeviceConnected(h, d)) return
        val b = buttons and 0x1F
        val report = byteArrayOf(
            b.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte(),
        )
        try {
            h.sendReport(d, 1, report)
        } catch (_: Exception) {}
    }

    private var buttonsState = 0
    private var moveRemainderX = 0f
    private var moveRemainderY = 0f
    private var scrollRemainder = 0f

    override fun move(dx: Float, dy: Float) {
        val sens = ConnectionPrefs.peekBtSensitivity()
        moveRemainderX += dx * sens
        moveRemainderY += dy * sens
        val x = moveRemainderX.toInt()
        val y = moveRemainderY.toInt()
        if (x == 0 && y == 0) return
        moveRemainderX -= x
        moveRemainderY -= y
        sendReport(buttonsState, x, y, 0)
    }

    override fun scroll(dy: Float) {
        scrollRemainder += -dy * 0.35f
        val w = scrollRemainder.toInt()
        if (w == 0) return
        scrollRemainder -= w
        sendReport(buttonsState, 0, 0, w)
    }

    override fun resetMotion() {
        moveRemainderX = 0f
        moveRemainderY = 0f
        scrollRemainder = 0f
    }

    override fun click(button: TrackpadSink.Button) {
        down(button)
        mainHandler.postDelayed({ up(button) }, 35)
    }

    override fun down(button: TrackpadSink.Button) {
        buttonsState = buttonsState or button.mask()
        sendReport(buttonsState, 0, 0, 0)
    }

    override fun up(button: TrackpadSink.Button) {
        buttonsState = buttonsState and button.mask().inv()
        sendReport(buttonsState, 0, 0, 0)
    }

    override fun keyTap(key: Byte, modifiers: Byte) {
        sendKeyboardReport(modifiers, key)
        mainHandler.postDelayed({ sendKeyboardReport(0, 0) }, 28)
    }

    override fun hotkey(modifiers: Byte, key: Byte) {
        keyTap(key, modifiers)
    }

    override fun typeText(text: String) {
        executor.execute {
            for (ch in text) {
                val code = HidKeyCodes.letter(ch) ?: continue
                val mod: Byte = if (ch.isUpperCase()) HidKeyCodes.MOD_SHIFT else 0
                sendKeyboardReport(mod, code)
                Thread.sleep(12)
                sendKeyboardReport(0, 0)
            }
        }
    }

    private fun sendKeyboardReport(modifiers: Byte, key: Byte) {
        val h = hid ?: return
        val d = host ?: return
        if (!registered || phase != Phase.CONNECTED) return
        if (!isDeviceConnected(h, d)) return
        val report = byteArrayOf(modifiers, 0, key, 0, 0, 0, 0, 0)
        try {
            h.sendReport(d, 2, report)
        } catch (_: Exception) {}
    }

    private fun TrackpadSink.Button.mask(): Int = when (this) {
        TrackpadSink.Button.LEFT -> 0x01
        TrackpadSink.Button.RIGHT -> 0x02
        TrackpadSink.Button.MIDDLE -> 0x04
    }

    private fun emit(phase: Phase, message: String, deviceLabel: String? = null) {
        this.phase = phase
        mainHandler.post { onState(HidState(phase, message, deviceLabel)) }
    }
}
