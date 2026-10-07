import type { Metadata } from 'next';
import localFont from 'next/font/local';
import { Providers } from './providers';
import './globals.css';

// Self-hosted (TASK-MONO-767) instead of next/font's google loader — the Google Fonts
// build-time fetch was an intermittent build failure (see ticket). The woff2
// files below are the exact Noto Sans KR `latin`-subset bytes Google serves
// for this family/weight set, pinned via @fontsource/noto-sans-kr@5.3.0
// (OFL-1.1, see ./fonts/LICENSE-OFL.txt).
//
// 🔴 MEASURED (TASK-MONO-767): the next/font `google`-loader version of this same
// config (`subsets: ['latin']`) did NOT actually drop the non-latin
// `@font-face` members from the compiled CSS — Google's css2 endpoint
// returns the font's full unicode-range split (Hangul + Hanja + Latin +
// Cyrillic + Vietnamese, ~497 rules) regardless of the `subset=` query
// param, and the pre-fix build shipped all of them under the literal
// `Noto Sans KR` family name. So Korean text was, in the deployed app,
// actually rendered via a Google-downloaded glyph (on-demand per
// unicode-range), not the Tailwind `font-sans` fallback
// (`system-ui, sans-serif`, see tailwind.config.ts) this comment used to
// imply. This self-hosted version ships only the 4 latin weight files
// above, so Korean now reliably falls through to that `system-ui` /
// `sans-serif` chain. That is a real (if likely hard-to-notice) rendering
// difference for Korean glyphs specifically; Latin/ASCII text is
// unaffected. Committing the full Korean subset (1.5–2.5 MB per weight) to
// reproduce the old behavior exactly is what the ticket's Edge Cases
// section weighs against.
const notoSansKR = localFont({
  src: [
    { path: './fonts/noto-sans-kr-latin-400.woff2', weight: '400', style: 'normal' },
    { path: './fonts/noto-sans-kr-latin-500.woff2', weight: '500', style: 'normal' },
    { path: './fonts/noto-sans-kr-latin-600.woff2', weight: '600', style: 'normal' },
    { path: './fonts/noto-sans-kr-latin-700.woff2', weight: '700', style: 'normal' },
  ],
  display: 'swap',
  preload: true,
  variable: '--font-noto-sans-kr',
});

export const metadata: Metadata = {
  title: 'fan-platform',
  description: '아티스트와 팬을 잇는 K-pop 팬덤 플랫폼',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={notoSansKR.variable}>
      <body className="min-h-screen bg-ink-50 font-sans text-ink-900 antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
