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
import android.provider.Settings
import android.net.Uri
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
    private lateinit var customizeButton: Button

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
        customizeButton = findViewById(R.id.customizeButton)
        val pairButton = findViewById<Button>(R.id.pairButton)

        skin = if (getPreferences(MODE_PRIVATE).getBoolean(SKIN_PS_PREF, false)) {
            Skin.PLAYSTATION
        } else {
            Skin.XBOX
        }
        skinButton.setText(
            if (skin == Skin.XBOX) R.string.skin_xbox else R.string.skin_playstation
        )
        gamepadView.skin = skin
        gamepadView.restoreLayout(getPreferences(MODE_PRIVATE).getString(LAYOUT_PREF, null))
        gamepadView.onStateChanged = { state -> queueSend(state) }
        gamepadView.onLayoutChanged = { layout ->
            getPreferences(MODE_PRIVATE).edit().putString(LAYOUT_PREF, layout).apply()
        }

        skinButton.setOnClickListener {
            skin = if (skin == Skin.XBOX) Skin.PLAYSTATION else Skin.XBOX
            gamepadView.skin = skin
            skinButton.setText(
                if (skin == Skin.XBOX) R.string.skin_xbox else R.string.skin_playstation
            )
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean(SKIN_PS_PREF, skin == Skin.PLAYSTATION)
                .apply()
        }

        customizeButton.setOnClickListener {
            gamepadView.customizeMode = !gamepadView.customizeMode
            customizeButton.setText(
                if (gamepadView.customizeMode) R.string.done else R.string.customize
            )
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            gamepadView.resetLayout()
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
        // Never leave a button or stick held on the PC while we are not on screen.
        gamepadView.releaseAll()
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
            // After "Don't allow" twice Android stops showing the dialog; send them to Settings.
            val blocked = bluetoothPermissions.any {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED &&
                    !shouldShowRequestPermissionRationale(it)
            }
            if (blocked) openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open app settings", e)
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
            startActivityForResult(intent, REQUEST_DISCOVERABLE)
            statusText.setText(R.string.status_pair_hint)
        } catch (e: Exception) {
            Log.e(TAG, "Could not request discoverability", e)
            Toast.makeText(this, "Could not make the phone discoverable", Toast.LENGTH_LONG)
                .show()
        }
    }

    @Deprecated("Plain Activity result API; this activity does not use AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DISCOVERABLE && resultCode == RESULT_CANCELED) {
            statusText.setText(R.string.status_discoverable_declined)
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
        const val REQUEST_DISCOVERABLE = 102
        const val SKIN_PS_PREF = "skin_playstation"
        const val DISCOVERABLE_SECONDS = 300
        const val MIN_SEND_INTERVAL_MS = 8L
        const val LAYOUT_PREF = "custom_gamepad_layout"
    }
}
