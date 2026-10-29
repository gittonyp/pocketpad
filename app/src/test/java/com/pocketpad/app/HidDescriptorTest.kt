package com.pocketpad.app

import com.pocketpad.app.hid.GamepadReport
import com.pocketpad.app.hid.HidDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Walks the HID report descriptor item by item, so a mismatch between the descriptor and
 * [GamepadReport] (wrong size, wrong report ID, leaked global items) fails in CI instead of
 * showing up as a silently ignored or garbled controller on Windows.
 */
class HidDescriptorTest {

    private data class Item(val type: Int, val tag: Int, val value: Int)

    private companion object {
        const val TYPE_MAIN = 0
        const val TYPE_GLOBAL = 1

        const val MAIN_INPUT = 0x8
        const val GLOBAL_LOGICAL_MAX = 0x2
        const val GLOBAL_PHYSICAL_MIN = 0x3
        const val GLOBAL_PHYSICAL_MAX = 0x4
        const val GLOBAL_REPORT_SIZE = 0x7
        const val GLOBAL_REPORT_ID = 0x8
        const val GLOBAL_REPORT_COUNT = 0x9
    }

    private fun parse(d: ByteArray): List<Item> {
        val items = mutableListOf<Item>()
        var i = 0
        while (i < d.size) {
            val prefix = d[i].toInt() and 0xFF
            val sizeCode = prefix and 0x03
            val size = if (sizeCode == 3) 4 else sizeCode
            var value = 0
            for (k in 0 until size) {
                value = value or ((d[i + 1 + k].toInt() and 0xFF) shl (8 * k))
            }
            items += Item((prefix shr 2) and 0x03, prefix shr 4, value)
            i += 1 + size
        }
        return items
    }

    /** One entry per Input item, with the global state that was in force at that point. */
    private data class InputField(
        val bits: Int,
        val reportId: Int,
        val logicalMax: Int,
        val physicalMin: Int,
        val physicalMax: Int
    )

    private fun inputFields(): List<InputField> {
        val globals = mutableMapOf<Int, Int>()
        val fields = mutableListOf<InputField>()
        for (item in parse(HidDescriptor.GAMEPAD)) {
            when (item.type) {
                TYPE_GLOBAL -> globals[item.tag] = item.value
                TYPE_MAIN -> if (item.tag == MAIN_INPUT) {
                    fields += InputField(
                        bits = globals.getValue(GLOBAL_REPORT_SIZE) * globals.getValue(GLOBAL_REPORT_COUNT),
                        reportId = globals.getValue(GLOBAL_REPORT_ID),
                        logicalMax = globals[GLOBAL_LOGICAL_MAX] ?: 0,
                        physicalMin = globals[GLOBAL_PHYSICAL_MIN] ?: 0,
                        physicalMax = globals[GLOBAL_PHYSICAL_MAX] ?: 0
                    )
                }
            }
        }
        return fields
    }

    @Test
    fun inputFieldsAddUpToTheReportLength() {
        val totalBits = inputFields().sumOf { it.bits }
        assertEquals(GamepadReport.LENGTH * 8, totalBits)
    }

    @Test
    fun everyInputFieldUsesTheEncodersReportId() {
        assertTrue(inputFields().all { it.reportId == GamepadReport.REPORT_ID })
    }

    @Test
    fun hatPhysicalRangeDoesNotLeakOntoTheAxes() {
        // Physical Min/Max are global items: they stay in force until changed.
        val axes = inputFields().filter { it.logicalMax == 255 }
        assertEquals(2, axes.size) // X/Y/Z/Rz block and Rx/Ry block
        for (axis in axes) {
            assertEquals(0, axis.physicalMin)
            assertTrue(
                "physical max ${axis.physicalMax} must be unset or equal to logical max",
                axis.physicalMax == 0 || axis.physicalMax == axis.logicalMax
            )
        }
    }

    @Test
    fun neutralHatValueIsOutsideTheLogicalRange() {
        // The hat has Null State, so 8 (> logical max 7) is what the host reads as "released".
        val hat = inputFields().first { it.logicalMax == 7 && it.bits == 4 }
        assertTrue(com.pocketpad.app.input.GamepadState.HAT_NEUTRAL > hat.logicalMax)
    }
}
