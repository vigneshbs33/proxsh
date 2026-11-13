'use strict';

const fs = require('node:fs');
const path = require('node:path');

const WINDOWS_RESERVED = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\..*)?$/i;
const MAX_NAME_LENGTH = 200;

/**
 * Turns a name sent by the other device into a safe file name:
 * no folders (blocks "../" tricks), no characters Windows rejects,
 * no reserved device names, and a sane length.
 */
function sanitizeFileName(name) {
  let base = String(name || '').split(/[\\/]/).pop();
  base = base.replace(/[\u0000-\u001f<>:"|?*]/g, '_').trim().replace(/[. ]+$/, '');
  if (!base || base === '.' || base === '..') base = 'file';
  if (WINDOWS_RESERVED.test(base)) base = `_${base}`;
  if (base.length > MAX_NAME_LENGTH) {
    const ext = path.extname(base).slice(0, 20);
    base = base.slice(0, MAX_NAME_LENGTH - ext.length) + ext;
  }
  return base;
}

/** Returns dir/name, or dir/name (1).ext, (2)... if that file already exists. */
function uniquePath(dir, name) {
  const ext = path.extname(name);
  const stem = name.slice(0, name.length - ext.length);
  let candidate = path.join(dir, name);
  for (let i = 1; fs.existsSync(candidate); i++) {
    candidate = path.join(dir, `${stem} (${i})${ext}`);
  }
  return candidate;
}

module.exports = { sanitizeFileName, uniquePath };
