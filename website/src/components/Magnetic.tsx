'use client';
import { useEffect, useRef, type ReactNode } from 'react';
import { gsap, prefersReducedMotion } from '@/motion/gsap';

/**
 * A button shell that leans toward the pointer. The outer shell follows more and the inner label less,
 * so the two layers separate a little and the button feels like an object with depth.
 * Fine pointers only: never on touch, never with reduced motion.
 */
export function Magnetic({ children, className = '' }: { children: ReactNode; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el || prefersReducedMotion() || !window.matchMedia('(pointer: fine)').matches) return;
    const inner = el.firstElementChild as HTMLElement | null;
    if (!inner) return;

    const move = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      const dx = e.clientX - (r.left + r.width / 2);
      const dy = e.clientY - (r.top + r.height / 2);
      gsap.to(el, { x: Math.max(-26, Math.min(26, dx * 0.3)), y: Math.max(-18, Math.min(18, dy * 0.3)), duration: 0.9, ease: 'power4.out', overwrite: 'auto' });
      gsap.to(inner, { x: Math.max(-14, Math.min(14, dx * 0.16)), y: Math.max(-10, Math.min(10, dy * 0.16)), duration: 1.2, ease: 'power4.out', overwrite: 'auto' });
    };
    const leave = () => {
      gsap.to([el, inner], { x: 0, y: 0, duration: 1.1, ease: 'elastic.out(1, 0.32)', overwrite: 'auto' });
    };
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerleave', leave);
    return () => {
      el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerleave', leave);
      gsap.killTweensOf([el, inner]);
    };
  }, []);

  return (
    <span ref={ref} className={`mag ${className}`}>
      <span className="mag__in">{children}</span>
    </span>
  );
}
