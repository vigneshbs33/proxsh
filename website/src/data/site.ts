/*
 * Everything the page says about the downloads lives here, so copy edits and new releases never touch
 * the animation code.
 *
 * To publish a new version:
 *   1. Build the files (gradle :android:assembleDebug, :laptop:createDistributable, then zip the folder).
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
    file: `ProxSh-${version}-windows-x64.zip`,
    url: `${base}/ProxSh-${version}-windows-x64.zip`,
    bytes: 65_592_170,
    sha256: '9ae576e262e86a7da9580ed19ea0621e171c153c1c1926d31e14609b37baa14e',
    needs: 'Windows 10 or 11, 64-bit',
  },
} as const;

export const megabytes = (bytes: number) => `${Math.round(bytes / 1_048_576)} MB`;
export const shortHash = (hash: string) => `${hash.slice(0, 8)}…${hash.slice(-8)}`;
