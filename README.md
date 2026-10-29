# PocketPad

Turn your Android phone into a wireless Xbox- or PlayStation-style controller for a PC.
PocketPad makes the phone advertise itself as a standard Bluetooth HID gamepad, so the
PC sees a controller with **nothing installed on the PC** — no receiver, no driver, no
companion app.

## Requirements

- Android 9+ (API 28) with the Bluetooth HID **Device** profile enabled.
  Realme/OPPO firmware sometimes disables it — check with the free
  *Bluetooth HID Profile Tester* app before installing.
- A PC with Bluetooth.
- For best game compatibility, launch games through **Steam** (Steam Input maps the
  generic HID gamepad). Most SDL2 games and emulators also work directly. Some
  XInput-only titles may not see a generic HID pad without Steam.

## Install

1. Download `app-debug.apk` from the repo's **Releases** (or the latest Actions run).
2. Sideload it onto the phone and open **PocketPad**.
3. Grant the Bluetooth permissions when asked.
4. On the PC: **Settings → Bluetooth & devices → Add device → Bluetooth**, then pick
   **PocketPad**. (Tap **Pair** first so the phone is discoverable.)
5. The status bar should read **Connected**. Play.

Keep the app in the foreground; the ongoing notification keeps the controller
registered.

## Controls

| Phone control | Gamepad |
| --- | --- |
| Left stick / right stick | Analog sticks (tap a stick for L3/R3) |
| D-pad | Hat switch |
| A/B/X/Y or ✕/○/□/△ | Face buttons |
| LB/RB or L1/R1 | Shoulders |
| LT/RT or L2/R2 | Analog triggers (press lower for more travel) |
| View/Share, Menu/Options | Back / Start |
| Guide/PS | Guide / PS |
| Xbox / PS button (top bar) | Switch skin |

## Build

Nothing needs to be installed locally: the Android SDK lives in the container/CI.

### GitHub Codespaces

Open the repo in a Codespace. The devcontainer installs JDK 17, Gradle 8.9, and the
Android SDK (command-line tools, platform 35, build-tools 35). Then:

```bash
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

Every push/PR runs unit tests and builds the APK. Push a tag to attach the APK to a
release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Limitations

- **Generic HID identity.** Without root the phone cannot report Microsoft/Sony USB
  IDs, so the PC shows a generic HID gamepad rather than a literal Xbox/DualShock.
- **No WiFi.** A standard gamepad cannot be delivered over WiFi without a PC-side
  receiver, which the project intentionally avoids.
- **HID Device profile must exist in firmware.** If the tester app fails on your
  phone, no app-level workaround exists without root.

## Troubleshooting

- **No "PocketPad" in the PC's Bluetooth list** — tap **Pair** in the app first; keep
  the app open. Remove any old pairing on both sides and retry.
- **Connected but games ignore it** — use Steam Input, or check the pad in Windows
  `joy.cpl` / `Game Controllers`.
- **Drops when switching apps** — PocketPad uses a foreground service; make sure the
  ongoing notification is present and battery optimization is not killing the app.
- **"Bluetooth HID not supported"** — the phone firmware lacks the HID Device profile.

## Project layout

```
app/src/main/java/com/pocketpad/app/
  MainActivity.kt          UI, permissions, status
  ControllerHub.kt         service <-> UI link
  hid/                     HID descriptor, report encoder, Bluetooth registration
  input/                   pure gamepad state + button map
  service/HidService.kt    foreground service owning the HID registration
  ui/                      custom multitouch pad view + skins
app/src/test/              JVM unit tests
.devcontainer/             Codespaces image with the Android SDK
.github/workflows/         CI build + release
```

## License

MIT — see [LICENSE](LICENSE).
