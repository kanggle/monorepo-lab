import type { ReactNode } from 'react';
import { Header } from '@/widgets/header/Header';

/**
 * Main shell — gated by middleware so all child pages can assume the visitor
 * is authenticated. Header reads session via the server boundary.
 */
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>
    </>
  );
}
