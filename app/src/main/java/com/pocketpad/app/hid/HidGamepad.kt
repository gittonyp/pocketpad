package com.pocketpad.app.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pocketpad.app.input.GamepadState
import java.util.concurrent.Executor

/**
 * Owns the Bluetooth HID Device registration and pushes gamepad reports to the
 * connected host. Must stay alive in a foreground service: Android unregisters
 * HID apps that leave the foreground.
 */
class HidGamepad(
    private val context: Context,
    private val executor: Executor,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(message: String)
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private var connected = false
    private var starting = false

    /** Last report the host actually accepted; used to skip duplicates. */
    private var lastReport: ByteArray? = null

    /** Most recent state we were asked to send; answers GET_REPORT and drives retries. */
    private var latestReport: ByteArray = GamepadReport.encode(GamepadState())

    private val handler = Handler(Looper.getMainLooper())
    private var retryScheduled = false
    private var retries = 0

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidGamepad.registered = registered
            lastReport = null
            if (!registered) {
                host = null
                connected = false
                listener.onStatus("HID registration lost — reopen PocketPad")
                return
            }
            host = pluggedDevice
            connected = false
            if (pluggedDevice == null) {
                listener.onStatus("Ready — tap Pair, then pair with this phone on your PC")
                return
            }
            // A bonded host is not necessarily a live connection, so check, and dial it if not.
            val state = try {
                hidDevice?.getConnectionState(pluggedDevice) ?: BluetoothProfile.STATE_DISCONNECTED
            } catch (e: SecurityException) {
                BluetoothProfile.STATE_DISCONNECTED
            }
            if (state == BluetoothProfile.STATE_CONNECTED) {
                markConnected(pluggedDevice)
            } else {
                listener.onStatus("Paired with ${safeName(pluggedDevice)} — connecting…")
                try {
                    hidDevice?.connect(pluggedDevice)
                } catch (e: SecurityException) {
                    Log.w(TAG, "connect denied", e)
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> markConnected(device)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == null || device == host) {
                        host = null
                        connected = false
                        lastReport = null
                        listener.onStatus("Disconnected — tap Pair to reconnect")
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val hid = hidDevice ?: return
            val requestedId = id.toInt() and 0xFF
            try {
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT &&
                    (requestedId == 0 || requestedId == GamepadReport.REPORT_ID)
                ) {
                    hid.replyReport(
                        device,
                        type,
                        GamepadReport.REPORT_ID.toByte(),
                        latestReport.copyOf()
                    )
                } else {
                    hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "GET_REPORT reply denied", e)
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // PocketPad has no output reports (rumble/LEDs); tell the host instead of leaving it hanging.
            try {
                hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            } catch (e: SecurityException) {
                Log.w(TAG, "SET_REPORT reply denied", e)
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            if (device == null || device == host) {
                host = null
                connected = false
                lastReport = null
                listener.onStatus("PC removed this controller — pair again")
            }
        }
    }

    private fun markConnected(device: BluetoothDevice?) {
        host = device ?: host
        connected = true
        lastReport = null
        retries = 0
        listener.onStatus("Connected to ${host?.let { safeName(it) } ?: "PC"}")
        // Tell the host the current (neutral) state right away.
        if (!trySend(latestReport)) scheduleRetry() else lastReport = latestReport.copyOf()
    }

    fun start() {
        if (starting || hidDevice != null) return

        val adapter = adapter
        if (adapter == null) {
            listener.onStatus("This phone has no Bluetooth adapter")
            return
        }
        if (!adapter.isEnabled) {
            listener.onStatus("Bluetooth is off — turn it on")
            return
        }

        starting = true
        listener.onStatus("Registering controller…")
        val requested = adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.HID_DEVICE || proxy !is BluetoothHidDevice) {
                        starting = false
                        adapter.closeProfileProxy(profile, proxy)
                        return
                    }
                    hidDevice = proxy
                    starting = false
                    register(proxy)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice?.let { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
                        hidDevice = null
                        starting = false
                        registered = false
                        connected = false
                        host = null
                        lastReport = null
                        handler.removeCallbacksAndMessages(null)
                        retryScheduled = false
                        listener.onStatus("Bluetooth HID service disconnected")
                    }
                }
            },
            BluetoothProfile.HID_DEVICE
        )
        if (!requested) {
            starting = false
            listener.onStatus("Could not open the Bluetooth HID service")
        }
    }

    private fun register(device: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "PocketPad",
            "PocketPad virtual gamepad",
            "PocketPad",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            HidDescriptor.GAMEPAD
        )
        val ok = try {
            device.registerApp(sdp, null, null, executor, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "registerApp denied", e)
            false
        }
        if (!ok) {
            starting = false
            listener.onStatus("Bluetooth HID registration failed")
        }
    }

    fun send(state: GamepadState) {
        val report = GamepadReport.encode(state)
        latestReport = report
        if (!registered || !connected) return
        if (report.contentEquals(lastReport ?: EMPTY)) return

        if (trySend(report)) {
            lastReport = report.copyOf()
            retries = 0
        } else {
            // Do NOT remember it as sent: a dropped "button released" report would
            // otherwise leave the key stuck down on the PC until the next touch.
            lastReport = null
            retries = 0
            scheduleRetry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun trySend(report: ByteArray): Boolean {
        val device = host ?: return false
        val hid = hidDevice ?: return false
        return try {
            hid.sendReport(device, GamepadReport.REPORT_ID, report)
        } catch (e: SecurityException) {
            Log.w(TAG, "sendReport denied", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "sendReport failed", e)
            false
        }
    }

    private fun scheduleRetry() {
        if (retryScheduled || retries >= MAX_RETRIES) return
        retryScheduled = true
        retries++
        handler.postDelayed({
            retryScheduled = false
            if (!registered || !connected) return@postDelayed
            val report = latestReport
            if (report.contentEquals(lastReport ?: EMPTY)) return@postDelayed
            if (trySend(report)) {
                lastReport = report.copyOf()
                retries = 0
            } else {
                scheduleRetry()
            }
        }, RETRY_DELAY_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        retryScheduled = false
        val hid = hidDevice ?: return
        try {
            hid.unregisterApp()
        } catch (e: SecurityException) {
            Log.w(TAG, "unregisterApp denied", e)
        }
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        hidDevice = null
        host = null
        registered = false
        connected = false
        starting = false
        lastReport = null
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String = try {
        device.name ?: "PC"
    } catch (e: SecurityException) {
        "PC"
    }

    private companion object {
        const val TAG = "HidGamepad"
        const val MAX_RETRIES = 5
        const val RETRY_DELAY_MS = 15L
        val EMPTY = ByteArray(0)
    }
}
