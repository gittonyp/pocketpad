package com.pocketpad.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.pocketpad.app.ControllerHub
import com.pocketpad.app.MainActivity
import com.pocketpad.app.R
import com.pocketpad.app.hid.HidGamepad
import java.util.concurrent.Executor

/**
 * Keeps the HID registration alive while the pad is in use. Android unregisters
 * HID apps that leave the foreground, so registration is owned by this service.
 */
class HidService : Service() {

    private var gamepad: HidGamepad? = null
    private var receiverRegistered = false

    /** Re-register the HID profile when Bluetooth is switched back on. */
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> gamepad?.start()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF ->
                    ControllerHub.updateStatus("Bluetooth is off — turn it on")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!startInForeground()) {
            ControllerHub.updateStatus("Could not start the controller service — check permissions")
            stopSelf()
            return
        }

        val executor = Executor { command -> mainExecutor.execute(command) }
        val controller = HidGamepad(this, executor, object : HidGamepad.Listener {
            override fun onStatus(message: String) {
                ControllerHub.updateStatus(message)
            }
        })
        gamepad = controller
        ControllerHub.gamepad = controller

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothStateReceiver, filter)
        }
        receiverRegistered = true

        controller.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // start() is a no-op when already running, but recovers after Bluetooth was off
        // or the HID proxy dropped. Not sticky: without the UI the pad is useless, and a
        // background restart would hit foreground-service start restrictions.
        gamepad?.start()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver)
            receiverRegistered = false
        }
        gamepad?.stop()
        gamepad = null
        ControllerHub.gamepad = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(): Boolean {
        return try {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)

            val intent = Intent(this, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service", e)
            false
        }
    }

    companion object {
        private const val TAG = "HidService"
        private const val CHANNEL_ID = "pocketpad-controller"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: HidService? = null
            private set

        fun ensureStarted(context: Context) {
            val intent = Intent(context, HidService::class.java)
            context.startForegroundService(intent)
        }
    }
}
