'use client';

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

/**
 * TASK-PC-FE-299 AC-5 — clears the shared TanStack Query cache on a **forced**
 * re-login landing (`/login?error=session_expired`, the {@link SESSION_EXPIRED}
 * marker — never a plain `/login` visit).
 *
 * -----------------------------------------------------------------------------
 * 🔴 Why this is the one genuine "residual client state" surface here
 * -----------------------------------------------------------------------------
 * This app never puts session/tenant/drill/user state in
 * `localStorage`/`sessionStorage` (`shared/lib/session.ts`'s own JSDoc makes
 * that a hard rule — HttpOnly cookies only). Client component `useState` (the
 * tenant switcher, sidebar drill `openKey`, …) dies with the component when
 * its route segment unmounts, which a forced re-login redirect always does.
 *
 * The one thing that does NOT reset on that redirect: `QueryProvider` mounts
 * its `QueryClient` in the ROOT layout (`app/layout.tsx`), which wraps BOTH
 * `(console)` and `(auth)/login` — so a forced-relogin redirect that happens
 * as an in-app (RSC) transition, not a full document reload, carries the
 * SAME `QueryClient` instance across the boundary. Its cache can still hold
 * the just-rejected session's account/tenant/audit/etc. reads in memory.
 * Nothing currently mounted renders that cache (the login page reads none of
 * those query keys), so this is not a visible leak today — but a stale cache
 * entry surviving a forced logout is exactly the "메모리 상태" this ticket's
 * AC-5 names, and the next authenticated render (after a fresh login) could
 * otherwise briefly paint the PREVIOUS session's cached data before its
 * queries revalidate (`staleTime: 30_000` in `QueryProvider.tsx` — up to 30s
 * of exactly that).
 *
 * 🔵 The plain (non-forced) `/login` visit does NOT run this — a plain
 * revisit is not a "the backend just rejected this session" event, and
 * clearing the cache there would just add pointless refetches to an ordinary
 * flow. See the mount guard in `(auth)/login/page.tsx`.
 */
export function ForcedReLoginCacheReset() {
  const queryClient = useQueryClient();

  useEffect(() => {
    queryClient.clear();
  }, [queryClient]);

  return null;
}
