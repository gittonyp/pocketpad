package com.pocketpad.app.hid

import com.pocketpad.app.input.GamepadState

/** Encodes [GamepadState] into the 9-byte input report described by [HidDescriptor]. */
object GamepadReport {
    const val REPORT_ID = 1
    const val LENGTH = 9

    fun encode(state: GamepadState, out: ByteArray = ByteArray(LENGTH)): ByteArray {
        require(out.size >= LENGTH) { "Report buffer must be at least $LENGTH bytes" }

        var low = 0
        var high = 0
        val count = minOf(16, state.buttons.size)
        for (i in 0 until count) {
            if (!state.buttons[i]) continue
            if (i < 8) {
                low = low or (1 shl i)
            } else {
                high = high or (1 shl (i - 8))
            }
        }

        out[0] = low.toByte()
        out[1] = high.toByte()
        out[2] = (state.hat and 0x0F).toByte()
        out[3] = clampByte(state.leftX)
        out[4] = clampByte(state.leftY)
        out[5] = clampByte(state.rightX)
        out[6] = clampByte(state.rightY)
        out[7] = clampByte(state.leftTrigger)
        out[8] = clampByte(state.rightTrigger)
        return out
    }

    private fun clampByte(value: Int): Byte = value.coerceIn(0, 255).toByte()
}
