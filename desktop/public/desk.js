'use strict';

/* Laptop page: shows the pairing QR code, receives from the phone, sends to the phone. */

const $ = (id) => document.getElementById(id);
const boot = window.PROXSH_BOOT || {};
const auth = { 'X-ProxSh-Key': boot.key || '' };
const withKey = (path) => `${path}${path.includes('?') ? '&' : '?'}k=${encodeURIComponent(boot.key || '')}`;

$('deviceName').textContent = boot.name ? `· ${boot.name}` : '';

function showError(message) {
  $('error').textContent = message;
  $('error').hidden = !message;
}

function setStatus(text, kind) {
  $('status').textContent = text;
  $('status').className = `pill ${kind}`;
}

// ---------- pairing QR (refreshes if the laptop switches Wi-Fi or joins a hotspot) ----------

let lastQrUrl = null;

async function refreshPairing() {
  try {
    const res = await fetch('/api/pairing', { headers: auth });
    const data = await res.json();
    const first = data.urls[0];
    if (!first) {
      $('qr').hidden = true;
      $('qrUrl').textContent = '';
      $('qrHelp').textContent = 'No Wi-Fi found. Connect this laptop to Wi-Fi, or turn on your phone hotspot and join it.';
      lastQrUrl = null;
      return;
    }
    if (first.url === lastQrUrl) return;
    lastQrUrl = first.url;
    $('qr').src = data.qr;
    $('qr').hidden = false;
    $('qrUrl').textContent = first.url.replace(/\?k=.*/, '');
    $('qrHelp').textContent = 'Open your phone camera and scan this. Phone and laptop must be on the same Wi-Fi or hotspot.';
    $('addressList').replaceChildren(...data.urls.map((u) => el('li', {}, `${u.url.replace(/\?k=.*/, '')} (${u.name})`)));
    $('otherAddresses').hidden = false;
  } catch {
    // The live connection below reports problems; try again on the next tick.
  }
}

refreshPairing();
setInterval(refreshPairing, 10000);

// ---------- live connection ----------

const incomingRows = new Map(); // id -> progress row while a phone upload is in flight
const seen = new Set();

function phoneStatus(count) {
  if (count > 0) setStatus(count === 1 ? 'Phone connected' : `${count} phones connected`, 'ok');
  else setStatus('Waiting for phone', 'wait');
}

const events = new EventSource(withKey('/api/events?role=desk'));
events.addEventListener('error', () => setStatus('ProxSh stopped', 'bad'));
events.addEventListener('init', (e) => {
  const data = JSON.parse(e.data);
  // Start from the laptop app's current state (it may have restarted).
  seen.clear();
  incomingRows.clear();
  $('received').replaceChildren();
  $('sent').replaceChildren();
  $('receivedEmpty').hidden = false;
  $('sentEmpty').hidden = false;
  $('receiveDir').textContent = data.receiveDir;
  phoneStatus(data.phones);
  for (const item of data.received.slice().reverse()) addReceived(item);
  for (const item of data.sent.slice().reverse()) addSent(item);
});
events.addEventListener('status', (e) => phoneStatus(JSON.parse(e.data).phones));
events.addEventListener('incoming', (e) => {
  const item = JSON.parse(e.data);
  const ui = progressRow(item.name, item.size);
  incomingRows.set(item.id, ui);
  $('receivedEmpty').hidden = true;
  $('received').prepend(ui.row);
});
events.addEventListener('progress', (e) => {
  const p = JSON.parse(e.data);
  incomingRows.get(p.id)?.update(p.received, p.size);
});
events.addEventListener('failed', (e) => {
  const { id } = JSON.parse(e.data);
  incomingRows.get(id)?.fail('Transfer interrupted');
  incomingRows.delete(id);
});
events.addEventListener('received', (e) => addReceived(JSON.parse(e.data)));
events.addEventListener('sent', (e) => addSent(JSON.parse(e.data)));

// ---------- lists ----------

