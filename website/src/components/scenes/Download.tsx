'use client';
import { useState } from 'react';
import { Glyph, type GlyphName } from '../Glyphs';
import { Magnetic } from '../Magnetic';
import { megabytes, release, shortHash, REPO_URL } from '@/data/site';

type Target = {
  key: 'android' | 'windows';
  glyph: GlyphName;
  title: string;
  button: string;
  file: string;
  url: string;
  bytes: number;
  sha256: string;
  needs: string;
  steps: string[];
  note: string;
};

const TARGETS: Target[] = [
  {
    key: 'android',
    glyph: 'phone',
    title: 'ProxSh for Android',
    button: 'Download the APK',
    ...release.android,
    steps: ['Open the file you downloaded.', 'If Android asks, allow installs from your browser. It does that for any app outside the Play Store.', 'Open ProxSh and allow notifications.'],
    note: 'Android will warn that the file is from outside the Play Store. That is expected for now.',
  },
  {
    key: 'windows',
    glyph: 'laptop',
    title: 'ProxSh for Windows',
    button: 'Download the zip',
    ...release.windows,
    steps: ['Unzip the folder anywhere you like.', 'Run ProxSh.exe. If Windows Firewall asks, allow it on private networks.', 'Leave it running in the tray so your phone can find it.'],
    note: 'Windows may say the publisher is unknown. ProxSh is a student project and is not signed yet.',
  },
];

function CopyHash({ hash }: { hash: string }) {
  const [done, setDone] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(hash);
      setDone(true);
      setTimeout(() => setDone(false), 1600);
    } catch {
      /* clipboard blocked: the full hash is still selectable in the details below */
    }
  };
  return (
    <button type="button" className="hash" onClick={copy} aria-label="Copy the SHA-256 fingerprint">
      <code>{shortHash(hash)}</code>
      <Glyph name={done ? 'check' : 'copy'} size={15} />
      <span>{done ? 'Copied' : 'Copy'}</span>
    </button>
  );
}

/** The invitation: two sheets of paper on the desk, one per device, each with the real file details. */
export function Download() {
  return (
    <section id="download" data-chapter className="ch ch--flow dl">
      <div className="dl__in">
        <h2 className="display dl__h" data-reveal>
          Take it <em>with you.</em>
        </h2>
        <p className="lede dl__sub" data-reveal style={{ ['--d' as string]: '80ms' }}>
          Install it on the phone and on the laptop. Version {release.version}, free, no account.
        </p>

        <div className="dl__sheets">
          {TARGETS.map((t, i) => (
            <article className={`sheet sheet--${t.key}`} key={t.key} data-reveal style={{ ['--d' as string]: `${160 + i * 120}ms` }}>
              <div className="sheet__fold" aria-hidden="true" />
              <header className="sheet__head">
                <span className="sheet__icon"><Glyph name={t.glyph} size={26} /></span>
                <div>
                  <h3>{t.title}</h3>
                  <p>{release.version} · {megabytes(t.bytes)} · {t.needs}</p>
                </div>
              </header>

              <Magnetic className="sheet__btn">
                <a className="btn btn--primary btn--wide" href={t.url} download>
                  <Glyph name="download" size={19} />
                  {t.button}
                </a>
              </Magnetic>

              <ol className="sheet__steps">
                {t.steps.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ol>

              <p className="sheet__note">{t.note}</p>

              <footer className="sheet__foot">
                <span>SHA-256</span>
                <CopyHash hash={t.sha256} />
              </footer>
            </article>
          ))}
        </div>

        <details className="verify" data-reveal>
          <summary>How to check the file you downloaded</summary>
          <p>Compare the fingerprint of the file with the one above. If they match, it is the file we published.</p>
          <p className="verify__os">On Windows, in PowerShell:</p>
          <pre><code>Get-FileHash .\{release.windows.file} -Algorithm SHA256</code></pre>
          <p className="verify__os">On Mac or Linux:</p>
          <pre><code>sha256sum {release.android.file}</code></pre>
          <p className="verify__full">
            Full fingerprints. Android: <code>{release.android.sha256}</code> Windows: <code>{release.windows.sha256}</code>
          </p>
          <p>
            You can also read every line of the source and build the apps yourself from <a href={REPO_URL} target="_blank" rel="noreferrer">GitHub</a>.
          </p>
        </details>
      </div>
    </section>
  );
}
