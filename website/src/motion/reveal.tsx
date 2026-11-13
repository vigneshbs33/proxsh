'use client';
/*
 * Soft reveals: one IntersectionObserver adds .is-in to every [data-reveal] (CSS in kit.css: a small
 * rise with a touch of blur, staggered with style="--d:120ms"). Cheap, works without GSAP.
 */
import { useEffect } from 'react';
import { prefersReducedMotion } from './gsap';

export function Reveals() {
  useEffect(() => {
    const els = document.querySelectorAll<HTMLElement>('[data-reveal]:not(.is-in)');
    if (prefersReducedMotion() || !('IntersectionObserver' in window)) {
      els.forEach((e) => e.classList.add('is-in'));
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            e.target.classList.add('is-in');
            io.unobserve(e.target);
          }
        }
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0.01 },
    );
    els.forEach((e) => io.observe(e));
    return () => io.disconnect();
  }, []);
  return null;
}
