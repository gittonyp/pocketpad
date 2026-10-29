package com.pocketpad.app.input

/**
 * Plain, Android-free gamepad state. All values are in HID wire units:
 * sticks are 0..255 with 128 centered, triggers are 0..255, and the hat is
 * 0..7 (clockwise from up) with 8 meaning neutral.
 */
class GamepadState {
    val buttons = BooleanArray(Buttons.COUNT)
    var hat: Int = HAT_NEUTRAL
    var leftX: Int = CENTER
    var leftY: Int = CENTER
    var rightX: Int = CENTER
    var rightY: Int = CENTER
    var leftTrigger: Int = 0
    var rightTrigger: Int = 0

    fun reset() {
        buttons.fill(false)
        hat = HAT_NEUTRAL
        leftX = CENTER
        leftY = CENTER
        rightX = CENTER
        rightY = CENTER
        leftTrigger = 0
        rightTrigger = 0
    }

    companion object {
        const val CENTER = 128
        const val HAT_NEUTRAL = 8
    }
}
