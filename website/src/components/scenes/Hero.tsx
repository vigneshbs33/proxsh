'use client';
import { useCallback, useEffect } from 'react';
import { Chapter, type Build } from '@/motion/chapter';
import { gsap, curve, prefersReducedMotion } from '@/motion/gsap';
import { afterIntro } from '@/motion/intro-gate';
import { Stage, Phone, Laptop, Slip, PhoneUi, LaptopUi } from '../Devices';
import { Glyph } from '../Glyphs';
import { Magnetic } from '../Magnetic';

/**
 * Hello. The slip starts on the phone and, as you scroll, crosses the desk to the laptop, leaving a
 * dotted trail. The two buttons are on screen from the first frame: the story is a reward for
 * scrolling, never a toll before the download.
 */
export function Hero() {
  const build = useCallback<Build>(({ tl, el }) => {
    const stage = el.querySelector<HTMLElement>('.stage')!;
    const phone = stage.querySelector<HTMLElement>('.dev--phone .dev__screen')!;
    const laptop = stage.querySelector<HTMLElement>('.dev--laptop .dev__screen')!;
    const slip = stage.querySelector<HTMLElement>('.slip')!;
    const trail = stage.querySelector<SVGSVGElement>('.trail')!;
    const dots = trail.querySelector<SVGPathElement>('.trail__dots')!;
    const reveal = trail.querySelector<SVGPathElement>('.trail__reveal')!;
    const pos = { t: 0 };

    const draw = () => {
      const s = stage.getBoundingClientRect();
      const centre = (n: Element): [number, number] => {
        const r = n.getBoundingClientRect();
        return [r.left + r.width / 2 - s.left, r.top + r.height / 2 - s.top];
      };
      // the slip rests on the phone's top edge, as if it is rising out of the phone
      const pr = phone.getBoundingClientRect();
      const from: [number, number] = [pr.left + pr.width * 0.56 - s.left, pr.top - s.top - pr.height * 0.03];
      const path = curve(from, centre(laptop), -0.3);
      const here = path(pos.t);
      const lift = Math.sin(Math.PI * pos.t);
      trail.setAttribute('viewBox', `0 0 ${s.width.toFixed(1)} ${s.height.toFixed(1)}`);
      dots.setAttribute('d', path.d);
      reveal.setAttribute('d', path.d);
      gsap.set(slip, { x: here.x, y: here.y, rotation: -7 + here.angle * 0.1 + lift * 8, scale: 0.6 + lift * 0.42 });
    };
    draw();

    tl.fromTo(pos, { t: 0 }, { t: 1, duration: 0.5, ease: 'glide', immediateRender: false, onUpdate: draw }, 0.14);
    tl.fromTo(reveal, { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.5, ease: 'glide', immediateRender: false }, 0.14);
    tl.fromTo(slip, { opacity: 1 }, { opacity: 0, duration: 0.05, immediateRender: false }, 0.66);
    tl.fromTo(el.querySelector('.ph-sent'), { opacity: 0, y: 6 }, { opacity: 1, y: 0, duration: 0.06, immediateRender: false }, 0.64);
    tl.fromTo(el.querySelector('.lp-done'), { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: 0.08, ease: 'power3.out', immediateRender: false }, 0.66);
    tl.fromTo(el.querySelector('.hero__copy'), { opacity: 1, y: 0 }, { opacity: 0, y: -70, duration: 0.1, immediateRender: false }, 0.9);

    // pointer parallax: the devices drift a few pixels toward the pointer (desktop, no reduced motion)
    if (!prefersReducedMotion() && window.matchMedia('(pointer: fine)').matches) {
      const layers = [...el.querySelectorAll<HTMLElement>('.dev')];
      const move = (e: PointerEvent) => {
        const nx = e.clientX / window.innerWidth - 0.5;
        const ny = e.clientY / window.innerHeight - 0.5;
        layers.forEach((l, i) =>
          gsap.to(l, { x: nx * (i ? 14 : 22), y: ny * (i ? 10 : 16), duration: 1.4, ease: 'power3.out', overwrite: 'auto' }),
        );
      };
      window.addEventListener('pointermove', move, { passive: true });
      gsap.context(() => {}, el).add(() => window.removeEventListener('pointermove', move));
    }
  }, []);

  // after the intro: the headline rises line by line, the rest fades in, the desk settles
  useEffect(() => {
    const root = document.querySelector('.hero');
    if (!root) return;
    return afterIntro(() => {
      if (prefersReducedMotion()) {
        gsap.set(root.querySelectorAll('.hero__line > span, .hero__fade, .hero__stage'), { clearProps: 'all' });
        return;
      }
      gsap.fromTo(root.querySelectorAll('.hero__line > span'), { yPercent: 118, visibility: 'visible' }, { yPercent: 0, duration: 1.25, ease: 'expo.out', stagger: 0.13 });
      gsap.fromTo(
        root.querySelectorAll('.hero__fade'),
        { opacity: 0, y: 22, filter: 'blur(8px)' },
        { opacity: 1, y: 0, filter: 'blur(0px)', duration: 1.2, ease: 'expo.out', stagger: 0.1, delay: 0.4, clearProps: 'filter' },
      );
      gsap.fromTo(root.querySelector('.hero__stage'), { opacity: 0, y: 40, scale: 0.97 }, { opacity: 1, y: 0, scale: 1, duration: 1.6, ease: 'expo.out', delay: 0.2 });
    });
  }, []);

  return (
    <Chapter
      id="top"
      vh={260}
      className="hero"
      build={build}
      back={
        <div className="hero__back">
          <Stage className="hero__stage">
            <i className="scrap" />
            <i className="scrap" />
            <i className="scrap" />
            <svg className="trail" aria-hidden="true">
              <defs>
                <mask id="hero-trail-mask">
                  <path className="trail__reveal" pathLength={1} fill="none" stroke="#fff" strokeWidth="6" strokeDasharray="1" strokeDashoffset="1" />
                </mask>
              </defs>
              <path className="trail__dots" fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinecap="round" strokeDasharray="0.5 8" mask="url(#hero-trail-mask)" />
            </svg>
            <Phone ping>
              <PhoneUi parts={['peer', 'actions', 'sent']} />
            </Phone>
            <Laptop ping="late">
              <LaptopUi parts={['peer', 'drop', 'done']} />
            </Laptop>
            <Slip />
          </Stage>
        </div>
      }
      front={
        <div className="hero__front">
          <div className="hero__copy">
            <h1 className="display hero__h">
              <span className="hero__line">
                <span>Phone to laptop,</span>
              </span>
              <span className="hero__line">
                <span>
                  <em>already there.</em>
                </span>
              </span>
            </h1>
            <p className="lede hero__fade">Share a photo, a file or a link and it lands on the other device by itself. No account, no cable, no code to type.</p>
            <div className="hero__cta hero__fade">
              <Magnetic>
                <a href="#download" className="btn btn--primary">
                  <Glyph name="phone" size={18} />
                  Get it for Android
                </a>
              </Magnetic>
              <Magnetic>
                <a href="#download" className="btn btn--ghost">
                  <Glyph name="laptop" size={18} />
                  Get it for Windows
                </a>
              </Magnetic>
            </div>
            <p className="fine hero__fade">Free and open source. Both devices need to be on the same Wi-Fi.</p>
          </div>
          <div className="scroll-cue" aria-hidden="true">
            <span>Scroll</span>
            <i />
          </div>
        </div>
      }
    />
  );
}
