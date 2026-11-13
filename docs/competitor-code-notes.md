# What the open-source apps do, and where ProxSh can beat them

Written 2026-11-13 after reading the source of six projects (shallow clones in
`D:\vignesh\tools\_study`, read only, nothing run). Claims marked **[checked]**
were confirmed by reading the code directly; the rest come from a reader's
report and should be re-checked before we depend on them.

## Licenses decide how we reuse them

| Project | License | What we may do |
| --- | --- | --- |
| [LocalSend](https://github.com/localsend/localsend) | Apache-2.0 | Adapt code and protocol, keep the notice |
| [Google Nearby](https://github.com/google/nearby) | Apache-2.0 | Port logic and Windows snippets, keep Google's headers |
| [NearDrop](https://github.com/grishka/NearDrop) | Unlicense | Free to learn from and copy |
| [PairDrop](https://github.com/schlagmichdoch/PairDrop), [KDE Connect Android](https://github.com/KDE/kdeconnect-android), [rquickshare](https://github.com/Martichou/rquickshare) | GPL | Ideas only. Copying code would make ProxSh GPL too. |

## What each one does

**LocalSend** (Dart and Rust, protocol spec in [localsend/protocol](https://github.com/localsend/protocol))
- Finds devices with a UDP multicast announcement (`224.0.0.167`, port 53317); if multicast fails it tries every address on the network. **[checked]**
- HTTPS with a self-signed certificate; the device fingerprint is the SHA-256 of that certificate. **[checked]**
- Flow: `prepare-upload` (file details) → receiver accepts → one `upload` per file with a token; optional SHA-256 check; `cancel`. **[checked]**
- Weaknesses: LAN only; the spec has no resume step; one POST per file.
- Its most-voted open requests are Wi-Fi Direct, Wi-Fi Aware, Bluetooth discovery, Bluetooth transfer, running in the background on Android and Tailscale detection. **[checked]**

**Google Nearby** (C++, the engine behind Quick Share)
- Finds devices over BLE, Bluetooth or mDNS, connects on the best link both share, then upgrades to a faster one (`connections/implementation/bwu_manager.cc`).
- The upgrade keeps the old channel open, sends the new link's credentials over it, switches, then closes the old one. If an upgrade fails it tries the next medium with backoff.
- **The choice is a fixed priority list and never measures speed.** **[checked]** Order: LAN, Wi-Fi Aware, Wi-Fi Direct, hotspot, WebRTC, Bluetooth, BLE (`connections/medium_selector.h`).
- Windows (`internal/platform/implementation/windows/`): BLE through WinRT advertising and watching; hotspot joined through the Wi-Fi API (`WlanConnect` with a generated profile, then wait for an IP). **[checked]**
- Windows **cannot reliably host a Wi-Fi Direct group**: Google disabled it (reported 20 to 60 percent connect failures) and the phone hosts instead. **[checked]**

**PairDrop** (JavaScript, WebRTC)
- Groups devices by public IP, plus stored secrets for paired devices and a 6-digit single-use code.
- Weaknesses: the receiver holds the whole file in memory, no resume, waits for an acknowledgement after every 1 MB, only a size and name check, relay fallback not end-to-end encrypted.

**KDE Connect Android** (Kotlin)
- UDP broadcast (port 1716) plus mDNS, then TLS; each device has its own certificate that the other remembers on first use, with a short verification key to compare on both screens.
- A small control message first, then the file on a separate encrypted connection.
- A foreground service that restarts at boot and when the network changes.
- Weaknesses: no resume, one stream, one file at a time, LAN only, clipboard sync needs an `adb` hack, no battery-optimization guidance.
- Newer Android asks for a local-network permission before discovery. **[checked]** (`ACCESS_LOCAL_NETWORK`)

**rquickshare and NearDrop** (Quick Share receivers for Linux and Mac)
- Let any Android phone's built-in Share menu send to a desktop with no phone app.
- Wi-Fi LAN only, over mDNS with an encrypted handshake. **[checked]** (only `WifiLan` is offered); no Windows support in either. **[checked]**
- Reverse engineered, so Google can change it without notice.

## What ProxSh takes

1. LocalSend's announce → accept → upload-with-token shape, with certificate fingerprints as identity.
2. KDE Connect's trust model: a certificate per device remembered on first use, a short code to compare, an easy revoke.
3. PairDrop's layered discovery and per-device auto-accept.
4. Google's link handover: keep the old channel until the new one works, fall back with backoff, let the phone host the hotspot and the laptop only join.
5. Windows hotspot joining with `WlanConnect` and a generated profile, restoring the user's own Wi-Fi afterwards.

## Where ProxSh beats all of them

| Gap | Evidence | ProxSh |
| --- | --- | --- |
| Nobody measures link speed | Google picks by a fixed list; the others use one link | Measure each link and use the fastest, re-check every 5 seconds |
| Nobody resumes | None of the four we read does | Chunks with hashes and offsets, resume after any drop |
| One transport each | LocalSend, KDE, PairDrop use one | LAN, hotspot, internet in one app |
| Windows is a second-class citizen | Google's own Windows code has no Wi-Fi Direct host | Phone hosts, laptop joins |
| Users ask for Bluetooth discovery and background receiving | LocalSend's top open issues | Built in |
| Whole file in memory | PairDrop | Stream to disk |

## Open risks

- Joining a phone's hotspot disconnects the laptop from its internet. Prefer the same-LAN case first and say so before switching.
- Android's local-network permission needs a clear screen in the phone app.
- The Quick Share receiver is a stretch goal: about 1,500 to 2,500 lines for the receive side, a week for the handshake alone, and untested on Windows.
- Everything here is read from source, not run. The Bluetooth and hotspot path still needs a test on a real phone and laptop.
