'use client';
import { useEffect, useState } from 'react';
import { Mark } from './Glyphs';
import { Magnetic } from './Magnetic';
import { REPO_URL } from '@/data/site';

const LINKS = [
  { href: '#story', label: 'How it works', id: 'story' },
  { href: '#under', label: 'Under the hood', id: 'under' },
  { href: '#download', label: 'Download', id: 'download' },
];

/** Each letter sits in a clipped cell with a copy below; on hover the column rolls up. */
function Roll({ text }: { text: string }) {
  return (
    <span className="roll" aria-label={text}>
      {[...text].map((c, i) => (
        <span className="roll__cell" key={i} aria-hidden="true">
          <span className="roll__char" style={{ ['--i' as string]: i }}>
            {c === ' ' ? ' ' : c}
            <span className="roll__copy">{c === ' ' ? ' ' : c}</span>
          </span>
        </span>
      ))}
    </span>
  );
}

export function Nav() {
  const [pill, setPill] = useState(false);
  const [active, setActive] = useState('');

  useEffect(() => {
    const onScroll = () => setPill(window.scrollY > 40);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const sections = LINKS.map((l) => document.getElementById(l.id)).filter(Boolean) as HTMLElement[];
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) if (e.isIntersecting) setActive(e.target.id);
      },
      { rootMargin: '-45% 0px -45% 0px' },
    );
    sections.forEach((s) => io.observe(s));
    return () => io.disconnect();
  }, []);

  return (
    <header className={`nav ${pill ? 'is-pill' : ''}`}>
      <nav className="nav__bar" aria-label="Main">
        <a href="#top" className="nav__brand" aria-label="ProxSh, back to the top">
          <Mark size={28} />
          <span>ProxSh</span>
        </a>
        <ul className="nav__links">
          {LINKS.map((l) => (
            <li key={l.id}>
              <a href={l.href} className={`roll-host ${active === l.id ? 'is-active' : ''}`}>
                <Roll text={l.label} />
                <i className="nav__bead" aria-hidden="true" />
              </a>
            </li>
          ))}
          <li>
            <a href={REPO_URL} className="roll-host" target="_blank" rel="noreferrer">
              <Roll text="GitHub" />
            </a>
          </li>
        </ul>
        <Magnetic>
          <a href="#download" className="btn btn--primary btn--sm">
            Get ProxSh
          </a>
        </Magnetic>
      </nav>
    </header>
  );
}
