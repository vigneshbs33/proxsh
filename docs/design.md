# ProxSh design notes

The full Software Requirements Specification lives in the team's shared SRS doc.
This file keeps the design decisions close to the code.

## Core idea

Use Bluetooth to find and pair the devices, Wi-Fi to move the bytes, and the
internet only when the devices are far apart. Quick Share and AirDrop work the
same way.

| Link | When it is used | Real speed | 100 MB video |
| --- | --- | --- | --- |
| Same Wi-Fi (LAN) | Both on the same router | 10–40 MB/s | 3–10 s |
| Phone local-only hotspot | No shared Wi-Fi, no internet | 5–30 MB/s | 5–20 s |
| Bluetooth Classic | Wi-Fi off, or tiny items | 0.1–0.2 MB/s | ~10 min |
| Internet (WebRTC, relay fallback) | Devices in different places | Depends | Depends |

ProxSh tries the links at the same time and uses the first one that connects.

## Pairing: no accounts

1. The laptop shows a QR code with its address and a random pairing key.
2. The phone scans it. From then on every request carries that key.
3. Paired devices skip the accept prompt. Unknown devices must be accepted.

There is no email, password, cloud copy, or central database.

## User flows

- **Phone → laptop:** WhatsApp → Share → *My laptop*. The file lands in
  `Downloads/ProxSh`; text goes to the laptop clipboard.
- **Laptop → WhatsApp:** drag a file onto the ProxSh window (or Ctrl+V). The
  phone gets it, with a *Share to WhatsApp* button (Android app, Sprint 2).
- **No app on the phone:** scan the QR code and use the browser page.

## Components

| Folder | What it is | Sprint |
| --- | --- | --- |
| `desktop/` | Laptop receiver (Node.js): HTTP server, phone page, laptop page | 1 |
| `mobile/` | Android app: Share menu, hotspot, Bluetooth | 2–4 |
| `signaling/` | Small server that lets devices find each other over the internet | 5 |

## Related work

LocalSend, PairDrop/Snapdrop, KDE Connect, Google Quick Share, Microsoft Phone
Link. ProxSh's difference: automatic choice across every link (including
Bluetooth and internet) plus a no-install browser mode.
