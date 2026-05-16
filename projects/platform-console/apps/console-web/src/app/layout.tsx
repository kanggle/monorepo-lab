import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';
import { QueryProvider } from '@/shared/ui/QueryProvider';
import { WebVitals } from '@/shared/observability/web-vitals';

export const metadata: Metadata = {
  title: 'Platform Console',
  description:
    'Unified operations console for the enterprise suite (gap · wms · scm · erp · finance)',
};

/**
 * Root layout — providers + observability only. The authenticated app shell
 * (topbar, tenant switcher, catalog nav) lives in the `(console)` segment
 * layout so the `(auth)/login` route stays minimal (perf budget: /login
 * 180 KB; architecture.md § Performance Budget).
 */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-full">
        <QueryProvider>{children}</QueryProvider>
        <WebVitals />
      </body>
    </html>
  );
}
