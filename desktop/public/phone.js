'use strict';

/* Phone page: send files and text to the laptop, and get what the laptop sends back. */

const $ = (id) => document.getElementById(id);

// The QR code opens /?k=<pairing key>. Remember it so a bookmark without it still works.
const key = (() => {
  const fromUrl = new URLSearchParams(location.search).get('k');
  try {
    if (fromUrl) localStorage.setItem('proxsh-key', fromUrl);
    return fromUrl || localStorage.getItem('proxsh-key');
  } catch {
    return fromUrl;
  }
})();
const auth = { 'X-ProxSh-Key': key || '' };
const withKey = (path) => `${path}${path.includes('?') ? '&' : '?'}k=${encodeURIComponent(key || '')}`;

function showError(message) {
  $('error').textContent = message;
  $('error').hidden = !message;
}

function setStatus(text, kind) {
  $('status').textContent = text;
  $('status').className = `pill ${kind}`;
}

// ---------- live connection ----------

const seen = new Set();

function connect() {
  if (!key) {
    setStatus('Not paired', 'bad');
    $('target').textContent = 'Scan the QR code shown on your laptop to connect.';
    return;
  }
  const events = new EventSource(withKey('/api/events?role=phone'));
  events.addEventListener('open', () => { setStatus('Connected', 'ok'); showError(''); });
  events.addEventListener('error', () => setStatus('Reconnecting…', 'wait'));
  events.addEventListener('init', (e) => {
    const data = JSON.parse(e.data);
    $('target').textContent = `Sending to ${data.name}`;
    // Start from the laptop app's current state (it may have restarted).
    seen.clear();
    $('incoming').replaceChildren();
    $('incomingEmpty').hidden = false;
    // Oldest first so the newest ends up on top.
    for (const item of data.items.slice().reverse()) addIncoming(item);
  });
  events.addEventListener('item', (e) => addIncoming(JSON.parse(e.data)));
}

// ---------- things the laptop sent ----------

function addIncoming(item) {
  if (seen.has(item.id)) return;
  seen.add(item.id);
  $('incomingEmpty').hidden = true;

  let row;
  if (item.kind === 'text') {
    const copyBtn = el('button', { class: 'small', type: 'button' }, 'Copy');
    copyBtn.addEventListener('click', async () => {
      copyBtn.textContent = (await copyText(item.text)) ? 'Copied' : 'Select and copy';
    });
    row = el('li', { class: 'row' },
      el('div', { class: 'icon' }, 'txt'),
      el('div', { class: 'body' }, el('div', { class: 'text' }, item.text), el('div', { class: 'meta' }, formatTime(item.at))),
      el('div', { class: 'actions' }, copyBtn),
    );
  } else {
    const href = withKey(`/api/outbox/${item.id}`);
    const actions = [el('a', { class: 'button small', href, download: item.name }, 'Save')];
    // The share sheet (WhatsApp etc.) only works on https pages; the Android app adds this in Sprint 2.
    if (window.isSecureContext && navigator.canShare) {
      const shareBtn = el('button', { class: 'small', type: 'button' }, 'Share');
      shareBtn.addEventListener('click', () => shareFile(item, href).catch((err) => showError(err.message)));
      actions.push(shareBtn);
    }
    row = el('li', { class: 'row' },
      el('div', { class: 'icon' }, fileBadge(item.name)),
      el('div', { class: 'body' },
        el('div', { class: 'name', title: item.name }, item.name),
        el('div', { class: 'meta' }, `${formatBytes(item.size)} · ${formatTime(item.at)}`)),
      el('div', { class: 'actions' }, actions),
    );
  }
  $('incoming').prepend(row);
}

async function shareFile(item, href) {
  const blob = await (await fetch(href)).blob();
  const file = new File([blob], item.name, { type: blob.type });
  if (!navigator.canShare({ files: [file] })) throw new Error('This phone cannot share that file type.');
  await navigator.share({ files: [file] });
}

// ---------- sending to the laptop ----------

let queue = Promise.resolve();

function sendFiles(files) {
  $('outgoingSection').hidden = false;
  for (const file of files) {
    const ui = progressRow(file.name, file.size);
    $('outgoing').prepend(ui.row);
    // One file at a time: same total speed, and each one finishes sooner.
    queue = queue.then(async () => {
      try {
        await uploadWithProgress('/api/upload', file, { ...auth, 'X-File-Name': encodeURIComponent(file.name) }, (s, t) => ui.update(s, t));
        ui.done();
      } catch (err) {
        ui.fail(err.message);
      }
    });
  }
}

$('files').addEventListener('change', (e) => {
  if (!key) return showError('Scan the QR code on your laptop first.');
  sendFiles([...e.target.files]);
  e.target.value = '';
});

$('textForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = $('text').value;
  if (!text.trim()) return showError('Type something to send first.');
  if (!key) return showError('Scan the QR code on your laptop first.');
  showError('');
  $('outgoingSection').hidden = false;
  const meta = el('div', { class: 'meta' }, 'Sending…');
  $('outgoing').prepend(el('li', { class: 'row' },
    el('div', { class: 'icon' }, 'txt'),
    el('div', { class: 'body' }, el('div', { class: 'text' }, text), meta)));
  try {
    const reply = await postText('/api/text', text, auth);
    meta.className = 'meta ok';
    meta.textContent = reply.copied ? 'Sent · copied to laptop clipboard' : 'Sent';
    $('text').value = '';
  } catch (err) {
    meta.className = 'meta bad';
    meta.textContent = err.message;
  }
});

$('text').addEventListener('input', () => showError(''));

connect();
