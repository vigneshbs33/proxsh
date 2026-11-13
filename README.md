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

## Status

| Piece | State |
| --- | --- |
| `core`: identity, trust, discovery, encrypted resumable transfer, link choice | Built. 22 automated tests pass, including discovery over this machine's real network. |
| `laptop`: Windows app (Compose Desktop, light theme) | Built. Starts and listens (checked through Gradle), and all screens render offscreen with two real nodes. Not yet used by hand. |
| `android`: phone app | Not started. Needs the Android SDK. |
| Hotspot joining, Bluetooth discovery, internet route | Link choice is built and tested with simulated links. Real hotspot, Bluetooth and internet links are not built. |
| Switching links in the middle of a transfer | Not built. It re-measures and switches after a dropped connection. |
| Windows installer | Configured. The packaged `ProxSh.exe` builds (about 130 MB with its own Java), but it is unsigned and Windows Application Control blocked it on the development laptop. Shipping to people needs a code-signing certificate or Microsoft Store packaging. |

Nothing has been tested with a real phone.

## Layout

```
core/        Kotlin library shared by both apps
laptop/      Windows app
desktop/     First prototype (Node.js, QR pairing). Kept for reference.
docs/        Design notes, competitor study, HTTP API of the prototype
scripts/     Build environment for Windows
mobile/, signaling/   Placeholders for later work
```

## Build and run (Windows)

Needs JDK 21 and Gradle (the wrapper downloads Gradle for you). `scripts/env.ps1` points the tools
and caches at one folder so nothing lands on the C drive:

```powershell
. .\scripts\env.ps1          # edit the two paths at the top if yours differ
gradle :core:test            # run the core tests
gradle :laptop:run           # start the laptop app
gradle :laptop:preview       # render the window offscreen to laptop/build/previews
gradle :laptop:packageExe    # build the Windows installer
```

## Documents

- [Design notes](docs/design.md)
- [What the open-source apps do and where ProxSh can beat them](docs/competitor-code-notes.md)

## Licence

Not chosen yet.
