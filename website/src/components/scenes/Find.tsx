'use client';
import { useCallback } from 'react';
import { Chapter, type Build } from '@/motion/chapter';
import { Stage, Phone, Laptop, PhoneUi, LaptopUi } from '../Devices';

/**
 * The turn: it finds the laptop by itself. Rings spread out from the phone, and each screen's
 * "nothing nearby" state gives way to the other device, with no code, QR or account in between.
 */
export function Find() {
  const build = useCallback<Build>(({ tl, q }) => {
    tl.fromTo(q('.fd__h .line > span'), { yPercent: 120 }, { yPercent: 0, duration: 0.12, stagger: 0.04, ease: 'power3.out', immediateRender: false }, 0.03);
    tl.fromTo(q('.fd__sub'), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.1, immediateRender: false }, 0.12);

    q('.wave').forEach((w, i) => {
      const at = 0.2 + i * 0.075;
      tl.fromTo(w, { scale: 0.15, opacity: 0 }, { scale: 1.1, opacity: 0.55, duration: 0.08, ease: 'none', immediateRender: false }, at);
      tl.fromTo(w, { scale: 1.1, opacity: 0.55 }, { scale: 2.9, opacity: 0, duration: 0.22, ease: 'none', immediateRender: false }, at + 0.08);
    });

    tl.fromTo(q('.layer--b'), { opacity: 0 }, { opacity: 1, duration: 0.08, stagger: 0.05, immediateRender: false }, 0.5);
    tl.fromTo(q('.lp-peer, .ph-peer'), { scale: 0.86, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.08, ease: 'back.out(2.2)', stagger: 0.05, immediateRender: false }, 0.52);
    tl.fromTo(q('.chip'), { opacity: 0, y: 16 }, { opacity: 1, y: 0, duration: 0.08, stagger: 0.07, ease: 'power3.out', immediateRender: false }, 0.6);

    tl.fromTo(q('.fd__copy'), { opacity: 1, y: 0 }, { opacity: 0, y: -50, duration: 0.07, immediateRender: false }, 0.93);
    tl.fromTo(q('.stage'), { opacity: 1 }, { opacity: 0, duration: 0.06, immediateRender: false }, 0.94);
  }, []);

  return (
    <Chapter
      vh={280}
      className="scene find"
      build={build}
      back={
        <div className="scene__back">
          <Stage className="scene__stage">
            <div className="waves" aria-hidden="true">
              <i className="wave" />
              <i className="wave" />
              <i className="wave" />
            </div>
            <Phone>
              <div className="layer layer--a">
                <PhoneUi parts={['actions']} />
              </div>
              <div className="layer layer--b">
                <PhoneUi parts={['peer', 'actions']} />
              </div>
            </Phone>
            <Laptop>
              <div className="layer layer--a">
                <LaptopUi parts={['drop']} />
              </div>
              <div className="layer layer--b">
                <LaptopUi parts={['peer', 'drop']} />
              </div>
            </Laptop>
          </Stage>
        </div>
      }
      front={
        <div className="scene__front">
          <div className="scene__copy fd__copy">
            <h2 className="display fd__h">
              <span className="line"><span>It finds the laptop</span></span>
              <span className="line"><span><em>by itself.</em></span></span>
            </h2>
            <p className="lede fd__sub">Open ProxSh on both and they notice each other on the Wi-Fi, a second or two later.</p>
            <ul className="chips">
              <li className="chip">No QR code</li>
              <li className="chip">No account</li>
              <li className="chip">No pairing step</li>
            </ul>
          </div>
        </div>
      }
    />
  );
}
