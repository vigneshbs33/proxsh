import type { ReactNode } from 'react';
import { Glyph } from './Glyphs';

/**
 * The desk: a mat seen from above with devices lying on it. Everything inside is sized in container
 * units (cqw), so a scene scales as one piece at any stage size.
 */
export function Stage({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`stage ${className}`}>
      <div className="stage__mat" aria-hidden="true" />
      {children}
    </div>
  );
}

export function Phone({ children, className = '', ping }: { children: ReactNode; className?: string; ping?: boolean | 'late' }) {
  return (
    <div className={`dev dev--phone ${className}`}>
      {ping && <i className={`ping ${ping === 'late' ? 'ping--late' : ''}`} />}
      <div className="dev__body">
        <div className="dev__screen">{children}</div>
      </div>
    </div>
  );
}

export function Laptop({ children, className = '', ping }: { children: ReactNode; className?: string; ping?: boolean | 'late' }) {
  return (
    <div className={`dev dev--laptop ${className}`}>
      {ping && <i className={`ping ${ping === 'late' ? 'ping--late' : ''}`} />}
      <div className="dev__body">
        <div className="dev__screen">{children}</div>
      </div>
      <div className="dev__deck" aria-hidden="true" />
    </div>
  );
}

/** The slip of paper: the file. It is the protagonist, so it is drawn with care. */
export function Slip({ name = 'IMG-2045.jpg', className = '' }: { name?: string; className?: string }) {
  return (
    <div className={`slip ${className}`}>
      <div className="slip__in">
        <svg className="slip__pic" viewBox="0 0 60 40" aria-hidden="true">
          <rect width="60" height="40" rx="3" fill="var(--accent-soft)" />
          <circle cx="44" cy="12" r="5" fill="var(--amber-soft)" stroke="var(--amber)" strokeWidth="1.2" />
          <path d="M0 33l14-14 11 11 8-7 27 20H0z" fill="var(--accent)" opacity=".82" />
        </svg>
        <span className="slip__bar" />
        <span className="slip__bar slip__bar--short" />
        <span className="slip__name">{name}</span>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ what the screens show */
/* These mirror the real apps, with fictional names. Every part is optional so a scene shows only
   what its story needs. */

export type LaptopPart = 'peer' | 'drop' | 'live' | 'done' | 'ask';
export type PhonePart = 'peer' | 'actions' | 'wait' | 'live' | 'sent';

export function LaptopUi({ parts, peer = 'Pixel' }: { parts: LaptopPart[]; peer?: string }) {
  const has = (p: LaptopPart) => parts.includes(p);
  return (
    <div className="lp">
      <aside className="lp__side">
        <b className="lp__brand">ProxSh</b>
        <small>Visible as Studio laptop</small>
        <p className="lp__label">Nearby</p>
        {has('peer') ? (
          <div className="lp-peer">
            <span className="lp-peer__badge">
              <Glyph name="phone" size={14} />
            </span>
            <span className="lp-peer__txt">
              <b>{peer}</b>
              <small>Ready · Wi-Fi</small>
            </span>
            <Glyph name="check" size={12} />
          </div>
        ) : (
          <div className="lp-empty">
            <Glyph name="tray" size={18} />
            <span>Open ProxSh on your phone</span>
          </div>
        )}
      </aside>
      <section className="lp__main">
        <h4 className="lp__h">
          {has('peer') ? (
            <>
              Send to <em>{peer}</em>
            </>
          ) : (
            <>
              Waiting for <em>your phone</em>
            </>
          )}
        </h4>
        {has('drop') && (
          <div className="lp-drop">
            <Glyph name="tray" size={16} />
            <b>Drop files here</b>
          </div>
        )}
        <ul className="lp__list">
          {has('live') && (
            <li className="lp-live">
              <i>
                <Glyph name="file" size={12} />
              </i>
              <span>
                <b>Lecture notes.pdf</b>
                <span className="lp-bar">
                  <span />
                </span>
                <small>2.4 of 4.9 MB · Wi-Fi</small>
              </span>
            </li>
          )}
          {has('done') && (
            <li className="lp-done">
              <i>
                <Glyph name="file" size={12} />
              </i>
              <span>
                <b>IMG-2045.jpg</b>
                <small>2.4 MB from {peer} · saved</small>
              </span>
              <em>Open</em>
            </li>
          )}
        </ul>
      </section>
      {has('ask') && (
        <div className="lp-ask">
          <b>{peer}</b>
          <span>wants to send you</span>
          <div className="lp-ask__code">4827</div>
          <small>Same code on {peer}?</small>
          <div className="lp-ask__always">
            <span className="lp-tick">
              <Glyph name="check" size={9} />
            </span>
            Always accept from this device
          </div>
          <div className="lp-ask__btns">
            <i>Decline</i>
            <i className="is-go">Accept</i>
          </div>
        </div>
      )}
    </div>
  );
}

export function PhoneUi({ parts, peer = 'Studio laptop' }: { parts: PhonePart[]; peer?: string }) {
  const has = (p: PhonePart) => parts.includes(p);
  return (
    <div className="ph">
      <div className="ph__top">
        <b>ProxSh</b>
        <small>Visible as Pixel</small>
      </div>
      <h4 className="ph__h">
        {has('peer') ? (
          <>
            Send to <em>{peer}</em>
          </>
        ) : (
          <>
            Waiting for <em>your laptop</em>
          </>
        )}
      </h4>
      {has('peer') && (
        <div className="ph-peer">
          <span className="ph-peer__badge">
            <Glyph name="laptop" size={13} />
          </span>
          <span className="ph-peer__txt">
            <b>{peer}</b>
            <small>Ready · Wi-Fi</small>
          </span>
          <Glyph name="check" size={11} />
        </div>
      )}
      {has('actions') && (
        <div className="ph-actions">
          <span className="ph-btn is-go">Choose files</span>
          <span className="ph-btn">Send what I copied</span>
        </div>
      )}
      {has('wait') && (
        <div className="ph-wait">
          <small>Waiting for {peer} to accept</small>
          <div className="ph-wait__code">4827</div>
        </div>
      )}
      {has('live') && (
        <div className="ph-live">
          <b>IMG-2045.jpg</b>
          <span className="lp-bar">
            <span />
          </span>
          <small>1.1 of 2.4 MB · Wi-Fi</small>
        </div>
      )}
      {has('sent') && (
        <div className="ph-sent">
          <Glyph name="check" size={12} />
          <span>IMG-2045.jpg sent · Wi-Fi</span>
        </div>
      )}
    </div>
  );
}
