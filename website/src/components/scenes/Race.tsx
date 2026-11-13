'use client';
import { useCallback } from 'react';
import { Chapter, type Build } from '@/motion/chapter';
import { Glyph, type GlyphName } from '../Glyphs';

type Lane = { id: string; glyph: GlyphName; name: string; tag: 'today' | 'next'; probe: number };

const LANES: Lane[] = [
  { id: 'wifi', glyph: 'wifi', name: 'Same Wi-Fi', tag: 'today', probe: 0.92 },
  { id: 'hotspot', glyph: 'hotspot', name: 'Phone hotspot', tag: 'next', probe: 0.56 },
  { id: 'internet', glyph: 'globe', name: 'Internet', tag: 'next', probe: 0.34 },
];

function MiniSlip() {
  return (
    <svg viewBox="0 0 30 38" width="100%" height="100%" aria-hidden="true">
      <path d="M2 2H20L28 10V36H2Z" fill="var(--surface)" stroke="var(--ink)" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M20 2V10H28" fill="none" stroke="var(--accent)" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M7 21H23M7 26H18" stroke="var(--line-2)" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

/**
 * Proof by doing: before it sends, ProxSh runs a short speed test down every road, picks the quickest,
 * and sends the file down that one. Only the Wi-Fi road is live today; the others are drawn dashed and
 * marked "next", because the speed test and the chooser are built and tested but those links are not.
 */
export function Race() {
  const build = useCallback<Build>(({ tl, el, q }) => {
    tl.fromTo(q('.rc__h .line > span'), { yPercent: 120 }, { yPercent: 0, duration: 0.12, stagger: 0.04, ease: 'power3.out', immediateRender: false }, 0.02);
    tl.fromTo(q('.rc__sub'), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.1, immediateRender: false }, 0.1);
    tl.fromTo(q('.lane'), { opacity: 0, y: 26 }, { opacity: 1, y: 0, duration: 0.09, stagger: 0.05, ease: 'power3.out', immediateRender: false }, 0.12);

    // 1. a short test down every road: the farther it gets in the same time, the faster the road
    const lanes = [...el.querySelectorAll<HTMLElement>('.lane')];
    lanes.forEach((lane, i) => {
      const probe = LANES[i].probe;
      tl.fromTo(lane, { '--pk': 0 } as gsap.TweenVars, { '--pk': probe, duration: 0.13, ease: 'power2.out', immediateRender: false } as gsap.TweenVars, 0.22);
      tl.fromTo(lane, { '--pk': probe } as gsap.TweenVars, { '--pk': 0, duration: 0.05, ease: 'power2.in', immediateRender: false } as gsap.TweenVars, 0.37);
    });

    // 2. the quickest road is picked, the others step back
    tl.fromTo(q('.lane.is-soon'), { opacity: 1 }, { opacity: 0.38, duration: 0.07, immediateRender: false }, 0.43);
    tl.fromTo(q('.lane.is-live'), { '--hl': 0 } as gsap.TweenVars, { '--hl': 1, duration: 0.07, immediateRender: false } as gsap.TweenVars, 0.43);
    tl.fromTo(q('.lane.is-live .lane__verdict'), { opacity: 0, scale: 0.8 }, { opacity: 1, scale: 1, duration: 0.07, ease: 'back.out(2.4)', immediateRender: false }, 0.45);
    tl.fromTo(q('.st--a'), { opacity: 1 }, { opacity: 0, duration: 0.04, immediateRender: false }, 0.41);
    tl.fromTo(q('.st--b'), { opacity: 0 }, { opacity: 1, duration: 0.05, immediateRender: false }, 0.46);

    // 3. the file goes down that road
    tl.fromTo(q('.lane.is-live'), { '--sk': 0 } as gsap.TweenVars, { '--sk': 1, duration: 0.3, ease: 'glide', immediateRender: false } as gsap.TweenVars, 0.52);
    tl.fromTo(q('.st--b'), { opacity: 1 }, { opacity: 0, duration: 0.04, immediateRender: false }, 0.84);
    tl.fromTo(q('.st--c'), { opacity: 0 }, { opacity: 1, duration: 0.05, immediateRender: false }, 0.86);

    tl.fromTo(q('.race__copy'), { opacity: 1, y: 0 }, { opacity: 0, y: -50, duration: 0.07, immediateRender: false }, 0.94);
    tl.fromTo(q('.race__board'), { opacity: 1 }, { opacity: 0, duration: 0.06, immediateRender: false }, 0.94);
  }, []);

  return (
    <Chapter
      vh={320}
      className="scene race"
      build={build}
      back={
        <div className="scene__back race__back">
          <div className="race__board">
            <div className="race__ends">
              <span><Glyph name="phone" size={20} /> Pixel</span>
              <span>Studio laptop <Glyph name="laptop" size={20} /></span>
            </div>
            {LANES.map((l) => (
              <div className={`lane ${l.tag === 'today' ? 'is-live' : 'is-soon'}`} key={l.id}>
                <div className="lane__name">
                  <Glyph name={l.glyph} size={20} />
                  <span>{l.name}</span>
                  <em>{l.tag}</em>
                </div>
                <div className="lane__track">
                  <i className="lane__line" />
                  <span className="lane__probe" />
                  <span className="lane__slip"><MiniSlip /></span>
                  <b className="lane__verdict">Fastest</b>
                </div>
              </div>
            ))}
            <p className="race__status" aria-live="off">
              <span className="st st--a">Trying each road for a moment…</span>
              <span className="st st--b">Same Wi-Fi is quickest right now.</span>
              <span className="st st--c">Sent down that road.</span>
            </p>
          </div>
        </div>
      }
      front={
        <div className="scene__front race__front">
          <div className="scene__copy race__copy">
            <h2 className="display rc__h">
              <span className="line"><span>It picks the</span></span>
              <span className="line"><span><em>fastest road.</em></span></span>
            </h2>
            <p className="lede rc__sub">It measures before it sends, so it never has to guess. Today the road is your Wi-Fi. The hotspot and the internet plug into the same test next.</p>
          </div>
        </div>
      }
    />
  );
}
