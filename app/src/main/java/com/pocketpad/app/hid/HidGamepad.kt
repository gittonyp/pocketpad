package com.pocketpad.app.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
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
    private var starting = false
    private var lastReport: ByteArray? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidGamepad.registered = registered
            lastReport = null
            if (!registered) {
                host = null
                listener.onStatus("HID registration lost — reopen PocketPad")
                return
            }
            host = pluggedDevice
            if (pluggedDevice != null) {
                listener.onStatus("Connected to ${safeName(pluggedDevice)}")
            } else {
                listener.onStatus("Ready — tap Pair, then pair with this phone on your PC")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    lastReport = null
                    listener.onStatus("Connected to ${device?.let { safeName(it) } ?: "PC"}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == null || device == host) {
                        host = null
                        lastReport = null
                        listener.onStatus("Disconnected — tap Pair to reconnect")
                    }
                }
            }
        }
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
                        return
                    }
                    hidDevice = proxy
                    starting = false
                    register(proxy)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null
                        registered = false
                        host = null
                        lastReport = null
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

    @SuppressLint("MissingPermission")
    fun send(state: GamepadState) {
        if (!registered) return
        val device = host ?: return
        val hid = hidDevice ?: return

        val report = GamepadReport.encode(state)
        if (report.contentEquals(lastReport ?: EMPTY)) return
        lastReport = report.copyOf()

        try {
            hid.sendReport(device, GamepadReport.REPORT_ID, report)
        } catch (e: SecurityException) {
            Log.w(TAG, "sendReport denied", e)
            lastReport = null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "sendReport failed", e)
            lastReport = null
        }
    }

    fun stop() {
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
        val EMPTY = ByteArray(0)
    }
}
