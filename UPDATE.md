# ProxSh — Development Update

This document summarizes the changes made to the ProxSh project during the Windows laptop and Android device connection work.

## 1. Connection mode setting

The laptop application now has a connection mode option in **Settings**:

- **Automatic LAN** — the normal mode for connecting to a physical Android phone or tablet over the local network.
- **Android Emulator (ADB)** — a development mode for connecting the laptop application to an Android emulator through ADB port forwarding.

The selected mode is saved and restored when the laptop application is restarted.

### Settings persistence

The laptop app stores its settings in:

```text
%APPDATA%\ProxSh\settings.json
```

The settings file includes the selected `connectionMode`. For example:

```json
{
  "deviceName": "LAPTOP-NAME",
  "receiveDir": "C:\\Users\\USER\\Downloads\\ProxSh",
  "connectionMode": "EMULATOR_ADB"
}
```

The actual device name and receive directory depend on the machine.

## 2. Android emulator connection

The emulator used during development could discover the laptop, but its network address did not provide a directly usable route back to the laptop. ADB port forwarding was used to provide that route.

The forwarding command used during development was:

```powershell
adb forward tcp:53319 tcp:53317
```

This forwards port `53319` on the Android emulator's ADB connection to port `53317` on the laptop.

To use this mode:

1. Start the Android emulator.
2. Confirm that ADB detects it:

   ```powershell
   adb devices
   ```

3. Set up the port forwarding:

   ```powershell
   adb forward tcp:53319 tcp:53317
   ```

4. Launch the ProxSh laptop application.
5. Open **Settings** and select **Android Emulator (ADB)**.

The ADB forwarding command must be active while using this connection. The port values are the ones used during this development setup; check the current listening port and connection configuration if they change.

For a physical Android phone or tablet, use **Automatic LAN**, not the emulator ADB mode.

## 3. Code changes

The following source files were modified:

| File | Change |
| --- | --- |
| `laptop/src/main/kotlin/app/proxsh/laptop/Settings.kt` | Added the `ConnectionMode` setting and persistence of the selected mode. |
| `laptop/src/main/kotlin/app/proxsh/laptop/AppModel.kt` | Added connection-mode handling and applies the emulator ADB endpoint when that mode is selected for sending. |
| `laptop/src/main/kotlin/app/proxsh/laptop/ui/Overlays.kt` | Added the connection mode choices to the laptop Settings UI. |
| `core/src/main/kotlin/app/proxsh/core/discovery/Discovery.kt` | Updated peer discovery and candidate handling for the local-network connection issue encountered during testing. |
| `core/src/main/kotlin/app/proxsh/core/Node.kt` | Updated connection handling related to the connection and discovery work. |
| `.gitignore` | Updated ignore rules. |

## 4. Build and packaging

### Laptop application

Run the laptop application:

```powershell
.\gradlew.bat :laptop:run
```

Build the Windows installer:

```powershell
.\gradlew.bat :laptop:packageExe
```

The installer was successfully generated at:

```text
laptop/build/compose/binaries/main/exe/ProxSh-0.1.0.exe
```

The generated installer is unsigned. Windows security settings may show an unknown-publisher warning or block execution.

### Android application

Build the debug APK:

```powershell
.\gradlew.bat :android:assembleDebug
```

APK output:

```text
android/build/outputs/apk/debug/android-debug.apk
```

Install it on a connected device using ADB:

```powershell
adb devices
adb install -r .\android\build\outputs\apk\debug\android-debug.apk
```

A Samsung Galaxy Tab A7 was detected by ADB during development. The APK installation command was run, but a final successful installation result was not confirmed in the development session.

### JDK note

JDK 21 was used successfully for the Windows EXE packaging step. The generated `gradle/gradle-daemon-jvm.properties` file that specified Java 25 was removed rather than included in the project changes. The unrelated addition to `gradle.properties` was also reverted.

## 5. Bluetooth clarification

Bluetooth pairing by itself does not make ProxSh communicate over Bluetooth.

The work described here uses network connections. Native Bluetooth discovery and a Bluetooth file-transfer transport have not been implemented or verified. Bluetooth PAN could potentially provide an IP network interface, but ProxSh would still need to discover and use that route correctly; this was not validated.

## 6. Current limitations / items not verified

- The emulator ADB route was configured and tested using port forwarding on the development machine. It depends on ADB forwarding being active.
- A physical Android device should use **Automatic LAN**. Successful end-to-end transfer with the physical tablet was not confirmed in this session.
- Native Bluetooth discovery and transfer are not available as a confirmed feature.
- Real hotspot and internet-route operation were not confirmed in this development work.
- The Windows installer was generated, but it is unsigned.

## 7. Files changed in this update

```text
.gitignore
core/src/main/kotlin/app/proxsh/core/Node.kt
core/src/main/kotlin/app/proxsh/core/discovery/Discovery.kt
laptop/src/main/kotlin/app/proxsh/laptop/AppModel.kt
laptop/src/main/kotlin/app/proxsh/laptop/Settings.kt
laptop/src/main/kotlin/app/proxsh/laptop/ui/Overlays.kt
```

`README.md` was updated separately to document the new connection mode setting and build/run instructions.
