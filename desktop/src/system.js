'use strict';

const path = require('node:path');
const { spawn } = require('node:child_process');

function run(cmd, args, input) {
  return new Promise((resolve) => {
    let child;
    try {
      child = spawn(cmd, args, {
        stdio: [input === undefined ? 'ignore' : 'pipe', 'ignore', 'ignore'],
        windowsHide: true,
        detached: input === undefined,
      });
    } catch {
      resolve(false);
      return;
    }
    child.on('error', () => resolve(false));
    if (input === undefined) {
      // Fire-and-forget launchers (explorer, open, xdg-open).
      child.unref();
      resolve(true);
      return;
    }
    child.on('close', (code) => resolve(code === 0));
    child.stdin.on('error', () => {});
    child.stdin.end(input);
  });
}

/** Puts text on the laptop clipboard. Resolves true if it worked. */
function copyToClipboard(text) {
  if (!text) return Promise.resolve(false);
  if (process.platform === 'win32') {
    // Read raw UTF-8 bytes from stdin so emoji and non-English text survive.
    // (clip.exe needs a byte-order mark for Unicode and then pastes it as an
    // invisible character at the start, so it is not used.)
    const script =
      '$s=[Console]::OpenStandardInput();$m=New-Object IO.MemoryStream;$s.CopyTo($m);' +
      'Set-Clipboard -Value ([Text.Encoding]::UTF8.GetString($m.ToArray()))';
    return run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], Buffer.from(text, 'utf8'));
  }
  if (process.platform === 'darwin') return run('pbcopy', [], text);
  if (process.env.WAYLAND_DISPLAY) return run('wl-copy', [], text);
  return run('xclip', ['-selection', 'clipboard'], text);
}

/** Opens a file with its default app. */
function openPath(target) {
  if (process.platform === 'win32') return run('explorer.exe', [target]);
  if (process.platform === 'darwin') return run('open', [target]);
  return run('xdg-open', [target]);
}

/** Opens the folder holding a file, with the file selected where the OS supports it. */
function revealPath(target) {
  if (process.platform === 'win32') return run('explorer.exe', [`/select,${target}`]);
  if (process.platform === 'darwin') return run('open', ['-R', target]);
  return run('xdg-open', [path.dirname(target)]);
}

/** Opens a URL in the default browser. */
function openUrl(url) {
  if (process.platform === 'win32') return run('rundll32.exe', ['url.dll,FileProtocolHandler', url]);
  if (process.platform === 'darwin') return run('open', [url]);
  return run('xdg-open', [url]);
}

module.exports = { copyToClipboard, openPath, revealPath, openUrl };
