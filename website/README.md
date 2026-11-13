# ProxSh website

The home page where people download ProxSh for Android and Windows. A single story page built with
Next.js, GSAP and Lenis. It is fully static, so it hosts for free.

## Run it

```bash
cd website
pnpm install
pnpm dev          # http://localhost:3000
pnpm build        # production build, what Vercel runs
```

Open `/?shot=1` to skip the intro while checking layouts.

## Deploy on Vercel

1. Sign in at [vercel.com](https://vercel.com) with your GitHub account.
2. **Add New → Project**, then import the `proxsh` repository. Allow Vercel to see it when GitHub asks.
3. On the configure screen click **Edit** next to **Root Directory** and choose `website`.
   This is the one setting that matters: the site lives in a subfolder of the repo.
4. Leave **Framework Preset** as Next.js. Vercel finds `pnpm-lock.yaml` and uses pnpm on its own.
   Leave build and output settings alone.
5. Click **Deploy**. In a minute or two you get a `*.vercel.app` address.

From then on every push to `main` redeploys the site. A custom domain goes under
**Settings → Domains**.

## Make the download buttons work

The buttons point at files attached to a GitHub release. They return a 404 until the release exists.

1. Build the files:
   - `gradle :android:assembleDebug`, then copy `android/build/outputs/apk/debug/android-debug.apk` to `ProxSh-0.1.0-android.apk`
   - `gradle :laptop:packageExe` (needs WiX 3, see `scripts/env.ps1`), then copy `laptop/build/compose/binaries/main/exe/ProxSh-0.1.0.exe` to `ProxSh-0.1.0-windows-x64.exe`
2. Create the release and upload both files under exactly these names:
   ```bash
   gh release create v0.1.0 ProxSh-0.1.0-android.apk ProxSh-0.1.0-windows-x64.exe --title "ProxSh 0.1.0" --notes "First public build."
   ```
3. Put each file's size and fingerprint into `src/data/site.ts` (`Get-FileHash <file> -Algorithm SHA256`),
   push, and Vercel redeploys.

For a new version, change `version` in `src/data/site.ts` too and repeat.

## Where things are

```
src/data/site.ts         download links, sizes and fingerprints: edit this for a new release
src/components/scenes/   one file per chapter of the story
src/components/          devices, the paper slip, navigation, intro, icons
src/motion/              GSAP and Lenis setup, scroll chapters, intro gate
src/styles/              tokens.css (colours), kit.css (motion), site.css, scenes.css
```

## Notes

- The builds are early. The Android file is signed with a debug key and the Windows installer is not
  signed, so Android and Windows will both warn on first install. The page says so.
- Fonts (Fraunces, Instrument Sans, DM Mono) are downloaded and self-hosted by Next at build time.
- Respects reduced-motion: no intro, no smooth scroll, final states shown.
