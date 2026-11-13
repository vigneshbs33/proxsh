'use strict';

/* Shared helpers for the phone page and the laptop page. */

function formatBytes(n) {
  if (!Number.isFinite(n) || n < 0) return '';
  if (n < 1024) return `${n} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = n / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return `${v < 10 ? v.toFixed(1) : Math.round(v)} ${units[i]}`;
}

function formatSpeed(bytes, ms) {
  if (!ms || ms <= 0) return '';
  return `${formatBytes(Math.round((bytes / ms) * 1000))}/s`;
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/** Tiny element builder: el('div', {class: 'row'}, child, 'text'). */
function el(tag, attrs, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (k === 'class') node.className = v;
    else node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === undefined || c === null || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

/** Short label for the file-type badge, e.g. "jpg", "pdf". */
function fileBadge(name) {
  const dot = name.lastIndexOf('.');
  const ext = dot > 0 ? name.slice(dot + 1, dot + 5) : '';
  return ext || 'file';
}

/** Copies text; works on plain http pages where navigator.clipboard is blocked. */
async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    try { await navigator.clipboard.writeText(text); return true; } catch { /* fall through */ }
  }
  const area = el('textarea', { style: 'position:fixed;top:-1000px;opacity:0' });
  area.value = text;
  document.body.append(area);
  area.select();
  let ok = false;
  try { ok = document.execCommand('copy'); } catch { ok = false; }
  area.remove();
  return ok;
}

/**
 * Uploads a File/Blob with progress (fetch cannot report upload progress, XHR can).
 * Resolves with the JSON reply; rejects with an Error carrying the server's message.
 */
function uploadWithProgress(url, body, headers, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    for (const [k, v] of Object.entries(headers)) xhr.setRequestHeader(k, v);
    xhr.upload.onprogress = (e) => { if (e.lengthComputable && onProgress) onProgress(e.loaded, e.total); };
    xhr.onload = () => {
      let data = {};
      try { data = JSON.parse(xhr.responseText); } catch { /* not JSON */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(data);
      else reject(new Error(data.error || `Failed (HTTP ${xhr.status})`));
    };
    xhr.onerror = () => reject(new Error('Lost connection to the laptop.'));
    xhr.onabort = () => reject(new Error('Cancelled.'));
    xhr.send(body);
  });
}

async function postText(url, text, headers) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain; charset=utf-8', ...headers },
    body: text,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Failed (HTTP ${res.status})`);
  return data;
}

/** A row with a progress bar, returned with handles to update it. */
function progressRow(name, size) {
  const bar = el('div');
  const meta = el('div', { class: 'meta' }, `${formatBytes(size)} · waiting`);
  const row = el('li', { class: 'row' },
    el('div', { class: 'icon' }, fileBadge(name)),
    el('div', { class: 'body' }, el('div', { class: 'name', title: name }, name), meta, el('div', { class: 'progress' }, bar)),
  );
  const started = performance.now();
  return {
    row,
    update(sent, total) {
      const pct = total ? Math.round((sent / total) * 100) : 0;
      bar.style.width = `${pct}%`;
      meta.textContent = `${formatBytes(total)} · ${pct}% · ${formatSpeed(sent, performance.now() - started)}`;
    },
    done(text) {
      bar.parentElement.remove();
      meta.className = 'meta ok';
      meta.textContent = text || `${formatBytes(size)} · sent in ${((performance.now() - started) / 1000).toFixed(1)} s`;
    },
    fail(message) {
      bar.parentElement.remove();
      meta.className = 'meta bad';
      meta.textContent = message;
    },
  };
}
