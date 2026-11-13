'use client';
import { useEffect, useRef } from 'react';
import { gsap } from '@/motion/gsap';
import { finishIntro, lockScroll, revealIntro, unlockScroll } from '@/motion/intro-gate';

/**
 * The intro: a blank slip is drawn on the desk, the arrow is added, the wordmark rises, and the page is
 * revealed through a growing hole. Plays once per session. A repeat visit gets only the exit; reduced
 * motion and ?shot= QA mode get nothing (the head script decides before first paint).
 */
export function Intro() {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const mode = document.documentElement.dataset.intro;
    const el = ref.current;
    if (!el || (mode !== 'long' && mode !== 'short')) return;
    const long = mode === 'long';
    lockScroll('intro');

    const tl = gsap.timeline();
    const at = long ? 2.15 : 0;
    if (long) {
      tl.fromTo('.intro__guide', { scale: 0.92 }, { scale: 1, duration: 0.7, ease: 'expo.out' }, 0)
        .fromTo('.intro__slip', { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 1.1, ease: 'InOut' }, 0.2)
        .fromTo('.intro__fold', { opacity: 0, scale: 0.3 }, { opacity: 1, scale: 1, duration: 0.5, ease: 'expo.out' }, 1.0)
        .fromTo('.intro__arrow', { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.7, ease: 'InOut' }, 0.95)
        .fromTo('.intro__word > span', { yPercent: 130, visibility: 'visible' }, { yPercent: 0, duration: 0.9, ease: 'expo.out' }, 1.05)
        .fromTo('.intro__cap > span', { yPercent: 130, visibility: 'visible' }, { yPercent: 0, duration: 0.8, ease: 'expo.out' }, 1.25);
    }
    tl.to(el, { '--hole': '160vmax', duration: 1, ease: 'expo.inOut' } as gsap.TweenVars, at)
      .add(revealIntro, at + 0.35)
      .add(finishIntro);

    return () => {
      tl.kill();
      unlockScroll('intro');
    };
  }, []);

  return (
    <div className="intro" ref={ref} aria-hidden="true">
      <div className="intro__stack">
        <svg className="intro__svg" viewBox="0 0 120 150">
          <rect className="intro__guide" x="6" y="4" width="108" height="142" rx="14" fill="none" stroke="var(--line)" strokeDasharray="2 4" />
          <path className="intro__slip" pathLength={1} d="M22 14H76L106 44V138H22Z" fill="none" stroke="var(--ink)" strokeWidth="2.4" strokeLinejoin="round" strokeDasharray="1" strokeDashoffset="1" />
          <path className="intro__fold" d="M76 14V44H106" fill="none" stroke="var(--accent)" strokeWidth="2.4" strokeLinejoin="round" style={{ transformBox: 'fill-box', transformOrigin: 'center' }} />
          <path className="intro__arrow" pathLength={1} d="M40 92H88M72 76L88 92L72 108" fill="none" stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" strokeDasharray="1" strokeDashoffset="1" />
        </svg>
        <div className="intro__word">
          <span>ProxSh</span>
        </div>
        <div className="intro__cap">
          <span>Clearing the desk</span>
        </div>
      </div>
    </div>
  );
}
