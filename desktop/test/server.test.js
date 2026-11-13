'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const http = require('node:http');
const { loadConfig } = require('../src/config');
const { createProxshServer } = require('../src/server');

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

/** Starts a server on a random port with temp folders and a fake clipboard. */
async function startServer(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'proxsh-test-'));
  const config = loadConfig({ dataDir: path.join(root, 'data'), receiveDir: path.join(root, 'received'), port: 0 });
  const clipboard = [];
  const server = createProxshServer(config, {
    quiet: true,
    copyToClipboard: async (text) => { clipboard.push(text); return true; },
    openPath: async () => true,
    revealPath: async () => true,
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  t.after(() => {
    server.stopAll();
    server.close();
    fs.rmSync(root, { recursive: true, force: true });
  });
  return { base, config, clipboard, key: config.key };
}

/** Reads Server-Sent Events until `until(events)` is true. */
function collectEvents(url, until) {
  return new Promise((resolve, reject) => {
    const events = [];
    const req = http.get(url, (res) => {
      let buffer = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => {
        buffer += chunk;
        let idx;
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);
          const event = /^event: (.+)$/m.exec(block)?.[1];
          const data = /^data: (.+)$/m.exec(block)?.[1];
          if (event) events.push({ event, data: JSON.parse(data) });
        }
        if (until(events)) {
          req.destroy();
          resolve(events);
        }
      });
    });
    req.on('error', (err) => (err.code === 'ECONNRESET' ? null : reject(err)));
  });
}

test('rejects API calls without the pairing key', async (t) => {
  const { base } = await startServer(t);
  const res = await fetch(`${base}/api/info`);
  assert.equal(res.status, 401);
  const wrong = await fetch(`${base}/api/info`, { headers: { 'X-ProxSh-Key': 'nope' } });
  assert.equal(wrong.status, 401);
});

test('serves the phone page and the laptop page with the key only on loopback', async (t) => {
  const { base, key } = await startServer(t);
  const phone = await fetch(`${base}/`);
  assert.equal(phone.status, 200);
  assert.match(await phone.text(), /Pick photos or files/);
  const desk = await (await fetch(`${base}/desk`)).text();
  assert.ok(desk.includes(key), 'laptop page carries the key');
  assert.ok(!desk.includes('__PROXSH_BOOT__'));
});

test('phone uploads a file byte-for-byte into the receive folder', async (t) => {
  const { base, key, config } = await startServer(t);
  const data = crypto.randomBytes(3 * 1024 * 1024 + 7);
  const res = await fetch(`${base}/api/upload`, {
    method: 'POST',
    headers: { 'X-ProxSh-Key': key, 'X-File-Name': encodeURIComponent('హాయ్ photo.jpg') },
    body: data,
  });
  assert.equal(res.status, 200);
  const reply = await res.json();
  assert.equal(reply.name, 'హాయ్ photo.jpg');
  const saved = fs.readFileSync(path.join(config.receiveDir, reply.name));
  assert.equal(sha256(saved), sha256(data));

  // Same name again does not overwrite.
  const again = await (await fetch(`${base}/api/upload`, {
    method: 'POST', headers: { 'X-ProxSh-Key': key, 'X-File-Name': encodeURIComponent('హాయ్ photo.jpg') }, body: 'x',
  })).json();
  assert.equal(again.name, 'హాయ్ photo (1).jpg');
  // No temp files left behind.
  assert.deepEqual(fs.readdirSync(config.receiveDir).filter((f) => f.endsWith('.part')), []);
});

test('upload names cannot escape the receive folder', async (t) => {
  const { base, key, config } = await startServer(t);
  const reply = await (await fetch(`${base}/api/upload`, {
    method: 'POST', headers: { 'X-ProxSh-Key': key, 'X-File-Name': encodeURIComponent('../../escape.txt') }, body: 'hi',
  })).json();
  assert.equal(reply.name, 'escape.txt');
  assert.ok(fs.existsSync(path.join(config.receiveDir, 'escape.txt')));
});

test('phone text goes to the laptop clipboard', async (t) => {
  const { base, key, clipboard } = await startServer(t);
  const reply = await (await fetch(`${base}/api/text`, {
    method: 'POST', headers: { 'X-ProxSh-Key': key, 'Content-Type': 'text/plain; charset=utf-8' }, body: 'OTP 482913 😀',
  })).json();
  assert.equal(reply.copied, true);
  assert.deepEqual(clipboard, ['OTP 482913 😀']);
});

test('laptop sends a file and text; the phone is told live and can download the file', async (t) => {
  const { base, key } = await startServer(t);
  const live = collectEvents(`${base}/api/events?role=phone&k=${key}`, (ev) => ev.filter((e) => e.event === 'item').length >= 2);
  await new Promise((r) => setTimeout(r, 100)); // let the phone connect first

  const data = crypto.randomBytes(512 * 1024);
  const fileReply = await (await fetch(`${base}/api/send/file`, {
    method: 'POST', headers: { 'X-ProxSh-Key': key, 'X-File-Name': encodeURIComponent('report final.docx') }, body: data,
  })).json();
  assert.equal(fileReply.phones, 1);
  await fetch(`${base}/api/send/text`, { method: 'POST', headers: { 'X-ProxSh-Key': key }, body: 'meet.google.com/abc' });

  const events = await live;
  assert.equal(events[0].event, 'init');
  const items = events.filter((e) => e.event === 'item').map((e) => e.data);
  assert.equal(items[0].name, 'report final.docx');
  assert.equal(items[0].filePath, undefined, 'laptop paths are never sent to the phone');
  assert.equal(items[1].text, 'meet.google.com/abc');

  const download = await fetch(`${base}/api/outbox/${items[0].id}?k=${key}`);
  assert.equal(download.status, 200);
  assert.match(download.headers.get('content-disposition'), /report%20final\.docx/);
  assert.equal(sha256(Buffer.from(await download.arrayBuffer())), sha256(data));
});

test('the laptop page sees phone uploads arrive live', async (t) => {
  const { base, key } = await startServer(t);
  const live = collectEvents(`${base}/api/events?role=desk&k=${key}`, (ev) => ev.some((e) => e.event === 'received'));
  await new Promise((r) => setTimeout(r, 100));
  await fetch(`${base}/api/upload`, { method: 'POST', headers: { 'X-ProxSh-Key': key, 'X-File-Name': 'a.txt' }, body: 'hello' });
  const events = await live;
  assert.ok(events.some((e) => e.event === 'incoming' && e.data.name === 'a.txt'));
  const done = events.find((e) => e.event === 'received');
  assert.equal(done.data.size, 5);
  assert.equal(done.data.filePath, undefined);
});
