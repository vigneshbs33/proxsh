'use strict';

const fs = require('node:fs');
const fsp = require('node:fs/promises');
const http = require('node:http');
const path = require('node:path');
const crypto = require('node:crypto');
const { Transform } = require('node:stream');
const { pipeline } = require('node:stream/promises');
const QRCode = require('qrcode');

const { sanitizeFileName, uniquePath } = require('./files');
const { lanAddresses, phoneUrl } = require('./network');
const system = require('./system');
const { version } = require('../package.json');

const PUBLIC_DIR = path.join(__dirname, '..', 'public');
const MAX_TEXT_BYTES = 1024 * 1024; // 1 MB of text is plenty for a clipboard
const HISTORY_LIMIT = 50;
const PROGRESS_EVERY_MS = 250;
const PING_EVERY_MS = 20000;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/**
 * Creates the ProxSh HTTP server.
 *
 *   phone  --POST /api/upload, /api/text-->  laptop (Downloads/ProxSh, clipboard)
 *   laptop --POST /api/send/file, /api/send/text--> outbox --GET /api/outbox/:id--> phone
 *
 * Both sides get live updates from GET /api/events (Server-Sent Events).
 * Every /api request must carry the pairing key from the QR code.
 *
 * deps lets tests swap out OS side effects (clipboard, file explorer).
 */
