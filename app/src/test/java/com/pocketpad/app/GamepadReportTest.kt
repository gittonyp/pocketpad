package com.pocketpad.app

import com.pocketpad.app.hid.GamepadReport
import com.pocketpad.app.hid.HidDescriptor
import com.pocketpad.app.input.Buttons
import com.pocketpad.app.input.GamepadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadReportTest {

    @Test
    fun neutralStateEncodesCenteredReport() {
        val report = GamepadReport.encode(GamepadState())

        assertEquals(9, report.size)
        assertEquals(0, report[0].toInt() and 0xFF)
        assertEquals(0, report[1].toInt() and 0xFF)
        assertEquals(GamepadState.HAT_NEUTRAL, report[2].toInt() and 0x0F)
        for (i in 3..6) {
            assertEquals(128, report[i].toInt() and 0xFF)
        }
        assertEquals(0, report[7].toInt() and 0xFF)
        assertEquals(0, report[8].toInt() and 0xFF)
    }

    @Test
    fun buttonsAndAxesLandInTheRightBytes() {
        val state = GamepadState()
        state.buttons[Buttons.A] = true
        state.buttons[Buttons.GUIDE] = true
        state.hat = 2
        state.leftX = 255
        state.leftY = 0
        state.rightX = 0
        state.rightY = 255
        state.leftTrigger = 200
        state.rightTrigger = 50

        val report = GamepadReport.encode(state)

        assertEquals(1, report[0].toInt() and 0xFF)
        assertEquals(1, report[1].toInt() and 0xFF)
        assertEquals(2, report[2].toInt() and 0x0F)
        assertEquals(255, report[3].toInt() and 0xFF)
        assertEquals(0, report[4].toInt() and 0xFF)
        assertEquals(0, report[5].toInt() and 0xFF)
        assertEquals(255, report[6].toInt() and 0xFF)
        assertEquals(200, report[7].toInt() and 0xFF)
        assertEquals(50, report[8].toInt() and 0xFF)
    }

    @Test
    fun valuesAreClampedToByteRange() {
        val state = GamepadState()
        state.leftX = -40
        state.rightX = 900
        state.leftTrigger = 999

        val report = GamepadReport.encode(state)

        assertEquals(0, report[3].toInt() and 0xFF)
        assertEquals(255, report[5].toInt() and 0xFF)
        assertEquals(255, report[7].toInt() and 0xFF)
    }

    @Test
    fun resetReturnsStateToNeutral() {
        val state = GamepadState()
        state.buttons[Buttons.B] = true
        state.hat = 5
        state.leftX = 3
        state.rightY = 240
        state.rightTrigger = 255

        state.reset()

        assertEquals(0, GamepadReport.encode(state)[0].toInt() and 0xFF)
        assertEquals(GamepadState.HAT_NEUTRAL, state.hat)
        assertEquals(128, state.leftX)
        assertEquals(128, state.rightY)
        assertEquals(0, state.rightTrigger)
    }

    @Test
    fun descriptorIsAGamepadApplicationCollection() {
        val d = HidDescriptor.GAMEPAD
        assertTrue(d.size > 40)
        assertEquals(0x05, d[0].toInt() and 0xFF)
        assertEquals(0x01, d[1].toInt() and 0xFF)
        assertEquals(0x09, d[2].toInt() and 0xFF)
        assertEquals(0x05, d[3].toInt() and 0xFF)
        assertEquals(0xA1, d[4].toInt() and 0xFF)
        assertEquals(0x01, d[5].toInt() and 0xFF)
        assertEquals(0xC0, d[d.size - 1].toInt() and 0xFF)
    }
}
