'use client';

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';

/**
 * Poll period while the tab is visible. 🔴 Must stay BELOW the `jwt` callback's
 * refresh margin (`REFRESH_MARGIN_SECONDS` = 60s in `auth-callbacks.ts`): the
 * callback refreshes only once the access token is within that margin of
 * expiry, so a read at least every 50s always lands inside the window before
 * the token expires — a visible tab never holds an expired access token.
 * (Not imported from `auth-callbacks.ts` to keep that server module out of the
 * client graph; `session-keeper.test.tsx` pins the inequality.)
 */
export const KEEPER_INTERVAL_MS = 50_000;

export const SESSION_ENDPOINT = '/api/auth/session';

/**
 * TASK-FAN-FE-027 — drives the ONE place the silent refresh runs.
 *
 * Every server-side session read in this app is decode-only (`session.ts`,
 * `middleware.ts`): the request-less `auth()` they used to call refreshed at
 * IAM and threw the rotated cookie away. The refresh now runs only in
 * `GET /api/auth/session`, whose response writes the cookie back — and unlike
 * ecommerce web-store (TASK-FE-106), fan-platform-web had NO client that ever
 * read that route (no `next-auth/react` `SessionProvider`). This component is
 * that reader, kept deliberately small instead of a session context nobody
 * consumes:
 *
 *   - every {@link KEEPER_INTERVAL_MS} while the tab is visible, and when the
 *     tab becomes visible again (return from idle) — so the cookie is rotated
 *     BEFORE the next navigation's server render needs it;
 *   - at once whenever the server render reports `staleAtRender`
 *     (it used an expired bearer — e.g. a page opened after a long idle):
 *     read, then `router.refresh()` so the page re-renders with the rotated
 *     cookie (or as anonymous / `/login` if the refresh genuinely failed).
 *
 * Concurrent reads from several tabs are the race `session-route.ts` absorbs;
 * within one tab reads are single-flight.
 *
 * 🔴🔴 Mounted ONLY in the header's `authed` branch, like `DemoHeartbeat` —
 * an anonymous visit must send no request at all (`Header.tsx`).
 * 🔵 Failures are swallowed: a failed read just leaves the next one to try.
 */
export function SessionKeeper({ staleAtRender }: { staleAtRender: boolean }) {
  const router = useRouter();
  const inFlight = useRef<Promise<boolean> | null>(null);

  const readSession = useRef(() => {
    if (!inFlight.current) {
      // DEMO-URL-EXEMPT: same-origin — `SESSION_ENDPOINT` is this app's own Auth.js route (a relative path), not a backend.
      inFlight.current = fetch(SESSION_ENDPOINT, {
        cache: 'no-store',
        credentials: 'same-origin',
      })
        .then((res) => res.ok)
        .catch(() => false)
        .finally(() => {
          inFlight.current = null;
        });
    }
    return inFlight.current;
  }).current;

  useEffect(() => {
    const visible = () => document.visibilityState === 'visible';
    const id = setInterval(() => {
      if (visible()) void readSession();
    }, KEEPER_INTERVAL_MS);
    const onVisibility = () => {
      if (visible()) void readSession();
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [readSession]);

  useEffect(() => {
    if (!staleAtRender) return;
    let cancelled = false;
    void readSession().then((ok) => {
      // Only re-render on an answered read: a network failure would otherwise
      // re-render into the same stale state.
      if (ok && !cancelled) router.refresh();
    });
    return () => {
      cancelled = true;
    };
  }, [staleAtRender, readSession, router]);

  return null;
}
