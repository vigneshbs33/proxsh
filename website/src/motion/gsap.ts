/*
 * One place that registers GSAP plugins and the site's motion vocabulary.
 * Import gsap from here everywhere, so plugins are registered once and only in the browser.
 *
 * Motion personality for this site: paper with weight. Slow, eased, soft blur on the way in, small
 * distances. A slight overshoot only on things you press (the download buttons).
 */
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { SplitText } from 'gsap/SplitText';
import { CustomEase } from 'gsap/CustomEase';
import { MotionPathPlugin } from 'gsap/MotionPathPlugin';

if (typeof window !== 'undefined') {
  gsap.registerPlugin(ScrollTrigger, SplitText, CustomEase, MotionPathPlugin);
  CustomEase.create('InOut', '.75,0,.25,1');
  CustomEase.create('Out', '.25,1,.5,1');
  CustomEase.create('In', '.5,0,.75,0');
  CustomEase.create('glide', '.45,0,.15,1');
}

export const DUR = { s: 0.4, m: 0.8, l: 1.2 } as const;
export const STAGGER = 0.1;
/** Below this width, motion simplifies: nothing is magnetic, scenes re-block for portrait. */
export const MOTION_BP = 992;

export const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * SplitText with masks that don't clip: each mask is padded and pulled back by the same amount, so italic
 * overhangs and descenders survive while layout is unchanged. Hide letters at yPercent ±160 (not ±110).
 */
export function splitMasked(target: Element, type: 'chars' | 'words' | 'lines' | 'words,chars' | 'lines,words', mask: 'chars' | 'words' | 'lines') {
  const split = SplitText.create(target, { type, mask });
  for (const m of split.masks as HTMLElement[]) Object.assign(m.style, { padding: '0 .12em .14em', margin: '0 -.12em -.14em' });
  return split;
}

/**
 * A gentle arc between two points. Returns a function t -> point and the angle of travel there, and
 * the same arc as an SVG path string in `.d`, so a trail can be drawn exactly where the slip flies.
 */
export function curve(p0: [number, number], p1: [number, number], bend: number) {
  const mid: [number, number] = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
  const dx = p1[0] - p0[0];
  const dy = p1[1] - p0[1];
  const c: [number, number] = [mid[0] - dy * bend, mid[1] + dx * bend];
  const at = (t: number) => {
    const u = 1 - t;
    const x = u * u * p0[0] + 2 * u * t * c[0] + t * t * p1[0];
    const y = u * u * p0[1] + 2 * u * t * c[1] + t * t * p1[1];
    const tx = 2 * u * (c[0] - p0[0]) + 2 * t * (p1[0] - c[0]);
    const ty = 2 * u * (c[1] - p0[1]) + 2 * t * (p1[1] - c[1]);
    return { x, y, angle: (Math.atan2(ty, tx) * 180) / Math.PI };
  };
  at.d = `M${p0[0].toFixed(1)} ${p0[1].toFixed(1)}Q${c[0].toFixed(1)} ${c[1].toFixed(1)} ${p1[0].toFixed(1)} ${p1[1].toFixed(1)}`;
  return at;
}

export const clamp01 = (v: number) => Math.min(1, Math.max(0, v));
/** 0 before a, 1 after b, linear between. */
export const ramp = (a: number, b: number, p: number) => clamp01((p - a) / (b - a));

export { gsap, ScrollTrigger, SplitText };
