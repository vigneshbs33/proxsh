'use client';
import { useCallback } from 'react';
import { Chapter, type Build } from '@/motion/chapter';
import { gsap, ramp } from '@/motion/gsap';
import { Stage, Phone, Laptop, PhoneUi, LaptopUi } from '../Devices';
import { Glyph } from '../Glyphs';

const CODE = '4827';

/** A stable pseudo-random digit, so the spinning digits don't flicker differently on every frame. */
const spin = (i: number, tick: number) => Math.floor(Math.abs(Math.sin(i * 12.9898 + tick * 78.233) * 43758.5453) % 10);

/**
 * Depth: nothing arrives unasked. Both screens show a four-digit code that is the same for this pair
 * of devices and nobody else's. The digits spin, then lock one by one, and a line joins the two.
 */
export function Code() {
  const build = useCallback<Build>(({ tl, el, q }) => {
    const codes = [...el.querySelectorAll<HTMLElement>('.lp-ask__code, .ph-wait__code')];
    const set = (text: string) => codes.forEach((c) => { if (c.textContent !== text) c.textContent = text; });
    set('0000');

    tl.fromTo(q('.cd__h .line > span'), { yPercent: 120 }, { yPercent: 0, duration: 0.12, stagger: 0.04, ease: 'power3.out', immediateRender: false }, 0.03);
    tl.fromTo(q('.cd__sub'), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.1, immediateRender: false }, 0.12);

    tl.fromTo(q('.layer--a'), { opacity: 1 }, { opacity: 0, duration: 0.06, immediateRender: false }, 0.18);
    tl.fromTo(q('.lp-ask'), { opacity: 0, y: 24, scale: 0.96 }, { opacity: 1, y: 0, scale: 1, duration: 0.1, ease: 'power3.out', immediateRender: false }, 0.2);
    tl.fromTo(q('.ph-wait'), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.1, ease: 'power3.out', immediateRender: false }, 0.24);

    // the digits spin, then lock left to right
    const lock = { v: 0 };
    tl.fromTo(
      lock,
      { v: 0 },
      {
        v: 1,
        duration: 0.3,
        ease: 'none',
        immediateRender: false,
        onUpdate: () => {
          const p = lock.v;
          const tick = Math.floor(p * 36);
          let text = '';
          for (let i = 0; i < 4; i++) text += p >= (i + 1) / 4.6 ? CODE[i] : String(spin(i, tick));
          set(p >= 0.99 ? CODE : text);
        },
      },
      0.34,
    );

    tl.fromTo(q('.code-link'), { scaleX: 0 }, { scaleX: 1, duration: 0.1, ease: 'glide', immediateRender: false }, 0.64);
    tl.fromTo(q('.code-link__chip'), { opacity: 0, scale: 0.7 }, { opacity: 1, scale: 1, duration: 0.07, ease: 'back.out(2.4)', immediateRender: false }, 0.72);
    tl.fromTo(q('.lp-tick'), { scale: 0.4, opacity: 0.25 }, { scale: 1, opacity: 1, duration: 0.07, ease: 'back.out(3)', immediateRender: false }, 0.82);

    tl.fromTo(q('.code__copy'), { opacity: 1, y: 0 }, { opacity: 0, y: -50, duration: 0.07, immediateRender: false }, 0.93);
    tl.fromTo(q('.stage'), { opacity: 1 }, { opacity: 0, duration: 0.06, immediateRender: false }, 0.94);
    void ramp;
    void gsap;
  }, []);

  return (
    <Chapter
      vh={300}
      className="scene code"
      build={build}
      back={
        <div className="scene__back">
          <Stage className="scene__stage code__stage">
            <Phone>
              <div className="layer layer--a"><PhoneUi parts={['peer', 'actions']} /></div>
              <div className="layer layer--b"><PhoneUi parts={['peer', 'wait']} /></div>
            </Phone>
            <Laptop>
              <div className="layer layer--a"><LaptopUi parts={['peer', 'drop']} /></div>
              <div className="layer layer--b"><LaptopUi parts={['peer', 'drop', 'ask']} /></div>
            </Laptop>
            <div className="code-link" aria-hidden="true" />
            <span className="code-link__chip">
              <Glyph name="check" size={14} /> match
            </span>
          </Stage>
        </div>
      }
      front={
        <div className="scene__front">
          <div className="scene__copy code__copy">
            <h2 className="display cd__h">
              <span className="line"><span>Nothing arrives</span></span>
              <span className="line"><span><em>unasked.</em></span></span>
            </h2>
            <p className="lede cd__sub">The first time a device sends, the other one has to say yes. Both screens show the same four digits, so you know it is really them. Tick &ldquo;always accept&rdquo; once and it never asks again.</p>
          </div>
        </div>
      }
    />
  );
}
