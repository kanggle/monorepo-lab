import type { Metadata } from 'next';
import { Providers } from './providers';
import './fonts/fonts.css';
import './globals.css';

// Self-hosted (TASK-MONO-767) instead of next/font's google loader — the Google Fonts
// build-time fetch was an intermittent build failure (see ticket). The woff2
// files are the exact Noto Sans KR bytes Google serves for this
// family/weight set, pinned via @fontsource/noto-sans-kr@5.3.0 (OFL-1.1, see
// ./fonts/LICENSE-OFL.txt).
//
// 🔴 Plain CSS import, not `next/font/local` (owner decision 2026-10-07,
// follow-up to the MEASURED finding below): `next/font/local`'s `src` array
// cannot express a per-entry `unicode-range` — checked the installed
// package's own type (`next/dist/compiled/@next/font/dist/local/
// validate-local-font-function-call.d.ts`), the `src` item shape is
// `{ path, weight?, style?, ext, format }`, no unicode-range field. Korean
// coverage needs ~120 unicode-range-split files *per weight* so a page only
// downloads the handful of chunks it actually renders (vs. one multi-MB
// file per weight) — `next/font/local` can't produce that, so
// `./fonts/fonts.css` (hand-authored glue) imports the generated
// `./fonts/noto-sans-kr.generated.css` (see
// scripts/gen-noto-sans-kr-fonts-css.mjs) instead. `./fonts/fonts.css` also
// sets `--font-noto-sans-kr` on `:root` — same CSS variable name
// `next/font/local`'s `variable` option used to produce, consumed the same
// way by Tailwind's `font-sans` (`tailwind.config.ts`).
//
// MEASURED (TASK-MONO-767, first pass): the next/font `google`-loader
// version of this same config (`subsets: ['latin']`) did NOT actually drop
// the non-latin `@font-face` members from the compiled CSS — Google's css2
// endpoint returns the font's full unicode-range split (Hangul + Hanja +
// Latin + Cyrillic + Vietnamese, ~497 rules) regardless of the `subset=`
// query param, and the pre-fix build shipped all of them under the literal
// `Noto Sans KR` family name. So Korean text was, in the deployed app,
// actually rendered via a Google-downloaded glyph (on-demand per
// unicode-range) — this version reproduces exactly that (same unicode-range
// split, now self-hosted), so Korean rendering should match the pre-fix
// behavior again (re-verified by screenshot diff, see task file).
export const metadata: Metadata = {
  title: 'fan-platform',
  description: '아티스트와 팬을 잇는 K-pop 팬덤 플랫폼',
};

// 🔵 No manual `<link rel="preload">`: `next/font/local`'s `preload: true`
// used to inject that automatically for a webpack-hashed asset URL. Without
// next/font managing the font, the built asset URL is only known after
// webpack hashes it (via the CSS `url(...)` it's referenced from) — hand-
// writing a preload href would either go stale against the real hash or
// require importing the file as a JS asset module just to read its URL.
// Not worth the complexity for a `font-display: swap` font where the
// fallback already renders immediately; dropped rather than shipped broken.
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="min-h-screen bg-ink-50 font-sans text-ink-900 antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
