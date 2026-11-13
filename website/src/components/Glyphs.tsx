/*
 * The icon set: drawn by hand in one line weight (24 grid, 1.5 stroke, round caps), not imported.
 * Same hand as the apps' own icons, so the site and the product look like one thing.
 */
import type { SVGProps } from 'react';

export type GlyphName =
  | 'phone' | 'laptop' | 'wifi' | 'hotspot' | 'globe' | 'check' | 'lock' | 'file'
  | 'arrow' | 'download' | 'copy' | 'code' | 'tray' | 'x';

const PATHS: Record<GlyphName, React.ReactNode> = {
  phone: (
    <>
      <rect x="7" y="3" width="10" height="18" rx="2.6" />
      <path d="M10.5 18h3" />
    </>
  ),
  laptop: (
    <>
      <rect x="4.5" y="5" width="15" height="10" rx="1.8" />
      <path d="M2.5 19h19" />
    </>
  ),
  wifi: (
    <>
      <path d="M3.5 9.5a12 12 0 0 1 17 0" />
      <path d="M6.5 12.8a8 8 0 0 1 11 0" />
      <path d="M9.5 16a4 4 0 0 1 5 0" />
      <circle cx="12" cy="19.2" r=".7" />
    </>
  ),
  hotspot: (
    <>
      <circle cx="12" cy="13" r="1.3" />
      <path d="M8 9.2a5.6 5.6 0 0 0 0 7.6M16 9.2a5.6 5.6 0 0 1 0 7.6" />
      <path d="M5 6.2a9.8 9.8 0 0 0 0 13.6M19 6.2a9.8 9.8 0 0 1 0 13.6" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <ellipse cx="12" cy="12" rx="4" ry="9" />
      <path d="M3 12h18" />
    </>
  ),
  check: <path d="M5 12.5l5 5L19 7" />,
  lock: (
    <>
      <rect x="5.5" y="11" width="13" height="9" rx="2" />
      <path d="M8.5 11V8.5a3.5 3.5 0 0 1 7 0V11" />
    </>
  ),
  file: <path d="M6 3h8l5 5v12H6zM14 3v5h5" />,
  arrow: <path d="M4 12h16M14 6l6 6-6 6" />,
  download: <path d="M12 4v11M7 10.5l5 5 5-5M4.5 19.5h15" />,
  copy: (
    <>
      <rect x="8.5" y="8.5" width="11" height="11" rx="2" />
      <path d="M15.5 8.5V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7.5a2 2 0 0 0 2 2h2.5" />
    </>
  ),
  code: <path d="M8 7l-5 5 5 5M16 7l5 5-5 5M13.5 5l-3 14" />,
  tray: <path d="M12 4v10M7.5 10l4.5 4.5 4.5-4.5M4 14v5.5h16V14" />,
  x: <path d="M6 6l12 12M18 6L6 18" />,
};

export function Glyph({ name, size = 24, ...rest }: { name: GlyphName; size?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {PATHS[name]}
    </svg>
  );
}

/** The mark: a teal tile with the arrow, the same one the apps use. */
export function Mark({ size = 30 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="9" fill="var(--accent)" />
      <path d="M9 16h14M17.5 10.5L23 16l-5.5 5.5" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
