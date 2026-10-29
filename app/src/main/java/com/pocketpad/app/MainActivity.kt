package com.pocketpad.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.pocketpad.app.input.GamepadState
import com.pocketpad.app.service.HidService
import com.pocketpad.app.ui.GamepadView
import com.pocketpad.app.ui.Skin

class MainActivity : Activity() {

    private lateinit var gamepadView: GamepadView
    private lateinit var statusText: TextView
    private lateinit var skinButton: Button

    private var skin = Skin.XBOX

    private val handler = Handler(Looper.getMainLooper())
    private var lastSendAt = 0L
    private var sendScheduled = false
    private var latestState: GamepadState? = null

    private val bluetoothPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            emptyArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        setContentView(R.layout.activity_main)

        gamepadView = findViewById(R.id.gamepadView)
        statusText = findViewById(R.id.statusText)
        skinButton = findViewById(R.id.skinButton)
        val pairButton = findViewById<Button>(R.id.pairButton)

        gamepadView.skin = skin
        gamepadView.onStateChanged = { state -> queueSend(state) }

        skinButton.setOnClickListener {
            skin = if (skin == Skin.XBOX) Skin.PLAYSTATION else Skin.XBOX
            gamepadView.skin = skin
            skinButton.setText(
                if (skin == Skin.XBOX) R.string.skin_xbox else R.string.skin_playstation
            )
        }

        pairButton.setOnClickListener { pair() }

        if (hasBluetoothPermissions()) {
            startController()
        } else {
            statusText.setText(R.string.status_permissions)
            requestPermissions(bluetoothPermissions, REQUEST_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        ControllerHub.setStatusListener { message -> statusText.text = message }
        if (hasBluetoothPermissions()) {
            ensureService()
        }
    }

    override fun onPause() {
        ControllerHub.setStatusListener(null)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (isFinishing) {
            stopService(Intent(this, HidService::class.java))
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (hasBluetoothPermissions()) {
            startController()
        } else {
            statusText.setText(R.string.status_permissions)
            Toast.makeText(this, R.string.status_permissions, Toast.LENGTH_LONG).show()
        }
    }

    private fun startController() {
        statusText.setText(R.string.status_starting)
        ensureService()
        maybeRequestNotifications()
    }

    private fun ensureService() {
        try {
            HidService.ensureStarted(this)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start controller service", e)
            statusText.text = "Could not start the controller service"
        }
    }

    private fun pair() {
        if (!hasBluetoothPermissions()) {
            requestPermissions(bluetoothPermissions, REQUEST_PERMISSIONS)
            return
        }
        ensureService()
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        }
        try {
            startActivity(intent)
            statusText.setText(R.string.status_pair_hint)
        } catch (e: Exception) {
            Log.e(TAG, "Could not request discoverability", e)
            Toast.makeText(this, "Could not make the phone discoverable", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun hasBluetoothPermissions(): Boolean = bluetoothPermissions.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun queueSend(state: GamepadState) {
        latestState = state
        val now = SystemClock.uptimeMillis()
        val wait = MIN_SEND_INTERVAL_MS - (now - lastSendAt)
        if (wait <= 0) {
            lastSendAt = now
            ControllerHub.gamepad?.send(state)
        } else if (!sendScheduled) {
            sendScheduled = true
            handler.postDelayed({
                sendScheduled = false
                val pending = latestState ?: return@postDelayed
                lastSendAt = SystemClock.uptimeMillis()
                ControllerHub.gamepad?.send(pending)
            }, wait)
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val REQUEST_PERMISSIONS = 100
        const val REQUEST_NOTIFICATIONS = 101
        const val DISCOVERABLE_SECONDS = 300
        const val MIN_SEND_INTERVAL_MS = 8L
    }
}
