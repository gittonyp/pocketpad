package com.pocketpad.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!startInForeground()) {
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
        controller.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
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
