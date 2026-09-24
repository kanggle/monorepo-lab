import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

/**
 * Injects the current request path (pathname + search) into the
 * `x-pathname` request header so that Server Component layouts can read it
 * via `import { headers } from 'next/headers'`.
 *
 * Used by the console layout guard (Gap D / F6 — TASK-PC-FE-115) to
 * append `?redirect=<path>` when bouncing unauthenticated users to /login,
 * preserving the intended destination across the OIDC round-trip.
 *
 * Matcher deliberately excludes:
 *   - /login (avoids a redirect-loop where the guard reads its own path)
 *   - /api/** (route handlers — not page destinations)
 *   - /_next/** (Next.js internals)
 *   - Static asset extensions (fonts, images, favicons, …)
 *
 * TASK-PC-FE-299 AC-1 — the same reason this matcher excludes static assets
 * is why the `Cache-Control: no-store` below can't live in `next.config.mjs`
 * `headers()`: that config is purely path-based (`source` match only), and
 * every route under this matcher is shared by BOTH an authenticated operator
 * AND a sample visitor (ADR-MONO-074 — `(console)/layout.tsx` gates on
 * `isSampleVisitor() || isAuthenticated()`, same path either way). A static
 * `headers()` entry cannot see which one is asking. This middleware already
 * reads the request per-visit, so it is the one place that CAN tell: it adds
 * `no-store` only when BOTH session cookies the layout's own
 * {@link isAuthenticated} checks are present — the exact "authenticated"
 * predicate, no more, no less. A sample visitor carries neither cookie, so
 * their (identically-routed) pages keep the default cacheability ADR-MONO-074
 * relies on.
 */
export function middleware(request: NextRequest) {
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set(
    'x-pathname',
    request.nextUrl.pathname + request.nextUrl.search,
  );
  const response = NextResponse.next({ request: { headers: requestHeaders } });

  // Mirrors `isAuthenticated()` in shared/lib/session.ts (access token cookie
  // AND operator token cookie) — the SAME predicate, read from the request
  // cookie jar instead of `next/headers` `cookies()` (not available here).
  const authenticated =
    Boolean(request.cookies.get(ACCESS_COOKIE)?.value) &&
    Boolean(request.cookies.get(OPERATOR_COOKIE)?.value);
  if (authenticated) {
    response.headers.set('Cache-Control', 'no-store, private');
  }

  return response;
}

export const config = {
  matcher: [
    /*
     * Match every path EXCEPT:
     *   /login (and its sub-paths, though none exist)
     *   /api/**
     *   /_next/**
     *   common static-asset extensions
     */
    '/((?!login|api/|_next/|.*\\.(?:ico|png|jpg|jpeg|svg|webp|woff2?|ttf|otf|css|js|map)$).*)',
  ],
};
