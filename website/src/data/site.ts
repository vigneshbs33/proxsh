/*
 * Everything the page says about the downloads lives here, so copy edits and new releases never touch
 * the animation code.
 *
 * To publish a new version:
 *   1. Build the files (gradle :android:assembleDebug and :laptop:packageExe).
 *   2. Create a GitHub release with the tag below and upload both files under exactly these names.
 *   3. Update version, tag, bytes and sha256 here (Get-FileHash <file> -Algorithm SHA256).
 */
export const REPO_URL = 'https://github.com/vigneshbs33/proxsh';

const version = '0.1.0';
const tag = `v${version}`;
const base = `${REPO_URL}/releases/download/${tag}`;

export const release = {
  version,
  tag,
  notesUrl: `${REPO_URL}/releases/tag/${tag}`,
  android: {
    file: `ProxSh-${version}-android.apk`,
    url: `${base}/ProxSh-${version}-android.apk`,
    bytes: 32_699_791,
    sha256: '38e5d503fb970256c119e715de37d05d97577539a284cebd35864cd9a787cc80',
    needs: 'Android 10 or newer',
  },
  windows: {
    file: `ProxSh-${version}-windows-x64.exe`,
    url: `${base}/ProxSh-${version}-windows-x64.exe`,
    bytes: 67_681_792,
    sha256: '08e6bdf8c7f8205c07df1240b37bdb2282b8de63194e3f052aed88fcefcff8ca',
    needs: 'Windows 10 or 11, 64-bit',
  },
} as const;

export const megabytes = (bytes: number) => `${Math.round(bytes / 1_048_576)} MB`;
export const shortHash = (hash: string) => `${hash.slice(0, 8)}…${hash.slice(-8)}`;
