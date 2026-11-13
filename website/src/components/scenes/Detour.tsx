'use client';
import { useCallback } from 'react';
import { Chapter, type Build } from '@/motion/chapter';
import { gsap } from '@/motion/gsap';
import { Glyph } from '../Glyphs';

/**
 * Tension: the ordinary way, shown honestly. The slip crawls through five steps on a winding road,
 * and a counter ticks up. Then the road is wiped away and the short way is shown.
 */

type Pt = [number, number];

/** A smooth curve through the points (Catmull-Rom turned into cubic segments), so every stop lies exactly on the road. */
function through(points: Pt[]) {
  let d = `M${points[0][0]} ${points[0][1]}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] ?? points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] ?? p2;
    const c1: Pt = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2: Pt = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += `C${c1[0].toFixed(1)} ${c1[1].toFixed(1)} ${c2[0].toFixed(1)} ${c2[1].toFixed(1)} ${p2[0]} ${p2[1]}`;
  }
  return d;
}

const STEPS = ['Open WhatsApp', 'Message yourself', 'Wait for the upload', 'Open WhatsApp Web', 'Download it again'];

const WIDE: { box: string; pts: Pt[]; label: (i: number) => { dx: number; dy: number; anchor: 'start' | 'middle' | 'end' } } = {
  box: '0 0 1200 460',
  pts: [[70, 350], [250, 130], [450, 330], [650, 130], [850, 330], [1030, 130], [1130, 340]],
  label: (i) => ({ dx: 0, dy: i % 2 ? 38 : -26, anchor: 'middle' }),
};
const TALL: typeof WIDE = {
  box: '0 0 400 1000',
  pts: [[200, 60], [90, 200], [300, 370], [90, 540], [300, 710], [90, 860], [200, 950]],
  label: (i) => (i % 2 ? { dx: -22, dy: 5, anchor: 'end' } : { dx: 22, dy: 5, anchor: 'start' }),
};

function Road({ variant, shape }: { variant: 'wide' | 'tall'; shape: typeof WIDE }) {
  const d = through(shape.pts);
  const stops = shape.pts.slice(1, -1);
  return (
    <svg className={`dt dt--${variant}`} viewBox={shape.box} aria-hidden="true">
      <path className="dt__road" d={d} fill="none" stroke="var(--line-2)" strokeWidth="2" strokeDasharray="2 9" strokeLinecap="round" />
      <path className="dt__ink" d={d} pathLength={1} fill="none" stroke="var(--amber)" strokeWidth="3" strokeLinecap="round" strokeDasharray="1" strokeDashoffset="1" />
      <g className="dt__end dt__end--a" transform={`translate(${shape.pts[0][0]} ${shape.pts[0][1]})`}>
        <circle r="26" fill="var(--surface)" stroke="var(--line-2)" />
        <g transform="translate(-12 -12)" stroke="var(--ink)" fill="none" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><rect x="7" y="3" width="10" height="18" rx="2.6" /><path d="M10.5 18h3" /></g>
      </g>
      <g className="dt__end dt__end--b" transform={`translate(${shape.pts[shape.pts.length - 1][0]} ${shape.pts[shape.pts.length - 1][1]})`}>
        <circle r="26" fill="var(--surface)" stroke="var(--line-2)" />
        <g transform="translate(-12 -12)" stroke="var(--ink)" fill="none" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><rect x="4.5" y="5" width="15" height="10" rx="1.8" /><path d="M2.5 19h19" /></g>
      </g>
      {stops.map(([x, y], i) => {
        const l = shape.label(i);
        return (
          <g className="dt__stop" key={i} transform={`translate(${x} ${y})`}>
            <circle r="15" fill="var(--surface)" stroke="var(--amber)" strokeWidth="1.5" />
            <text className="dt__n" textAnchor="middle" dy="5">{i + 1}</text>
            <text className="dt__label" x={l.dx} y={l.dy} textAnchor={l.anchor}>{STEPS[i]}</text>
          </g>
        );
      })}
      <g className="dt__slip">
        <g transform="translate(-15 -19)">
          <path d="M3 3H21L30 12V35H3Z" fill="var(--surface)" stroke="var(--ink)" strokeWidth="1.6" strokeLinejoin="round" />
          <path d="M21 3V12H30" fill="none" stroke="var(--accent)" strokeWidth="1.6" strokeLinejoin="round" />
          <path d="M8 22H25M8 27H20" stroke="var(--line-2)" strokeWidth="2" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  );
}

export function Detour() {
  const build = useCallback<Build>(({ tl, el, q }) => {
    const count = el.querySelector<HTMLElement>('.dt__count b')!;
    const roads = (['wide', 'tall'] as const).map((v) => ({
      svg: el.querySelector<SVGSVGElement>(`.dt--${v}`)!,
    }));
    const heading = q('.dt__h .line > span');

    tl.fromTo(heading, { yPercent: 120 }, { yPercent: 0, duration: 0.12, stagger: 0.03, ease: 'power3.out', immediateRender: false }, 0.02);
    tl.fromTo(q('.dt__sub'), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.1, immediateRender: false }, 0.1);

    const start = 0.2;
    const end = 0.72;
    roads.forEach(({ svg }) => {
      const road = svg.querySelector<SVGPathElement>('.dt__road')!;
      const slip = svg.querySelector<SVGGElement>('.dt__slip')!;
      const ink = svg.querySelector<SVGPathElement>('.dt__ink')!;
      const stops = [...svg.querySelectorAll<SVGGElement>('.dt__stop')];
      tl.fromTo(ink, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: end - start, ease: 'none', immediateRender: false }, start);
      tl.fromTo(
        slip,
        { motionPath: { path: road, align: road, alignOrigin: [0.5, 0.5], start: 0, end: 0 } },
        { motionPath: { path: road, align: road, alignOrigin: [0.5, 0.5], start: 0, end: 1 }, duration: end - start, ease: 'glide', immediateRender: false },
        start,
      );
      stops.forEach((s, i) => {
        const at = start + ((i + 1) / (stops.length + 1)) * (end - start) - 0.03;
        tl.fromTo(s, { opacity: 0.22, scale: 0.8, transformOrigin: 'center' }, { opacity: 1, scale: 1, duration: 0.06, ease: 'back.out(2)', immediateRender: false }, at);
      });
    });

    // the counter ticks with the slip: one real step at a time, five in all
    const steps = { n: 0 };
    tl.fromTo(
      steps,
      { n: 0 },
      { n: 5, duration: end - start, ease: 'glide', immediateRender: false, onUpdate: () => { count.textContent = String(Math.min(5, Math.round(steps.n + 0.2))); } },
      start,
    );

    // the words leave, the road is wiped away, and the short way takes its place
    tl.fromTo(q('.dt__h, .dt__sub'), { opacity: 1, y: 0 }, { opacity: 0, y: -40, duration: 0.08, immediateRender: false }, 0.76);
    tl.fromTo(q('.dt'), { opacity: 1, y: 0 }, { opacity: 0, y: -30, duration: 0.1, immediateRender: false }, 0.76);
    tl.fromTo(q('.dt__count'), { opacity: 1 }, { opacity: 0, duration: 0.06, immediateRender: false }, 0.76);
    tl.fromTo(q('.dt__short'), { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: 0.1, ease: 'power3.out', immediateRender: false }, 0.82);
    tl.fromTo(q('.short__row'), { '--k': 0 } as gsap.TweenVars, { '--k': 1, duration: 0.1, ease: 'glide', immediateRender: false } as gsap.TweenVars, 0.88);
    tl.fromTo(q('.dt__short'), { opacity: 1 }, { opacity: 0, duration: 0.05, immediateRender: false }, 0.96);
  }, []);

  return (
    <Chapter
      id="story"
      vh={340}
      className="detour"
      build={build}
      back={
        <div className="detour__back">
          <Road variant="wide" shape={WIDE} />
          <Road variant="tall" shape={TALL} />
        </div>
      }
      front={
        <div className="detour__front">
          <h2 className="display dt__h">
            <span className="line"><span>Today it takes</span></span>
            <span className="line"><span><em>five steps.</em></span></span>
          </h2>
          <p className="lede dt__sub">Send it to yourself on WhatsApp, wait, open it on the laptop, download it again. Photos get squeezed along the way.</p>
          <p className="dt__count" aria-live="off">
            <b>0</b> of 5 steps
          </p>
          <div className="dt__short">
            <div className="short__row">
              <span className="short__dev"><Glyph name="phone" size={26} /></span>
              <span className="short__line"><i className="short__slip" /></span>
              <span className="short__dev"><Glyph name="laptop" size={26} /></span>
            </div>
            <p className="display">Or tap <em>Share.</em></p>
          </div>
        </div>
      }
    />
  );
}

// kept so the unused-import check never strips the plugin registration side effect
void gsap;
