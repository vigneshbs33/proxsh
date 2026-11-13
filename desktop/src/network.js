'use strict';

const os = require('node:os');

const VIRTUAL_ADAPTER = /vethernet|virtualbox|vmware|wsl|hyper-v|docker|loopback|tailscale|zerotier|vpn|bluetooth/i;
const WIRELESS_ADAPTER = /wi-?fi|wlan|wireless|wl/i;

function isPrivate(address) {
  return (
    address.startsWith('192.168.') ||
    address.startsWith('10.') ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(address)
  );
}

/**
 * Lists this laptop's IPv4 addresses that a phone on the same Wi-Fi or hotspot
 * could reach, best guess first (real Wi-Fi/Ethernet before virtual adapters).
 */
function lanAddresses(interfaces = os.networkInterfaces()) {
  const found = [];
  for (const [name, addrs] of Object.entries(interfaces)) {
    for (const a of addrs || []) {
      const v4 = a.family === 'IPv4' || a.family === 4;
      if (!v4 || a.internal || a.address.startsWith('169.254.')) continue;
      let score = 0;
      if (isPrivate(a.address)) score += 5;
      if (WIRELESS_ADAPTER.test(name)) score += 3;
      if (VIRTUAL_ADAPTER.test(name)) score -= 10;
      found.push({ name, address: a.address, score });
    }
  }
  return found.sort((x, y) => y.score - x.score).map(({ name, address }) => ({ name, address }));
}

/** The address a phone opens (the QR code holds this). */
function phoneUrl(address, port, key) {
  return `http://${address}:${port}/?k=${encodeURIComponent(key)}`;
}

module.exports = { lanAddresses, phoneUrl };
