import type { Metadata, Viewport } from 'next';
import { Fraunces, Instrument_Sans, DM_Mono } from 'next/font/google';
import '../styles/tokens.css';
import '../styles/kit.css';
import '../styles/site.css';
import '../styles/scenes.css';
import { introGateScript } from '@/motion/intro-gate';
import { SmoothScroll } from '@/motion/smooth-scroll';
import { Reveals } from '@/motion/reveal';
import { Intro } from '@/components/Intro';
import { Nav } from '@/components/Nav';
import { Rail } from '@/components/Rail';

const display = Fraunces({
  subsets: ['latin'],
  style: ['normal', 'italic'],
  axes: ['opsz', 'SOFT'],
  variable: '--font-display',
  display: 'swap',
});
const body = Instrument_Sans({ subsets: ['latin'], variable: '--font-body', display: 'swap' });
const mono = DM_Mono({ subsets: ['latin'], weight: ['400', '500'], variable: '--font-mono-face', display: 'swap' });

export const metadata: Metadata = {
  title: 'ProxSh: phone to laptop, already there',
  description: 'Send files, photos and links between your phone and laptop. No account, no cable, no code. Free and open source.',
};

export const viewport: Viewport = { themeColor: '#f6f2ea' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning className={`${display.variable} ${body.variable} ${mono.variable}`}>
      <head>
        <script dangerouslySetInnerHTML={{ __html: introGateScript }} />
      </head>
      <body>
        <SmoothScroll />
        <Intro />
        <Nav />
        <Rail />
        {children}
        <Reveals />
      </body>
    </html>
  );
}
