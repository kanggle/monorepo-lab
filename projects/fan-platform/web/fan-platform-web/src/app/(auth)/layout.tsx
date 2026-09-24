import type { ReactNode } from 'react';
import { Header } from '@/widgets/header/Header';

/**
 * `(auth)` shell — currently only `/login`.
 *
 * `TASK-FAN-FE-024` — the login page had no top navigation (no `Header`
 * import), so a visitor landing there could not browse the rest of the site
 * without first authenticating. Reusing the shared `Header` (server
 * component) keeps the documented zero-gateway invariant for anonymous
 * visitors: `Header` only fetches notification data inside its `authed`
 * branch (`widgets/header/Header.tsx:39-51`), and `/login` is reached
 * exclusively by unauthenticated visitors, so this adds no new gateway call.
 *
 * 🔴 Do NOT port web-store's client-component header (hamburger state) here
 * — `Header` is a server component by design and this layout must not change
 * that.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      {children}
    </>
  );
}
