import type { Metadata } from 'next';
import { Providers } from './providers';
import { WebVitals } from './web-vitals';
import { ThemeScript } from '@/shared/ui/ThemeScript';
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
// glyphs — the primary content for this `lang="ko"` storefront — need ~120
// unicode-range-split files *per weight* so a page only downloads the
// handful of chunks it actually renders (vs. one multi-MB file per weight)
// — `next/font/local` can't produce that, so `./fonts/fonts.css`
// (hand-authored glue) imports the generated
// `./fonts/noto-sans-kr.generated.css` (see
// scripts/gen-noto-sans-kr-fonts-css.mjs) instead and defines a
// `.font-noto-sans-kr` class carrying the same family + fallback chain
// (`Pretendard, Apple SD Gothic Neo, Malgun Gothic, sans-serif`)
// `next/font/local`'s `className` used to produce.
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
// behavior again (re-verified by screenshot diff, see task file). Zero
// added network bytes at build time either way.
//
// 🔵 No manual `<link rel="preload">`: see the equivalent comment in
// fan-platform-web's layout.tsx for why (webpack-hashed asset URL, not
// worth the complexity for a `font-display: swap` font).

export const metadata: Metadata = {
  title: 'Web Store',
  description: 'Customer-facing storefront',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko" className="font-noto-sans-kr" suppressHydrationWarning>
      <head>
        <ThemeScript />
      </head>
      <body>
        <WebVitals />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
