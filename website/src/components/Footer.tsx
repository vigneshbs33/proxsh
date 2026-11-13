import { Mark } from './Glyphs';
import { REPO_URL, release } from '@/data/site';

/** The closing frame: the slip tucked into the tray, and the way home. */
export function Footer() {
  return (
    <footer className="foot">
      <div className="foot__in">
        <div className="foot__scene" data-reveal aria-hidden="true">
          <svg viewBox="0 0 240 120" className="foot__svg">
            <path d="M30 70V104H210V70" fill="none" stroke="var(--ink)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            <g className="foot__slip">
              <path d="M88 22H132L152 42V92H88Z" fill="var(--surface)" stroke="var(--ink)" strokeWidth="2" strokeLinejoin="round" />
              <path d="M132 22V42H152" fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinejoin="round" />
              <path d="M100 62H138M100 74H128" stroke="var(--line-2)" strokeWidth="3" strokeLinecap="round" />
            </g>
            <path d="M30 70H210" stroke="var(--ink)" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </div>
        <p className="display foot__line">
          Tap Share. <em>It&rsquo;s on the laptop.</em>
        </p>
        <div className="foot__row">
          <a href="#top" className="foot__brand">
            <Mark size={24} />
            <span>ProxSh</span>
          </a>
          <nav className="foot__links" aria-label="Footer">
            <a href="#download">Download</a>
            <a href={REPO_URL} target="_blank" rel="noreferrer">Source on GitHub</a>
            <a href={release.notesUrl} target="_blank" rel="noreferrer">Release notes</a>
          </nav>
          <p className="fine">Version {release.version}. A course project, free and open source.</p>
        </div>
      </div>
    </footer>
  );
}
