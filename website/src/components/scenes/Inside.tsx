'use client';
import { useEffect, useRef } from 'react';
import { gsap, ScrollTrigger, prefersReducedMotion } from '@/motion/gsap';
import { Glyph, type GlyphName } from '../Glyphs';
import { REPO_URL } from '@/data/site';

const STATEMENT =
  'Every device makes its own certificate. There is no account, no server in the middle and no cloud copy. A file goes from one device to the other, and nowhere else.';

const FACTS: { glyph: GlyphName; title: string; text: string }[] = [
  { glyph: 'lock', title: 'Its own certificate', text: 'Made on first run. Its fingerprint is its name, so nobody else can claim it.' },
  { glyph: 'tray', title: 'Asks before it takes', text: 'A new device has to be accepted, and you compare four digits on both screens.' },
  { glyph: 'file', title: 'Files stay files', text: 'Photos arrive exactly as they left. Nothing is squeezed on the way.' },
  { glyph: 'check', title: 'Checked on arrival', text: 'Every file is checked against its fingerprint before it is put in your Downloads.' },
  { glyph: 'wifi', title: 'Encrypted in transit', text: 'TLS 1.3 between the two devices, with the other side’s certificate pinned.' },
  { glyph: 'code', title: 'Open source', text: 'Read it, build it, change it. All of it is on GitHub.' },
];

const NOT_YET = ['Hotspot and Bluetooth discovery', 'The internet road, for when you are far apart', 'An iPhone version', 'A signed Windows installer'];

/** Trust: a calm, flowing section. The statement fills with ink as you read down it. */
export function Inside() {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el || prefersReducedMotion()) return;
    const ctx = gsap.context(() => {
      const words = gsap.utils.toArray<HTMLElement>('.fill-word', el);
      const css = getComputedStyle(document.documentElement);
      const from = css.getPropertyValue('--ink-3').trim();
      const to = css.getPropertyValue('--ink').trim();
      gsap.fromTo(
        words,
        { color: from },
        {
          color: to,
          ease: 'none',
          stagger: 0.12,
          scrollTrigger: { trigger: el.querySelector('.inside__statement'), start: 'top 82%', end: 'bottom 48%', scrub: 0.6 },
        },
      );
    }, el);
    ScrollTrigger.refresh();
    return () => ctx.revert();
  }, []);

  return (
    <section id="under" ref={ref} data-chapter className="ch ch--flow inside">
      <div className="inside__in">
        <p className="display inside__statement">
          {STATEMENT.split(' ').map((w, i) => (
            <span className="fill-word" key={i}>{w} </span>
          ))}
        </p>

        <ul className="facts">
          {FACTS.map((f, i) => (
            <li className="fact" key={f.title} data-reveal style={{ ['--d' as string]: `${(i % 3) * 90}ms` }}>
              <span className="fact__icon"><Glyph name={f.glyph} size={22} /></span>
              <h3>{f.title}</h3>
              <p>{f.text}</p>
            </li>
          ))}
        </ul>

        <div className="notyet" data-reveal>
          <h3 className="display">Not built <em>yet.</em></h3>
          <ul>
            {NOT_YET.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
          <p className="fine">The honest list, updated with every release. Follow along on <a href={REPO_URL} target="_blank" rel="noreferrer">GitHub</a>.</p>
        </div>
      </div>
    </section>
  );
}
