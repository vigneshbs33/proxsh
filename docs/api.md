# ProxSh HTTP API (v0.1)

The laptop app listens on port **53317** on every network interface.
The phone page and the future Android app both use this API.

## Authentication

Every `/api/*` request must carry the pairing key from the QR code, either as
the header `X-ProxSh-Key: <key>` or the query parameter `?k=<key>`.
A missing or wrong key returns `401`.

The key is stored in `desktop/data/config.json`. Delete that file to unpair
every phone (a new key and QR code are made on the next start).

## Endpoints

| Method and path | Who calls it | Purpose |
| --- | --- | --- |
| `GET /` | Phone | Phone web page |
| `GET /desk` | Laptop only (loopback) | Laptop web page, with the key built in |
| `GET /api/info` | Either | `{ name, version }` |
| `GET /api/pairing` | Laptop | Current addresses and QR code (changes when Wi-Fi changes) |
| `GET /api/events?role=phone\|desk` | Either | Live updates (Server-Sent Events) |
| `POST /api/upload` | Phone | Send a file to the laptop |
| `POST /api/text` | Phone | Send text; the laptop copies it to its clipboard |
| `POST /api/send/file` | Laptop | Send a file to the phone |
| `POST /api/send/text` | Laptop | Send text to the phone |
| `GET /api/outbox/{id}` | Phone | Download a file the laptop sent |
| `POST /api/open/{id}`, `POST /api/reveal/{id}` | Laptop only | Open a received file, or show it in its folder |

### Sending a file

The body is the raw file bytes (no multipart). The name goes in a header,
URL-encoded so any language works:

```
POST /api/upload
X-ProxSh-Key: <key>
X-File-Name: IMG-2045%20from%20WhatsApp.jpg
Content-Length: 5242880

<bytes>
```

Reply: `{ "ok": true, "id": "…", "name": "IMG-2045 from WhatsApp.jpg", "size": 5242880 }`.
The laptop cleans the name (no folders, no characters Windows rejects) and adds
` (1)`, ` (2)` instead of overwriting an existing file. Interrupted uploads are
deleted, never left half-written.

### Live events

| Event | Sent to | Data |
| --- | --- | --- |
| `init` | Both, on connect | Current lists; laptop name |
| `item` | Phone | Something the laptop sent |
| `status` | Laptop | `{ phones }` — how many phones are connected |
| `incoming`, `progress`, `received`, `failed` | Laptop | A phone upload starting, moving, done, or interrupted |
| `sent` | Laptop | Something the laptop sent to the phone |

Laptop file paths are never sent to the phone.
