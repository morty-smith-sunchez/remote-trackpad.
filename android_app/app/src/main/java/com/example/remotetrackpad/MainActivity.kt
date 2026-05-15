package com.example.remotetrackpad

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var modeHint: TextView
    private lateinit var btToolsRow: View
    private lateinit var selectPcBtn: MaterialButton
    private lateinit var btHelpBtn: MaterialButton
    private lateinit var hostRow: View
    private lateinit var hostInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var findPcBtn: MaterialButton
    private lateinit var pcServerHelpBtn: MaterialButton
    private lateinit var connectBtn: MaterialButton
    private lateinit var holdBtn: MaterialButton
    private lateinit var trackpad: TrackpadView
    private lateinit var leftBtn: MaterialButton
    private lateinit var rightBtn: MaterialButton
    private lateinit var keyboardBtn: MaterialButton
    private lateinit var keyboardPanelRoot: View
    private lateinit var keyboardPanel: KeyboardPanel
    private lateinit var tvRemoteScroll: View
    private lateinit var tvRemotePanelRoot: View
    private lateinit var tvRemotePanel: TvRemotePanel
    private lateinit var mouseButtonsRow: View

    private lateinit var hid: HidMouseManager
    private val wsClient = WsTrackpadClient { state ->
        runOnUiThread { onWsState(state) }
    }
    private val wsSink = WsTrackpadSink(wsClient)
    private val smoothSink = SmoothingTrackpadSink()

    private var useUsbMode = false
    private var useTvMode = false
    private var connected = false
    private var discovering = false
    private var selectedBtDevice: BluetoothDevice? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (!useUsbMode && hasBtPermission(result)) {
            refreshBtSelection()
            hid.start()
        } else if (!useUsbMode) {
            statusText.text = "Нужно разрешение Bluetooth"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionPrefs.init(this)
        AppContext.init(this)
        setContentView(R.layout.activity_main)

        modeToggle = findViewById(R.id.modeToggle)
        modeHint = findViewById(R.id.modeHint)
        btToolsRow = findViewById(R.id.btToolsRow)
        selectPcBtn = findViewById(R.id.selectPcBtn)
        btHelpBtn = findViewById(R.id.btHelpBtn)
        hostRow = findViewById(R.id.hostRow)
        hostInput = findViewById(R.id.hostInput)
        portInput = findViewById(R.id.portInput)
        statusText = findViewById(R.id.statusText)
        findPcBtn = findViewById(R.id.findPcBtn)
        pcServerHelpBtn = findViewById(R.id.pcServerHelpBtn)
        connectBtn = findViewById(R.id.connectBtn)
        holdBtn = findViewById(R.id.holdBtn)
        trackpad = findViewById(R.id.trackpad)
        leftBtn = findViewById(R.id.leftClickBtn)
        rightBtn = findViewById(R.id.rightClickBtn)
        keyboardBtn = findViewById(R.id.keyboardBtn)
        keyboardPanelRoot = findViewById(R.id.keyboardPanel)
        keyboardPanel = KeyboardPanel(keyboardPanelRoot)
        trackpad.sink = smoothSink
        keyboardPanel.bind { smoothSink }
        tvRemoteScroll = findViewById(R.id.tvRemoteScroll)
        tvRemotePanelRoot = findViewById(R.id.tvRemotePanel)
        tvRemotePanel = TvRemotePanel(tvRemotePanelRoot)
        tvRemotePanel.bind { smoothSink }
        mouseButtonsRow = findViewById(R.id.mouseButtonsRow)

        ConnectionPrefs.peekHost()?.let { hostInput.setText(it) }
        portInput.setText(ConnectionPrefs.peekPort().toString())

        hid = HidMouseManager(this) { state ->
            runOnUiThread { onHidState(state) }
        }

        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            disconnectTransport()
            useTvMode = checkedId == R.id.modeAndroidTv
            useUsbMode = checkedId == R.id.modeUsbPc
            applyModeUi()
        }

        useTvMode = modeToggle.checkedButtonId == R.id.modeAndroidTv
        useUsbMode = modeToggle.checkedButtonId == R.id.modeUsbPc
        applyModeUi()

        selectPcBtn.setOnClickListener { showBluetoothDevicePicker(hid.bondedDevices(), connectAfterPick = false) }
        btHelpBtn.setOnClickListener { showBluetoothHelp() }
        findPcBtn.setOnClickListener { findPcOnly() }
        pcServerHelpBtn.setOnClickListener { showPcServerLinksDialog() }

        connectBtn.setOnClickListener {
            if (connected) {
                if (useUsbMode) wsClient.disconnect() else hid.disconnect()
            } else {
                if (useUsbMode) findAndConnectUsb() else connectBluetooth()
            }
        }

        holdBtn.setOnClickListener {
            trackpad.holdEnabled = !trackpad.holdEnabled
            holdBtn.text = if (trackpad.holdEnabled) "Hold: ON" else "Hold: OFF"
        }

        leftBtn.setOnClickListener { trackpad.sink?.click(TrackpadSink.Button.LEFT) }
        rightBtn.setOnClickListener { trackpad.sink?.click(TrackpadSink.Button.RIGHT) }

        keyboardBtn.setOnClickListener {
            val show = !keyboardPanelRoot.isVisible
            keyboardPanelRoot.isVisible = show
            keyboardBtn.text = if (show) "Скрыть клавиатуру" else "Клавиатура"
        }
    }

    override fun onResume() {
        super.onResume()
        if (!useUsbMode) ensureBtPermissionsAndStart()
    }

    override fun onPause() {
        tvRemotePanel.stopRepeat()
        super.onPause()
    }

    private fun onHidState(state: HidMouseManager.HidState) {
        if (useUsbMode) return
        val extra = state.deviceLabel?.let { " ($it)" }.orEmpty()
        statusText.text = state.message + if (state.phase == HidMouseManager.Phase.CONNECTED) extra else ""
        connected = state.isConnected
        connectBtn.text = when {
            connected -> "Отключить"
            state.phase == HidMouseManager.Phase.CONNECTING -> "Подключение…"
            state.phase == HidMouseManager.Phase.REGISTERING ||
                state.phase == HidMouseManager.Phase.INIT -> "Подождите…"
            else -> "Подключить"
        }
        connectBtn.isEnabled = state.phase != HidMouseManager.Phase.INIT &&
            state.phase != HidMouseManager.Phase.REGISTERING &&
            state.phase != HidMouseManager.Phase.CONNECTING
    }

    private fun wsPort(): Int =
        portInput.text?.toString()?.trim()?.toIntOrNull() ?: ConnectionPrefs.peekPort()

    private fun onWsState(state: WsTrackpadClient.State) {
        if (!useUsbMode) return
        when (state) {
            WsTrackpadClient.State.DISCONNECTED -> {
                discovering = false
                statusText.text = "Не подключено — запустите сервер на ПК"
                connected = false
                connectBtn.text = "Подключить"
                connectBtn.isEnabled = true
                findPcBtn.isEnabled = true
            }
            WsTrackpadClient.State.CONNECTING -> {
                statusText.text = "Подключение…"
                connected = false
                connectBtn.text = "Подключение…"
                connectBtn.isEnabled = false
            }
            WsTrackpadClient.State.CONNECTED -> {
                discovering = false
                statusText.text = "Подключено к ПК"
                connected = true
                connectBtn.text = "Отключить"
                connectBtn.isEnabled = true
                findPcBtn.isEnabled = true
                val host = hostInput.text?.toString()?.trim().orEmpty()
                if (host.isNotEmpty()) ConnectionPrefs.save(host, wsPort())
            }
        }
    }

    private fun applyModeUi() {
        when {
            useUsbMode -> {
                tvRemoteScroll.isVisible = false
                holdBtn.isVisible = true
                mouseButtonsRow.isVisible = true
                btToolsRow.isVisible = false
                hostRow.isVisible = true
                findPcBtn.isVisible = true
                pcServerHelpBtn.isVisible = true
                keyboardBtn.isVisible = true
                modeHint.text = "На ПК запустите сервер (.bat), затем «Подключить»."
                smoothSink.delegate = wsSink
                hid.stop()
            }
            useTvMode -> {
                hostRow.isVisible = false
                findPcBtn.isVisible = false
                pcServerHelpBtn.isVisible = false
                keyboardBtn.isVisible = false
                keyboardPanelRoot.isVisible = false
                tvRemoteScroll.isVisible = true
                holdBtn.isVisible = false
                mouseButtonsRow.isVisible = false
                btToolsRow.isVisible = true
                selectPcBtn.text = "Выбрать ТВ"
                if (hasBtPermission()) {
                    refreshBtSelection()
                } else {
                    selectedBtDevice = null
                }
                modeHint.text =
                    "ТВ: Настройки → Bluetooth → сопрягите телефон. «Готово» → Подключить. Сервер на ТВ не нужен."
                smoothSink.delegate = hid
                wsClient.disconnect()
                ensureBtPermissionsAndStart()
            }
            else -> {
                tvRemoteScroll.isVisible = false
                holdBtn.isVisible = true
                mouseButtonsRow.isVisible = true
                btToolsRow.isVisible = true
                hostRow.isVisible = false
                findPcBtn.isVisible = false
                pcServerHelpBtn.isVisible = false
                keyboardBtn.isVisible = true
                selectPcBtn.text = "Выбрать ПК"
                if (hasBtPermission()) {
                    refreshBtSelection()
                } else {
                    selectedBtDevice = null
                }
                modeHint.text = "Сопрягите ноутбук в Bluetooth → дождитесь «Готово» → Подключить."
                smoothSink.delegate = hid
                wsClient.disconnect()
                ensureBtPermissionsAndStart()
            }
        }
        trackpad.holdEnabled = false
        holdBtn.text = "Hold: OFF"
        connectBtn.text = if (connected) "Отключить" else "Подключить"
        applyTvTrackpadLayout()
    }

    private fun applyTvTrackpadLayout() {
        val lp = trackpad.layoutParams as LinearLayout.LayoutParams
        val dm = resources.displayMetrics
        lp.topMargin = ((if (useTvMode) 4 else 6) * dm.density).toInt()
        trackpad.minimumHeight = if (useTvMode) {
            (dm.heightPixels * 0.45f).toInt()
        } else {
            (200 * dm.density).toInt()
        }
        trackpad.layoutParams = lp
    }

    private fun updateSelectedPcLabel() {
        val d = selectedBtDevice
        selectPcBtn.text = when {
            d == null -> "Выбрать ПК"
            else -> deviceLabel(d)
        }
    }

    private fun deviceLabel(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address
        } catch (_: SecurityException) {
            device.address
        }
    }

    private fun refreshBtSelection() {
        if (!::hid.isInitialized) return
        selectedBtDevice = hid.guessPcDevice()
        updateSelectedPcLabel()
    }

    private fun hasBtPermission(
        result: Map<String, Boolean>? = null,
    ): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val map = result ?: mapOf(
            Manifest.permission.BLUETOOTH_CONNECT to
                (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED),
        )
        return map[Manifest.permission.BLUETOOTH_CONNECT] == true
    }

    private fun disconnectTransport() {
        wsClient.disconnect()
        hid.stop()
        connected = false
        discovering = false
        connectBtn.text = "Подключить"
        connectBtn.isEnabled = true
        findPcBtn.isEnabled = true
        statusText.text = "Отключено"
    }

    private fun connectBluetooth() {
        ensureBtPermissionsAndStart()
        val device = selectedBtDevice ?: hid.guessPcDevice()
        if (device == null) {
            val bonded = hid.bondedDevices()
            if (bonded.isEmpty()) {
                statusText.text = "Нет сопряжённых устройств"
                showBluetoothHelp()
            } else {
                showBluetoothDevicePicker(bonded, connectAfterPick = true)
            }
            return
        }
        hid.connect(device)
    }

    private fun showBluetoothDevicePicker(
        devices: List<BluetoothDevice>,
        connectAfterPick: Boolean,
    ) {
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Нет устройств")
                .setMessage("Сопрягите ноутбук в настройках Bluetooth телефона.")
                .setPositiveButton("Помощь") { _, _ -> showBluetoothHelp() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val labels = devices.map { d ->
            val tag = if (hid.guessPcDevice()?.address == d.address) " ★" else ""
            val kind = try {
                if (d.bluetoothClass?.majorDeviceClass ==
                    android.bluetooth.BluetoothClass.Device.Major.COMPUTER
                ) " [ПК]" else ""
            } catch (_: SecurityException) {
                ""
            }
            "${deviceLabel(d).ifBlank { "Без имени" }}$kind$tag\n${d.address}"
        }.toTypedArray()
        val guessed = selectedBtDevice ?: hid.guessPcDevice()
        var selected = devices.indexOfFirst { it.address == guessed?.address }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Ноутбук для мыши")
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setPositiveButton(if (connectAfterPick) "Подключить" else "Выбрать") { _, _ ->
                selectedBtDevice = devices[selected]
                updateSelectedPcLabel()
                if (connectAfterPick) hid.connect(devices[selected])
            }
            .setNeutralButton("Чувствительность") { _, _ -> showSensitivityDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSensitivityDialog() {
        val bar = SeekBar(this).apply {
            max = 25
            progress = ((ConnectionPrefs.peekBtSensitivity() - 0.5f) / 0.1f).toInt()
        }
        AlertDialog.Builder(this)
            .setTitle("Чувствительность")
            .setView(bar)
            .setPositiveButton("OK") { _, _ ->
                ConnectionPrefs.setBtSensitivity(0.5f + bar.progress * 0.1f)
            }
            .show()
    }

    private fun showPcServerLinksDialog() {
        val labels = arrayOf(
            "Папка pc_agent на GitHub",
            "start_server.bat — запуск вручную",
            "install_autostart.bat — автозапуск Windows",
            "Скачать APK (релизы)",
            "Открыть репозиторий",
        )
        val urls = arrayOf(
            ProjectLinks.pcAgentFolder,
            ProjectLinks.startServerBat,
            ProjectLinks.installAutostartBat,
            ProjectLinks.releases,
            ProjectLinks.repo,
        )
        AlertDialog.Builder(this)
            .setTitle("Сервер на Windows")
            .setMessage(
                "Скачайте файлы из папки pc_agent на GitHub и запустите на ноутбуке:\n\n" +
                    "• start_server.bat — один раз вручную\n" +
                    "• install_autostart.bat — сервер в фоне при входе в Windows",
            )
            .setItems(labels) { _, which ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls[which])))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBluetoothHelp() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth-мышь без программы на ПК")
            .setMessage(
                """
                1. На ноутбуке: Параметры → Bluetooth → сопрягите телефон (Pair).
                2. На телефоне: разрешите Bluetooth для RemoteTrackpad.
                3. В приложении: дождитесь «Готово» → Подключить.
                4. Если не работает: в Windows удалите телефон из Bluetooth и сопрягите заново.
                5. На части ПК Windows не принимает телефон как мышь — тогда режим USB / Wi‑Fi.
                """.trimIndent()
            )
            .setPositiveButton("Настройки BT") { _, _ ->
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            .setNeutralButton("Разрешения") { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    )
                )
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun findPcOnly() {
        if (discovering) return
        discovering = true
        statusText.text = "Поиск ПК…"
        findPcBtn.isEnabled = false
        connectBtn.isEnabled = false
        Thread {
            val result = PcDiscovery.find(wsPort())
            runOnUiThread {
                discovering = false
                findPcBtn.isEnabled = true
                connectBtn.isEnabled = true
                if (result == null) {
                    statusText.text = "ПК не найден. Запустите start_server.bat"
                } else {
                    hostInput.setText(result.host)
                    portInput.setText(result.port.toString())
                    statusText.text = "Найден: ${result.host}"
                }
            }
        }.start()
    }

    private fun findAndConnectUsb() {
        val manual = hostInput.text?.toString()?.trim().orEmpty()
        if (manual.isNotEmpty()) {
            connectUsbPc(manual, wsPort())
            return
        }
        if (discovering) return
        discovering = true
        statusText.text = "Поиск и подключение…"
        connectBtn.isEnabled = false
        findPcBtn.isEnabled = false
        Thread {
            val result = PcDiscovery.find(wsPort())
            runOnUiThread {
                if (result == null) {
                    discovering = false
                    connectBtn.isEnabled = true
                    findPcBtn.isEnabled = true
                    statusText.text = "ПК не найден"
                } else {
                    hostInput.setText(result.host)
                    portInput.setText(result.port.toString())
                    connectUsbPc(result.host, result.port)
                }
            }
        }.start()
    }

    private fun connectUsbPc(host: String, port: Int) {
        ConnectionPrefs.save(host, port)
        wsClient.connect(host, port)
    }

    override fun onDestroy() {
        wsClient.disconnect()
        hid.stop()
        super.onDestroy()
    }

    private fun ensureBtPermissionsAndStart() {
        if (useUsbMode) return
        if (Build.VERSION.SDK_INT < 31) {
            refreshBtSelection()
            hid.start()
            return
        }
        val needed = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
        val missing = needed.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(needed)
        } else {
            refreshBtSelection()
            hid.start()
        }
    }
}
