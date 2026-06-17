import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

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
 */
export function middleware(request: NextRequest) {
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set(
    'x-pathname',
    request.nextUrl.pathname + request.nextUrl.search,
  );
  return NextResponse.next({ request: { headers: requestHeaders } });
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
