# ProxSh Android app (Sprint 2–4)

Not started yet. Until it exists, phones use the browser page served by the
laptop app in `../desktop`.

## Planned features

- Appears in the Android Share menu, with a one-tap shortcut per paired laptop (FR-8)
- Notification with **Share to WhatsApp** when a file arrives from the laptop (FR-9)
- Remembers paired laptops (FR-10)
- Starts a local-only hotspot when there is no shared Wi-Fi (FR-12, Sprint 3)
- Bluetooth Low Energy discovery and small-item fallback (FR-14, FR-15, Sprint 4)

## Planned stack

Flutter (Dart) or Kotlin — decide in Sprint 2 planning. Must target Android 8.0+
(API 26) for the local-only hotspot API.

It talks to the laptop over the same HTTP API the browser page uses — see
[`../docs/api.md`](../docs/api.md).
