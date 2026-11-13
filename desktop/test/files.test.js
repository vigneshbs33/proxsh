'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { sanitizeFileName, uniquePath } = require('../src/files');
const { lanAddresses, phoneUrl } = require('../src/network');

test('sanitizeFileName strips folders and ../ tricks', () => {
  assert.equal(sanitizeFileName('../../Windows/system32/evil.dll'), 'evil.dll');
  assert.equal(sanitizeFileName('..\\..\\boot.ini'), 'boot.ini');
  assert.equal(sanitizeFileName('..'), 'file');
  assert.equal(sanitizeFileName(''), 'file');
});

test('sanitizeFileName replaces characters Windows rejects', () => {
  assert.equal(sanitizeFileName('a<b>c:d"e|f?g*h.txt'), 'a_b_c_d_e_f_g_h.txt');
  assert.equal(sanitizeFileName('trailing dots...'), 'trailing dots');
  assert.equal(sanitizeFileName('CON.txt'), '_CON.txt');
});

test('sanitizeFileName keeps non-English names and caps length', () => {
  assert.equal(sanitizeFileName('ఫోటో 2026.jpg'), 'ఫోటో 2026.jpg');
  const long = sanitizeFileName(`${'x'.repeat(500)}.pdf`);
  assert.equal(long.length, 200);
  assert.ok(long.endsWith('.pdf'));
});

test('uniquePath adds (1), (2) instead of overwriting', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'proxsh-'));
  assert.equal(uniquePath(dir, 'a.jpg'), path.join(dir, 'a.jpg'));
  fs.writeFileSync(path.join(dir, 'a.jpg'), '');
  assert.equal(uniquePath(dir, 'a.jpg'), path.join(dir, 'a (1).jpg'));
  fs.writeFileSync(path.join(dir, 'a (1).jpg'), '');
  assert.equal(uniquePath(dir, 'a.jpg'), path.join(dir, 'a (2).jpg'));
  fs.rmSync(dir, { recursive: true });
});

test('lanAddresses puts real Wi-Fi before virtual adapters and skips loopback', () => {
  const list = lanAddresses({
    'vEthernet (WSL)': [{ family: 'IPv4', address: '172.20.0.1', internal: false }],
    'Wi-Fi': [{ family: 'IPv4', address: '192.168.1.42', internal: false }],
    Loopback: [{ family: 'IPv4', address: '127.0.0.1', internal: true }],
    Ethernet: [{ family: 'IPv4', address: '169.254.3.3', internal: false }],
  });
  assert.deepEqual(list.map((a) => a.address), ['192.168.1.42', '172.20.0.1']);
});

test('phoneUrl puts the key in the query string', () => {
  assert.equal(phoneUrl('192.168.1.5', 53317, 'abc'), 'http://192.168.1.5:53317/?k=abc');
});