function createProxshServer(config, deps = {}) {
  const os = { ...system, ...deps };
  const log = deps.quiet ? () => {} : (...args) => console.log(...args);
  const keyBuffer = Buffer.from(config.key);

  const clients = { phone: new Set(), desk: new Set() };
  const received = []; // phone -> laptop, newest first
  const outbox = []; // laptop -> phone, newest first

  // The outbox only holds files until the phone has had a chance to save them.
  fs.rmSync(config.outboxDir, { recursive: true, force: true });
  fs.mkdirSync(config.outboxDir, { recursive: true });

  // ---------- helpers ----------

  function broadcast(role, event, data) {
    const message = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    for (const res of clients[role]) res.write(message);
  }

  function deskStatus() {
    broadcast('desk', 'status', { phones: clients.phone.size });
  }

  function remember(list, item, onDrop) {
    list.unshift(item);
    while (list.length > HISTORY_LIMIT) {
      const dropped = list.pop();
      if (onDrop) onDrop(dropped);
    }
  }

  /** What the other side is allowed to see (never the laptop's file paths). */
  function publicItem(item) {
    const { filePath, ...rest } = item;
    return rest;
  }

  function sendJson(res, status, body) {
    const data = JSON.stringify(body);
    res.writeHead(status, {
      'Content-Type': 'application/json; charset=utf-8',
      'Content-Length': Buffer.byteLength(data),
      'Cache-Control': 'no-store',
    });
    res.end(data);
  }

  function isLoopback(req) {
    const addr = req.socket.remoteAddress || '';
    return addr === '127.0.0.1' || addr === '::1' || addr === '::ffff:127.0.0.1';
  }

  function authorized(req, url) {
    const given = Buffer.from(String(req.headers['x-proxsh-key'] || url.searchParams.get('k') || ''));
    return given.length === keyBuffer.length && crypto.timingSafeEqual(given, keyBuffer);
  }

  function headerFileName(req) {
    const raw = String(req.headers['x-file-name'] || '');
    try {
      return sanitizeFileName(decodeURIComponent(raw));
    } catch {
      return sanitizeFileName(raw);
    }
  }

  async function readText(req) {
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > MAX_TEXT_BYTES) throw new HttpError(413, 'Text is too long. Send it as a file instead.');
      chunks.push(chunk);
    }
    return Buffer.concat(chunks).toString('utf8');
  }

  /**
   * Streams a request body to a temp file in `dir`, then renames it to a unique
   * name. Calls onProgress(bytesSoFar) at most every PROGRESS_EVERY_MS.
   * Deletes the partial file if the sender disconnects.
   */
  async function receiveFile(req, dir, name, onProgress) {
    const expected = Number(req.headers['content-length'] || 0);
    const tmp = path.join(dir, `.proxsh-${crypto.randomUUID()}.part`);
    let got = 0;
    let lastReport = 0;
    const counter = new Transform({
      transform(chunk, _enc, done) {
        got += chunk.length;
        const now = Date.now();
        if (onProgress && now - lastReport >= PROGRESS_EVERY_MS) {
          lastReport = now;
          onProgress(got);
        }
        done(null, chunk);
      },
    });
    try {
      await pipeline(req, counter, fs.createWriteStream(tmp, { flags: 'wx' }));
      if (expected && got !== expected) throw new Error('size mismatch');
    } catch (err) {
      await fsp.rm(tmp, { force: true });
      throw new HttpError(400, 'Transfer was interrupted. Try again.');
    }
    const finalPath = uniquePath(dir, name);
    await fsp.rename(tmp, finalPath);
    return { filePath: finalPath, size: got };
  }

  async function pairingInfo() {
    const addresses = lanAddresses();
    const urls = addresses.map((a) => ({ ...a, url: phoneUrl(a.address, config.port, config.key) }));
    const qr = urls.length ? await QRCode.toDataURL(urls[0].url, { margin: 1, width: 320 }) : null;
    return { name: config.deviceName, urls, qr };
  }

  // ---------- static pages ----------

  async function serveStatic(res, relative) {
    const target = path.normalize(path.join(PUBLIC_DIR, relative));
    if (!target.startsWith(PUBLIC_DIR + path.sep)) throw new HttpError(404, 'Not found');
    let data;
    try {
      data = await fsp.readFile(target);
    } catch {
      throw new HttpError(404, 'Not found');
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(target)] || 'application/octet-stream',
      'Content-Length': data.length,
      'Cache-Control': 'no-cache',
    });
    res.end(data);
  }

  async function serveDesk(res) {
    const html = await fsp.readFile(path.join(PUBLIC_DIR, 'desk.html'), 'utf8');
    // The laptop page is only served to the laptop itself (loopback), so it is
    // safe to hand it the pairing key. Other websites cannot read this page.
    const boot = JSON.stringify({ key: config.key, name: config.deviceName, version }).replace(/</g, '\\u003c');
    const body = html.replace('"__PROXSH_BOOT__"', boot);
    res.writeHead(200, { 'Content-Type': MIME['.html'], 'Cache-Control': 'no-store' });
    res.end(body);
  }

  // ---------- live events ----------

  function openEvents(req, res, role) {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    });
    res.write('retry: 2000\n\n');
    clients[role].add(res);

    const init =
      role === 'phone'
        ? { name: config.deviceName, items: outbox.map(publicItem) }
        : {
            name: config.deviceName,
            receiveDir: config.receiveDir,
            received: received.map(publicItem),
            sent: outbox.map(publicItem),
            phones: clients.phone.size,
          };
    res.write(`event: init\ndata: ${JSON.stringify(init)}\n\n`);
    if (role === 'phone') deskStatus();

    const ping = setInterval(() => res.write(': ping\n\n'), PING_EVERY_MS);
    req.on('close', () => {
      clearInterval(ping);
      clients[role].delete(res);
      if (role === 'phone') deskStatus();
    });
  }

  // ---------- API handlers ----------

  async function phoneUploadsFile(req, res) {
    const name = headerFileName(req);
    const id = crypto.randomUUID();
    const size = Number(req.headers['content-length'] || 0);
    const started = Date.now();
    broadcast('desk', 'incoming', { id, kind: 'file', name, size, at: started });
    let result;
    try {
      result = await receiveFile(req, config.receiveDir, name, (got) =>
        broadcast('desk', 'progress', { id, received: got, size }),
      );
    } catch (err) {
      broadcast('desk', 'failed', { id });
      throw err;
    }
    const item = {
      id,
      kind: 'file',
      name: path.basename(result.filePath),
      size: result.size,
      at: Date.now(),
      ms: Date.now() - started,
      filePath: result.filePath,
    };
    remember(received, item);
    broadcast('desk', 'received', publicItem(item));
    log(`Received ${item.name} (${item.size} bytes) in ${item.ms} ms`);
    sendJson(res, 200, { ok: true, id, name: item.name, size: item.size });
  }

  async function phoneSendsText(req, res) {
    const text = await readText(req);
    if (!text.trim()) throw new HttpError(400, 'Nothing to send.');
    const copied = await os.copyToClipboard(text);
    const item = { id: crypto.randomUUID(), kind: 'text', text, copied, at: Date.now() };
    remember(received, item);
    broadcast('desk', 'received', item);
    log(`Received text (${text.length} characters)${copied ? ', copied to clipboard' : ''}`);
    sendJson(res, 200, { ok: true, id: item.id, copied });
  }

  function dropOutboxItem(item) {
    if (item.filePath) fs.rm(item.filePath, { force: true }, () => {});
  }

  async function laptopSendsFile(req, res) {
    const name = headerFileName(req);
    const result = await receiveFile(req, config.outboxDir, name);
    const item = {
      id: crypto.randomUUID(),
      kind: 'file',
      name: path.basename(result.filePath),
      size: result.size,
      at: Date.now(),
      filePath: result.filePath,
    };
    remember(outbox, item, dropOutboxItem);
    broadcast('phone', 'item', publicItem(item));
    broadcast('desk', 'sent', publicItem(item));
    sendJson(res, 200, { ok: true, id: item.id, phones: clients.phone.size });
  }

  async function laptopSendsText(req, res) {
    const text = await readText(req);
    if (!text.trim()) throw new HttpError(400, 'Nothing to send.');
    const item = { id: crypto.randomUUID(), kind: 'text', text, at: Date.now() };
    remember(outbox, item, dropOutboxItem);
    broadcast('phone', 'item', item);
    broadcast('desk', 'sent', item);
    sendJson(res, 200, { ok: true, id: item.id, phones: clients.phone.size });
  }

  async function phoneDownloads(res, id) {
    const item = outbox.find((i) => i.id === id && i.kind === 'file');
    if (!item) throw new HttpError(404, 'That file is no longer available. Send it again from the laptop.');
    const stat = await fsp.stat(item.filePath);
    const ascii = item.name.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
    res.writeHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Length': stat.size,
      'Content-Disposition': `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(item.name)}`,
      'Cache-Control': 'no-store',
    });
    await pipeline(fs.createReadStream(item.filePath), res);
  }

  async function laptopOpens(req, res, id, action) {
    if (!isLoopback(req)) throw new HttpError(403, 'Only the laptop can open files.');
    const item = received.find((i) => i.id === id && i.kind === 'file');
    if (!item) throw new HttpError(404, 'File not found.');
    if (!fs.existsSync(item.filePath)) throw new HttpError(404, 'The file was moved or deleted.');
    await (action === 'reveal' ? os.revealPath(item.filePath) : os.openPath(item.filePath));
    sendJson(res, 200, { ok: true });
  }

  // ---------- router ----------

  async function route(req, res) {
    const url = new URL(req.url, 'http://proxsh.local');
    const p = url.pathname;
    const method = req.method;

    if (method === 'GET' && (p === '/' || p === '/index.html')) return serveStatic(res, 'phone.html');
    if (method === 'GET' && p.startsWith('/static/')) return serveStatic(res, p.slice('/static/'.length));
    if (method === 'GET' && p === '/desk') {
      if (!isLoopback(req)) throw new HttpError(403, 'The laptop page only opens on the laptop itself.');
      return serveDesk(res);
    }
    if (!p.startsWith('/api/')) throw new HttpError(404, 'Not found');
    if (!authorized(req, url)) throw new HttpError(401, 'Wrong or missing pairing key. Scan the QR code on the laptop again.');

    if (method === 'GET' && p === '/api/info') return sendJson(res, 200, { name: config.deviceName, version });
    if (method === 'GET' && p === '/api/pairing') return sendJson(res, 200, await pairingInfo());
    if (method === 'GET' && p === '/api/events') {
      const role = url.searchParams.get('role') === 'desk' ? 'desk' : 'phone';
      return openEvents(req, res, role);
    }
    if (method === 'POST' && p === '/api/upload') return phoneUploadsFile(req, res);
    if (method === 'POST' && p === '/api/text') return phoneSendsText(req, res);
    if (method === 'POST' && p === '/api/send/file') return laptopSendsFile(req, res);
    if (method === 'POST' && p === '/api/send/text') return laptopSendsText(req, res);

    let m = p.match(/^\/api\/outbox\/([\w-]+)$/);
    if (method === 'GET' && m) return phoneDownloads(res, m[1]);
    m = p.match(/^\/api\/(open|reveal)\/([\w-]+)$/);
    if (method === 'POST' && m) return laptopOpens(req, res, m[2], m[1]);

    throw new HttpError(404, 'Not found');
  }

  const server = http.createServer(async (req, res) => {
    try {
      await route(req, res);
    } catch (err) {
      if (!(err instanceof HttpError) && !deps.quiet) console.error(err);
      if (res.headersSent) {
        res.destroy();
      } else {
        // Drain whatever the sender is still uploading so the error reaches them.
        req.resume();
        sendJson(res, err.status || 500, { error: err instanceof HttpError ? err.message : 'Something went wrong on the laptop.' });
      }
    }
  });

  // Big files can take minutes; Node's default 5-minute request timeout would cut them off.
  server.requestTimeout = 0;

  /** Ends live event streams so server.close() can finish. */
  server.stopAll = () => {
    for (const set of Object.values(clients)) for (const res of set) res.end();
    server.closeAllConnections?.();
  };

  return server;
}

module.exports = { createProxshServer };
