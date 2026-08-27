import type { ReactNode } from 'react';
import { Header } from '@/widgets/header/Header';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';

/**
 * Main shell — gated by middleware so all child pages can assume the visitor
 * is authenticated. Header reads session via the server boundary.
 *
 * 🔴 That first sentence is currently FALSE in production, and it is not this file's
 * job to fix: `TASK-FAN-FE-018` measured that `src/middleware.ts` never fires on the
 * deployed build (`/nonexistent-xyz` answers 404 instead of redirecting to `/login`).
 * The shell therefore renders for anonymous visitors too — which is exactly why the
 * notice below belongs here rather than behind the guard.
 *
 * `DemoBackendNotice` renders nothing outside the demo deployment, so local dev and CI
 * are unaffected (`TASK-MONO-586` AC-3).
 */
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <DemoBackendNotice />
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>
    </>
  );
}
