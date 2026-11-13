'use client';
import { useEffect, useRef, useState } from 'react';

const STOPS = ['Hello', 'The old way', 'It finds it', 'Fastest road', 'The code', 'Carries on', 'Under the hood', 'Download'];

/**
 * A ruled margin like a notebook's: a hairline down the right edge with a tick per chapter and a bead
 * that slides to where you are. It reads the real scroll position, nothing is faked.
 */
export function Rail() {
  const root = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const chapters = () => [...document.querySelectorAll<HTMLElement>('[data-chapter]')];
    const update = () => {
      const list = chapters();
      const y = window.scrollY + window.innerHeight * 0.5;
      let i = 0;
      list.forEach((c, n) => {
        if (c.offsetTop <= y) i = n;
      });
      setIndex(i);
      const max = document.documentElement.scrollHeight - window.innerHeight;
      const p = max > 0 ? window.scrollY / max : 0;
      root.current?.style.setProperty('--p', p.toFixed(4));
      document.documentElement.classList.toggle('is-scrolled', window.scrollY > window.innerHeight * 0.25);
    };
    update();
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, []);

  return (
    <div className="rail" ref={root} aria-hidden="true">
      <div className="rail__line">
        {STOPS.map((_, n) => (
          <i key={n} className={`rail__tick ${n <= index ? 'is-passed' : ''}`} style={{ top: `${(n / (STOPS.length - 1)) * 100}%` }} />
        ))}
        <div className="rail__bead" />
      </div>
      <span className="rail__label" key={index}>
        {String(index + 1).padStart(2, '0')} {STOPS[index]}
      </span>
    </div>
  );
}
