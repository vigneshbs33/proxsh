'use client';
import { useCallback } from 'react';
import { Chapter, type Build } from '@/motion/chapter';
import { ramp } from '@/motion/gsap';

const TOTAL = 84; // 84 chunks of 512 KB = a 42 MB file
const DROP_AT = 52;

/**
 * Range: it carries on. A file travels in chunks. The connection drops partway; when it is back, only
 * the chunks that are missing go again. The counts here are the real mechanism with sample sizes.
 */
export function Resume() {
  const build = useCallback<Build>(({ tl, el, q }) => {
    const cells = [...el.querySelectorAll<HTMLElement>('.cell')];
    const state = el.querySelector<HTMLElement>('.board__state')!;
    const count = el.querySelector<HTMLElement>('.board__count')!;
    let lastN = 0;
    let lastPhase = '';

    const render = () => {
      const p = tl.progress();
      let n = 0;
      let phase = 'idle';
      if (p >= 0.08 && p < 0.5) { n = DROP_AT * ramp(0.08, 0.5, p); phase = 'sending'; }
      else if (p >= 0.5 && p < 0.62) { n = DROP_AT; phase = 'lost'; }
      else if (p >= 0.62 && p < 0.7) { n = DROP_AT; phase = 'back'; }
      else if (p >= 0.7 && p < 0.95) { n = DROP_AT + (TOTAL - DROP_AT) * ramp(0.7, 0.95, p); phase = 'resumed'; }
      else if (p >= 0.95) { n = TOTAL; phase = 'done'; }
      const whole = Math.floor(n);
      if (whole !== lastN) {
        const [a, b] = whole > lastN ? [lastN, whole] : [whole, lastN];
        for (let i = a; i < b; i++) cells[i]?.classList.toggle('is-full', whole > lastN);
        lastN = whole;
        count.textContent = `${whole} of ${TOTAL} chunks there`;
      }
      if (phase !== lastPhase) {
        lastPhase = phase;
        const copy: Record<string, string> = {
          idle: 'Ready to send',
          sending: 'Sending',
          lost: 'Connection lost',
          back: 'Back. Asking what already arrived',
          resumed: `Sending the other ${TOTAL - DROP_AT}`,
          done: 'All there, and checked against the file’s fingerprint',
        };
        state.textContent = copy[phase];
        state.dataset.phase = phase;
        cells.forEach((c, i) => c.classList.toggle('is-gap', phase === 'lost' && i === DROP_AT));
      }
    };
    tl.eventCallback('onUpdate', render);
    render();

    tl.fromTo(q('.rs__h .line > span'), { yPercent: 120 }, { yPercent: 0, duration: 0.12, stagger: 0.04, ease: 'power3.out', immediateRender: false }, 0.02);
    tl.fromTo(q('.rs__sub'), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.1, immediateRender: false }, 0.1);
    tl.fromTo(q('.board'), { opacity: 0, y: 30 }, { opacity: 1, y: 0, duration: 0.1, ease: 'power3.out', immediateRender: false }, 0.1);
    tl.fromTo(q('.resume__copy'), { opacity: 1, y: 0 }, { opacity: 0, y: -50, duration: 0.05, immediateRender: false }, 0.96);
    tl.fromTo(q('.board'), { opacity: 1 }, { opacity: 0, duration: 0.04, immediateRender: false }, 0.96);
  }, []);

  return (
    <Chapter
      vh={320}
      className="scene resume"
      build={build}
      back={
        <div className="scene__back resume__back">
          <div className="board">
            <div className="board__meta">
              <b>Holiday video.mp4</b>
              <span>42 MB, sent as 84 chunks of 512 KB</span>
            </div>
            <div className="board__grid" aria-hidden="true">
              {Array.from({ length: TOTAL }, (_, i) => (
                <i className="cell" key={i} />
              ))}
            </div>
            <div className="board__foot">
              <span className="board__state" data-phase="idle">Ready to send</span>
              <span className="board__count">0 of {TOTAL} chunks there</span>
            </div>
          </div>
        </div>
      }
      front={
        <div className="scene__front">
          <div className="scene__copy resume__copy">
            <h2 className="display rs__h">
              <span className="line"><span>Connection drops?</span></span>
              <span className="line"><span><em>It carries on.</em></span></span>
            </h2>
            <p className="lede rs__sub">A big file travels in small chunks. If the Wi-Fi blinks, only the missing chunks go again, not the whole file.</p>
          </div>
        </div>
      }
    />
  );
}
