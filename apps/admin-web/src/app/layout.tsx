import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';
import { Providers } from './providers';
import { WebVitalsReporter } from './web-vitals-reporter';

export const metadata: Metadata = {
  title: 'Admin Console',
  description: 'Global Account Platform — operator console',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>
          <WebVitalsReporter />
          {children}
        </Providers>
      </body>
    </html>
  );
}
