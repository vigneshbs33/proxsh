#!/usr/bin/env node
'use strict';

const QRCode = require('qrcode');
const { loadConfig } = require('./config');
const { createProxshServer } = require('./server');
const { lanAddresses, phoneUrl } = require('./network');
const { openUrl } = require('./system');
const { version } = require('../package.json');

const args = process.argv.slice(2);
const config = loadConfig();
const server = createProxshServer(config);

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(`Port ${config.port} is already in use. Is ProxSh already running?`);
    console.error('To use another port: set PROXSH_PORT=53318 and start again.');
  } else {
    console.error(err);
  }
  process.exit(1);
});

server.listen(config.port, '0.0.0.0', async () => {
  const deskUrl = `http://localhost:${config.port}/desk`;
  const addresses = lanAddresses();

  console.log(`\nProxSh ${version} is running on "${config.deviceName}"`);
  console.log(`Files from your phone are saved to: ${config.receiveDir}\n`);

  if (!addresses.length) {
    console.log('No Wi-Fi or network connection found.');
    console.log('Connect this laptop to Wi-Fi, or turn on your phone hotspot and join it.\n');
  } else {
    const url = phoneUrl(addresses[0].address, config.port, config.key);
    console.log('Scan this with your phone camera (phone and laptop on the same Wi-Fi or hotspot):\n');
    console.log(await QRCode.toString(url, { type: 'terminal', small: true }));
    console.log(`  ${url}\n`);
    if (addresses.length > 1) {
      console.log('Other addresses, if the one above does not work:');
      for (const a of addresses.slice(1)) console.log(`  ${phoneUrl(a.address, config.port, config.key)}   (${a.name})`);
      console.log('');
    }
  }

  console.log(`Laptop window: ${deskUrl}`);
  console.log('Press Ctrl+C to stop.\n');
  if (!args.includes('--no-open')) openUrl(deskUrl);
});

function shutdown() {
  server.stopAll();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
