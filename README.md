# ProxSh

Send files and text between your phone and laptop the way AirDrop does it: tap Share, pick the
device, done. No account, no QR code, no cable. ProxSh measures every way two devices can reach each
other (same Wi-Fi, phone hotspot, internet) and sends over the fastest one, and an interrupted
transfer carries on instead of starting again.

SEPM course project, work in progress. See [Status](#status) for exactly what is and isn't built.

## How it works

- **No setup.** Each device makes its own certificate on first run. Its fingerprint is its identity.
- **Nearby devices appear by themselves** (UDP multicast announcements on the local network).
- **Ask first.** A device you haven't met has to be accepted, and both screens show the same 4-digit
  code so you know it's really your phone. Tick "Always accept" once and that device is trusted.
  Receive modes: Ask me, Trusted only, Off.
- **Fastest link wins.** Before sending, each available link gets a short speed test. The internet
  is used when it measures faster than the local network, and not otherwise.
- **Resumable.** Files travel in 512 KB chunks. If the connection drops, only the missing chunks are
  sent next time.
- **Everything is encrypted** (TLS 1.3 with certificates pinned by fingerprint).
- **Choose the connection mode.** The laptop Settings screen now provides **Automatic LAN** and
  **Android Emulator (ADB)**. Use Automatic LAN for a physical phone/tablet on the same network.
  The ADB option is for connecting to an Android emulator through an ADB port forward.

## Status

| Piece | State |
| --- | --- |
| `core`: identity, trust, discovery, encrypted resumable transfer, link choice | Built. |
| `laptop`: Windows app (Compose Desktop, light theme) | Built and run. Settings include Automatic LAN and Android Emulator (ADB) connection modes; the selected mode is saved between launches. A Windows installer was packaged successfully as `ProxSh-0.1.0.exe` using `gradle :laptop:packageExe`. |
| `android`: phone app (Kotlin, Android 10+) | Debug APK builds with `gradle :android:assembleDebug`. A Samsung Galaxy Tab A7 was detected through ADB; installation was attempted, but successful installation has not yet been confirmed. |
| Emulator connection | ADB port forwarding was configured and the laptop could reach the forwarded local port. Select **Android Emulator (ADB)** in laptop Settings when using this route. |
| Hotspot joining, Bluetooth discovery, internet route | Link choice is built and tested with simulated links. Native Bluetooth discovery/transfer and real internet/hotspot routing are not confirmed as implemented. Pairing devices over Bluetooth alone does not make ProxSh use Bluetooth. |
| Switching links in the middle of a transfer | Not built. It re-measures and switches after a dropped connection. |
| Windows installer execution | The EXE package was generated. It is unsigned; Windows may show an unknown-publisher warning or block it depending on system security settings. |

## Layout

```text
core/        Kotlin library shared by both apps
laptop/      Windows app
android/     Phone app
website/     Home page and downloads
desktop/     First prototype (Node.js, QR pairing). Kept for reference.
docs/        Design notes, competitor study, HTTP API of the prototype
scripts/     Build environment for Windows
signaling/   Placeholder for the internet-mode helper (later)
mobile/      Old placeholder, replaced by android/
```

## Build and run (Windows)

Needs JDK 21 and Gradle (the wrapper downloads Gradle for you). The phone app also needs the Android
SDK (platform 37, build-tools 36.1) with its licence accepted; set `ANDROID_HOME`. `scripts/env.ps1`
points the tools and caches at one folder so nothing lands on the C drive:

```powershell
. .\scripts\env.ps1           # edit the two paths at the top if yours differ
.\gradlew.bat :core:test      # run the core tests
.\gradlew.bat :laptop:run     # start the laptop app
.\gradlew.bat :laptop:preview # render the window offscreen to laptop/build/previews
.\gradlew.bat :laptop:packageExe # build the Windows installer
.\gradlew.bat :android:assembleDebug # build the phone app (android/build/outputs/apk/debug)
```

### Android emulator connection

1. Start the Android emulator and the laptop app.
2. Forward the laptop's listening port to the emulator using ADB. For the port used during development:

```powershell
adb forward tcp:53319 tcp:53317
```

3. In the laptop app, open Settings and select **Android Emulator (ADB)**.
4. For a physical Android device, select **Automatic LAN** instead.

The ADB forwarding port may need to be adjusted if the laptop's listening port changes.

## Documents

- [Design notes](docs/design.md)
- [What the open-source apps do and where ProxSh can beat them](docs/competitor-code-notes.md)

## Licence

Not chosen yet.
