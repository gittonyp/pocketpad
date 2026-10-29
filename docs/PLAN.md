# Implementation Plan

1. Scaffold Gradle Android project (Kotlin, minSdk 28, target/compile 35, JDK 17).
2. Implement pure input model + HID report descriptor + 9-byte report encoder.
3. Add JVM unit tests for encoder, descriptor, and state reset.
4. Implement `HidGamepad` (register/unregister SDP, track host, send reports).
5. Implement `HidService` foreground service and `ControllerHub` link.
6. Implement `GamepadView` multitouch input and Xbox/PS skins.
7. Implement `MainActivity` layout, permissions, status, skin toggle, Pair.
8. Add Codespaces devcontainer (Android SDK inside container) and GitHub Actions
   workflow that builds the APK, runs unit tests, uploads an artifact, and attaches
   the APK to a Release on `v*` tags.
9. Generate the Gradle wrapper, commit, create the GitHub repo, push.
10. Watch CI; fix until green; tag a release so the APK is downloadable.
