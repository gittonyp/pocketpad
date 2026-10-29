# PocketPad — Virtual Controller: Design

Date: 2026-10-29

## Goal

Turn an Android phone into a wireless gamepad for a PC with **no companion software
installed on the PC**. The phone presents itself as a standard Bluetooth HID gamepad;
the PC pairs to it like any Bluetooth controller.

## Hard constraints

- No Android SDK installed on the user's machine. Build happens in GitHub Codespaces
  and GitHub Actions.
- No extra software on the PC (no ViGEm, DS4Windows, vJoy, driver, or receiver app).
- Works over Bluetooth. WiFi cannot deliver a standard gamepad to a PC without a
  receiver, so WiFi is explicitly out of scope.
- Target phone: Realme, Android 13. Bluetooth HID Device profile verified present
  with "Bluetooth HID Profile Tester" (pass).

## Chosen approach

**Android Bluetooth HID Device profile** (`BluetoothHidDevice`, API 28+).

- App registers an SDP record with subclass `SUBCLASS2_GAMEPAD` and a report
  descriptor: 16 buttons, 4-bit hat switch, four 8-bit stick axes, two 8-bit
  trigger axes, report ID 1 (9-byte input report).
- Phone is made discoverable; the PC pairs to "PocketPad" and sees a generic HID
  gamepad (DirectInput). Steam Input, SDL2 games, and emulators handle it.
  XInput-only titles may not see it without Steam or a PC-side driver — accepted
  tradeoff for the "nothing on the PC" requirement.
- Registration is owned by a **foreground service** because Android auto-unregisters
  HID apps that leave the foreground; the service keeps registration alive while the
  user opens the system "make discoverable" dialog and while connected.

## Alternatives considered

1. **WiFi + receiver on PC** — reliable XInput via ViGEm, but requires installing a
   driver. Rejected by the no-PC-software rule.
2. **WiFi + browser receiver** — no install, but a web page cannot inject input into
   native games. Rejected.
3. **Bluetooth HID (chosen)** — only path that is wireless and install-free on the PC.

## Components

- `MainActivity` — landscape, immersive, hosts the pad, top status bar, skin toggle,
  Pair button, runtime permission flow, starts the HID foreground service.
- `service/HidService` — foreground service (`connectedDevice` type) that owns
  `HidGamepad`, shows an ongoing notification, survives activity pauses.
- `hid/HidGamepad` — gets the `BluetoothHidDevice` proxy, registers/unregisters the
  SDP app, tracks the connected host, sends reports.
- `hid/HidDescriptor` — the HID report descriptor bytes.
- `hid/GamepadReport` — pure encoder: `GamepadState` → 9-byte report.
- `input/GamepadState` / `input/Buttons` — pure model and button indices.
- `ui/GamepadView` — custom `View`; draws controls and handles multi-touch directly.
- `ui/Skin` — Xbox and PlayStation visuals, labels, and colors.
- `ControllerHub` — tiny process-wide holder linking the service's gamepad to the UI.

## Input mapping

| Control | HID |
| --- | --- |
| A / ✕ | button 1 |
| B / ○ | button 2 |
| X / □ | button 3 |
| Y / △ | button 4 |
| LB / L1, RB / R1 | buttons 5, 6 |
| View/Share, Menu/Options, Guide/PS | buttons 7, 8, 9 |
| L3 / R3 | buttons 10, 11 (quick tap on a stick) |
| D-pad | hat switch 0–7, 8 = neutral |
| Left stick | X, Y (0–255, center 128) |
| Right stick | Z, Rz (0–255, center 128) |
| LT / L2, RT / R2 | Rx, Ry (0–255, position-sensitive) |

Reports are sent when state changes, throttled to ~125 Hz.

## UI / UX

- Landscape only, immersive fullscreen, keep screen on.
- Xbox skin: green accent, A/B/X/Y colored buttons.
- PlayStation skin: blue accent, △/○/□/✕ buttons.
- Layout mirrors a real pad: left stick upper-left with D-pad below; face buttons
  upper-right with right stick below; triggers/shoulders along the top; center
  buttons in the middle.
- Top bar: status text, skin toggle, Pair button.

## Error handling

- Missing Bluetooth adapter / Bluetooth off / HID unsupported / registration failure
  are reported in the status bar.
- Runtime permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, notifications) are
  requested on Android 12+; denial shows a clear status message.
- Sending reports is guarded by `registered` and host presence, and swallows
  `SecurityException`.

## Testing / verification

- JVM unit tests for the report encoder, descriptor, and state reset.
- CI (`assembleDebug` + `testDebugUnitTest`) is the build gate; no local SDK.
- Manual smoke test on the Realme: install APK, grant permissions, Pair, add
  "PocketPad" on the PC, verify axes/buttons in Steam or `joy.cpl`.
