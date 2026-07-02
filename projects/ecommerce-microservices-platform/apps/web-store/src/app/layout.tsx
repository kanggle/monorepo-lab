import type { Metadata } from 'next';
import { Noto_Sans_KR } from 'next/font/google';
import { Providers } from './providers';
import { WebVitals } from './web-vitals';
import { ThemeScript } from '@/shared/ui/ThemeScript';
import './globals.css';

// Only the `latin` subset is self-hosted (the full Korean subset is multiple MB
// and not worth preloading). Korean glyphs — the primary content for this
// `lang="ko"` storefront — therefore fall through to the OS-native fallback
// chain below rather than rendering in the browser default (often a serif).
// `adjustFontFallback` (Next default) tunes the fallback metrics to cut CLS.
// Zero added network bytes.
const notoSansKR = Noto_Sans_KR({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
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