function fileActions(item) {
  const act = (action) => async () => {
    const res = await fetch(`/api/${action}/${item.id}`, { method: 'POST', headers: auth });
    if (!res.ok) showError((await res.json().catch(() => ({}))).error || 'Could not open the file.');
  };
  return el('div', { class: 'actions' },
    el('button', { class: 'small', type: 'button', onclick: act('open') }, 'Open'),
    el('button', { class: 'small', type: 'button', onclick: act('reveal') }, 'Show in folder'));
}

function textRow(item, note) {
  return el('li', { class: 'row' },
    el('div', { class: 'icon' }, 'txt'),
    el('div', { class: 'body' }, el('div', { class: 'text' }, item.text), el('div', { class: 'meta' }, note)),
    el('div', { class: 'actions' },
      el('button', { class: 'small', type: 'button', onclick: (e) => copyText(item.text).then((ok) => { e.target.textContent = ok ? 'Copied' : 'Copy failed'; }) }, 'Copy')));
}

function fileRow(item, meta, actions) {
  return el('li', { class: 'row' },
    el('div', { class: 'icon' }, fileBadge(item.name)),
    el('div', { class: 'body' }, el('div', { class: 'name', title: item.name }, item.name), el('div', { class: 'meta' }, meta)),
    actions);
}

function addReceived(item) {
  if (seen.has(item.id)) return;
  seen.add(item.id);
  $('receivedEmpty').hidden = true;
  const speed = item.ms ? ` · ${formatSpeed(item.size, item.ms)}` : '';
  const row = item.kind === 'text'
    ? textRow(item, `${formatTime(item.at)}${item.copied ? ' · copied to clipboard' : ''}`)
    : fileRow(item, `${formatBytes(item.size)} · ${formatTime(item.at)}${speed}`, fileActions(item));
  const pending = incomingRows.get(item.id);
  if (pending) {
    pending.row.replaceWith(row);
    incomingRows.delete(item.id);
  } else {
    $('received').prepend(row);
  }
}

function addSent(item) {
  if (seen.has(item.id)) return;
  seen.add(item.id);
  $('sentEmpty').hidden = true;
  $('sent').prepend(item.kind === 'text'
    ? textRow(item, formatTime(item.at))
    : fileRow(item, `${formatBytes(item.size)} · ${formatTime(item.at)}`, null));
}

// ---------- sending to the phone ----------

let queue = Promise.resolve();

function sendFiles(files) {
  showError('');
  for (const file of files) {
    queue = queue.then(async () => {
      try {
        const reply = await uploadWithProgress('/api/send/file', file, { ...auth, 'X-File-Name': encodeURIComponent(file.name) });
        if (!reply.phones) showError('Saved for your phone. Open ProxSh on the phone to get it.');
      } catch (err) {
        showError(`${file.name}: ${err.message}`);
      }
    });
  }
}

async function sendText(text) {
  if (!text.trim()) return;
  try {
    const reply = await postText('/api/send/text', text, auth);
    showError(reply.phones ? '' : 'Saved for your phone. Open ProxSh on the phone to get it.');
  } catch (err) {
    showError(err.message);
  }
}

const drop = $('drop');
['dragenter', 'dragover'].forEach((t) => document.addEventListener(t, (e) => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((t) => document.addEventListener(t, (e) => {
  e.preventDefault();
  if (t === 'drop' || e.relatedTarget === null) drop.classList.remove('over');
}));
document.addEventListener('drop', (e) => {
  const files = [...(e.dataTransfer?.files || [])];
  if (files.length) sendFiles(files);
});

document.addEventListener('paste', (e) => {
  if (e.target === $('text')) return; // normal paste into the text box
  const files = [...(e.clipboardData?.files || [])];
  if (files.length) {
    e.preventDefault();
    sendFiles(files);
    return;
  }
  const text = e.clipboardData?.getData('text');
  if (text) {
    e.preventDefault();
    sendText(text);
  }
});

$('files').addEventListener('change', (e) => {
  sendFiles([...e.target.files]);
  e.target.value = '';
});

$('textForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  await sendText($('text').value);
  $('text').value = '';
});

$('text').addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    $('textForm').requestSubmit();
  }
});
