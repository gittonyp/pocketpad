package com.pocketpad.app

import com.pocketpad.app.hid.HidGamepad

/** Process-wide bridge between the foreground service and the UI. */
object ControllerHub {
    @Volatile
    var gamepad: HidGamepad? = null

    @Volatile
    var lastStatus: String = "Starting controller…"

    private var statusListener: ((String) -> Unit)? = null

    fun setStatusListener(listener: ((String) -> Unit)?) {
        statusListener = listener
        listener?.invoke(lastStatus)
    }

    fun updateStatus(message: String) {
        lastStatus = message
        statusListener?.invoke(message)
    }
}
