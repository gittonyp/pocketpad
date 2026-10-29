package com.pocketpad.app.hid

/**
 * HID report descriptor for a generic gamepad.
 *
 * Layout of report ID 1 (9 data bytes):
 *   bytes 0-1  : 16 buttons (bit 0 = button 1)
 *   byte  2    : hat switch in the low nibble (8 = neutral), 4 bits padding
 *   bytes 3-6  : X, Y, Z, Rz (left stick, right stick) each 0..255
 *   bytes 7-8  : Rx, Ry (left and right triggers) each 0..255
 */
object HidDescriptor {
    val GAMEPAD: ByteArray = bytes(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x05,             // Usage (Game Pad)
        0xA1, 0x01,             // Collection (Application)
        0x85, 0x01,             //   Report ID (1)
        0x05, 0x09,             //   Usage Page (Button)
        0x19, 0x01,             //   Usage Minimum (1)
        0x29, 0x10,             //   Usage Maximum (16)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95, 0x10,             //   Report Count (16)
        0x81, 0x02,             //   Input (Data, Var, Abs)
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x39,             //   Usage (Hat switch)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x07,             //   Logical Maximum (7)
        0x35, 0x00,             //   Physical Minimum (0)
        0x46, 0x3B, 0x01,       //   Physical Maximum (315)
        0x65, 0x14,             //   Unit (English Rotation: Degrees)
        0x75, 0x04,             //   Report Size (4)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x42,             //   Input (Data, Var, Abs, Null State)
        0x65, 0x00,             //   Unit (None)
        0x35, 0x00,             //   Physical Minimum (0)  - reset so the hat's 0..315 does not
        0x45, 0x00,             //   Physical Maximum (0)    leak onto the axes that follow
        0x75, 0x04,             //   Report Size (4)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x03,             //   Input (Const, Var, Abs) padding
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x09, 0x32,             //   Usage (Z)
        0x09, 0x35,             //   Usage (Rz)
        0x15, 0x00,             //   Logical Minimum (0)
        0x26, 0xFF, 0x00,       //   Logical Maximum (255)
        0x75, 0x08,             //   Report Size (8)
        0x95, 0x04,             //   Report Count (4)
        0x81, 0x02,             //   Input (Data, Var, Abs)
        0x09, 0x33,             //   Usage (Rx)
        0x09, 0x34,             //   Usage (Ry)
        0x15, 0x00,             //   Logical Minimum (0)
        0x26, 0xFF, 0x00,       //   Logical Maximum (255)
        0x75, 0x08,             //   Report Size (8)
        0x95, 0x02,             //   Report Count (2)
        0x81, 0x02,             //   Input (Data, Var, Abs)
        0xC0                    // End Collection
    )

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }
}
