import type { Metadata } from 'next';
import localFont from 'next/font/local';
import { Providers } from './providers';
import { WebVitals } from './web-vitals';
import { ThemeScript } from '@/shared/ui/ThemeScript';
import './globals.css';

// Self-hosted (TASK-MONO-767) instead of next/font's google loader — the Google Fonts
// build-time fetch was an intermittent build failure (see ticket). The woff2
// files below are the exact Noto Sans KR `latin`-subset bytes Google serves
// for this family/weight set, pinned via @fontsource/noto-sans-kr@5.3.0
// (OFL-1.1, see ./fonts/LICENSE-OFL.txt).
//
// Only the `latin` subset is self-hosted (the full Korean subset is multiple
// MB per weight and not worth committing/preloading). Korean glyphs — the
// primary content for this `lang="ko"` storefront — fall through to the
// OS-native fallback chain below instead of the browser default (often a
// serif). `adjustFontFallback` (Next default) tunes the fallback metrics to
// cut CLS. Zero added network bytes at build or request time.
//
// 🔴 MEASURED (TASK-MONO-767): the next/font `google`-loader version of this same
// config (`subsets: ['latin']`) did NOT actually drop the non-latin
// `@font-face` members from the compiled CSS — Google's css2 endpoint
// returns the font's full unicode-range split (Hangul + Hanja + Latin +
// Cyrillic + Vietnamese, ~497 rules) regardless of the `subset=` query
// param, and the pre-fix build shipped all of them under the literal
// `Noto Sans KR` family name. So Korean text was, in the deployed app,
// actually rendered via a Google-downloaded glyph (on-demand per
// unicode-range) rather than the OS fallback this comment describes. This
// self-hosted version intentionally does NOT reproduce that — it ships only
// the 4 latin weight files above, so Korean now reliably uses the fallback
// chain. That is a real (if likely hard-to-notice) rendering difference for
// Korean glyphs specifically; Latin/ASCII text is unaffected. Reproducing
// the old per-glyph-range Korean behavior would mean committing the full
// Korean subset (1.5–2.5 MB per weight) which the ticket's Edge Cases
// section explicitly weighs against.
const notoSansKR = localFont({
  src: [
    { path: './fonts/noto-sans-kr-latin-400.woff2', weight: '400', style: 'normal' },
    { path: './fonts/noto-sans-kr-latin-500.woff2', weight: '500', style: 'normal' },
    { path: './fonts/noto-sans-kr-latin-600.woff2', weight: '600', style: 'normal' },
    { path: './fonts/noto-sans-kr-latin-700.woff2', weight: '700', style: 'normal' },
  ],
  display: 'swap',
  preload: true,
  fallback: ['Pretendard', 'Apple SD Gothic Neo', 'Malgun Gothic', 'sans-serif'],
});

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
    <html lang="ko" className={notoSansKR.className} suppressHydrationWarning>
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
