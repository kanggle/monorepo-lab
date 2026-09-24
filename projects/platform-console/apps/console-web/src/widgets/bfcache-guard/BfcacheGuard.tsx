'use client';

import { useEffect } from 'react';

/**
 * `pageshow` bfcache guard (TASK-PC-FE-299 AC-3). **Mount only inside the
 * authenticated `(console)` shell** — see the mount-point note in
 * `(console)/layout.tsx` (same rule {@link DemoHeartbeat} follows, for the
 * mirror-image reason).
 *
 * -----------------------------------------------------------------------------
 * 🔴 Why this exists — a back-button return can show a torn-down session
 * -----------------------------------------------------------------------------
 * The browser's back/forward cache (bfcache) can restore a full, already-
 * rendered page (DOM + in-memory JS state) WITHOUT re-running this app's
 * server-side auth guard (`(console)/layout.tsx`'s `isAuthenticated()` /
 * `isSampleVisitor()` check only runs on a real navigation-to-the-server).
 * If the session cookies were cleared since that page was frozen into
 * bfcache (logout, forced re-login, idle expiry), a back-button return would
 * silently show the stale authenticated screen — tenant selection, drill
 * state, account label, all of it — with no server round-trip to catch it.
 * That is also the AC-5 "residual client state" surface: this app never
 * writes session/tenant/drill state to localStorage/sessionStorage (grep
 * confirms zero uses — `shared/lib/session.ts`'s own JSDoc requires
 * HttpOnly-cookie-only session state), so the ONLY place stale client state
 * can survive a forced logout is exactly this frozen bfcache snapshot.
 *
 * -----------------------------------------------------------------------------
 * 🔵 The fix — force a real navigation, let the existing guard re-run
 * -----------------------------------------------------------------------------
 * `pageshow`'s `event.persisted === true` is the standard signal "this paint
 * came from bfcache, not a fresh load" (MDN). On that signal we
 * `location.reload()` — a real network round-trip that re-enters
 * `(console)/layout.tsx` server-side with the CURRENT cookie jar. If the
 * session is still valid, the guard renders the shell normally (no
 * regression — this is indistinguishable from an ordinary refresh, AC-2).
 * If the cookies are gone, the guard's existing redirect (`/login` or the
 * silent refresh hop, TASK-MONO-674) fires exactly as it does for any other
 * request. This widget adds NO new auth logic — it only makes sure the
 * existing one gets a chance to run again after a bfcache restore.
 *
 * 🔴 Not scoped to the sample-visitor shell (ADR-MONO-074): a sample visitor
 * has no session cookie of any kind, so forcing them through this same
 * guard would be a harmless no-op reload — but it IS an unnecessary network
 * round trip on every back-button visit, which is exactly the caching
 * benefit ADR-MONO-074 wants to keep for that path. So the mount point is
 * the deciding factor: `(console)/layout.tsx` only renders this component
 * on the authenticated (`!sampleVisitor`) branch.
 */
export function BfcacheGuard() {
  useEffect(() => {
    const handlePageShow = (event: PageTransitionEvent) => {
      if (event.persisted) {
        window.location.reload();
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

  return null;
}
