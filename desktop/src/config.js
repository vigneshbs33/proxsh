'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');

const DEFAULT_PORT = 53317;

/**
 * Loads (or creates on first run) this laptop's ProxSh settings.
 * The pairing key is saved in data/config.json so a paired phone keeps working
 * after a restart. Delete that file to unpair every phone.
 */
function loadConfig(options = {}) {
  const dataDir = options.dataDir || process.env.PROXSH_DATA_DIR || path.join(__dirname, '..', 'data');
  const receiveDir =
    options.receiveDir || process.env.PROXSH_RECEIVE_DIR || path.join(os.homedir(), 'Downloads', 'ProxSh');
  const port = options.port !== undefined ? Number(options.port) : Number(process.env.PROXSH_PORT || DEFAULT_PORT);

  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(receiveDir, { recursive: true });

  const file = path.join(dataDir, 'config.json');
  let saved = {};
  try {
    saved = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    // First run or unreadable file: create fresh settings below.
  }

  const settings = {
    deviceName: saved.deviceName || os.hostname(),
    key: saved.key || crypto.randomBytes(16).toString('hex'),
  };
  if (saved.deviceName !== settings.deviceName || saved.key !== settings.key) {
    fs.writeFileSync(file, JSON.stringify(settings, null, 2));
  }

  return {
    ...settings,
    port,
    dataDir,
    receiveDir,
    outboxDir: path.join(dataDir, 'outbox'),
  };
}

module.exports = { loadConfig, DEFAULT_PORT };
