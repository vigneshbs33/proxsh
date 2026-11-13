import { Hero } from '@/components/scenes/Hero';
import { Detour } from '@/components/scenes/Detour';
import { Find } from '@/components/scenes/Find';
import { Race } from '@/components/scenes/Race';
import { Code } from '@/components/scenes/Code';
import { Resume } from '@/components/scenes/Resume';
import { Inside } from '@/components/scenes/Inside';
import { Download } from '@/components/scenes/Download';
import { Footer } from '@/components/Footer';

export default function Home() {
  return (
    <main>
      <Hero />
      <Detour />
      <Find />
      <Race />
      <Code />
      <Resume />
      <Inside />
      <Download />
      <Footer />
    </main>
  );
}
