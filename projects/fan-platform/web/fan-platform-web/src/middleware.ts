import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth } from '@/shared/auth/auth';

/**
 * Route guard. Protects every page except `/login` and `/api/auth/*`.
 *
 * The `authorized` callback in auth.ts produces the same logic for next-auth
 * internal flows; this middleware is the explicit redirect path so unauth'd
 * visits to a protected route land on `/login?from=<original>`.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * TASK-FAN-FE-019 / AC-1 — what this guard does when auth is NOT configured.
 * Chosen: **(A) fail-closed, redirect to `/login`.** The measurement that
 * forced the choice is TASK-FAN-FE-018's verdict; reproduced 2026-08-28 UTC
 * against a local `next start` (prod build) with the auth env absent:
 *
 *   /artists  /me  /posts/:id  → 200  (page rendered, no redirect)
 *   /nonexistent-xyz           → 404  ← the discriminator: the request reached
 *                                       routing, so the guard did not close
 *   /login                     → 200  (public — correct)
 *   /api/auth/providers        → 500  {"message":"There was a problem with
 *                                       the server configuration…"}
 *
 * Mechanism: with no usable secret, auth.js answers its own session request
 * with a 500 **and a JSON body**, and `auth()` returns that body verbatim
 * (`next-auth/lib/index.js` → `getSession(...).then((r) => r.json())` — there
 * is no `response.ok` check). So the previous `if (!session)` was asking "is
 * this truthy?" of `{ message: "There was a problem…" }`, which it is. The
 * guard read a configuration error as "there is a session" and opened.
 *
 * A route guard must never fail in that direction, and the symptom was not an
 * error but *nothing happening*, so nobody saw it for three days.
 *
 * Why (A) and not (B) 5xx: a 5xx takes down the public paths too — `/login`
 * and `/api/auth/*`, i.e. the whole site — and removes the one page that can
 * tell an operator what broke. (A)'s known cost is that a misconfigured
 * deployment can look normal; that is paid off two ways: the misconfiguration
 * keeps an independent loud signal (`/api/auth/*` → 500, asserted in
 * `e2e-smoke/auth-config-absent.spec.ts`), and the branch below logs every
 * time it fires. "Closed" and "misconfigured" stay distinguishable.
 *
 * Why the predicate is the *shape of the returned value* rather than an env
 * check (`process.env.NEXTAUTH_SECRET`): env names are a declaration, and
 * this defect was exactly a declaration that did not match the runtime. What
 * gets trusted is the value `auth()` returns, so that is what gets asked.
 * ─────────────────────────────────────────────────────────────────────────
 */

/**
 * What `auth()` can hand back, and what each means:
 *
 *  | returned                             | meaning                          |
 *  |--------------------------------------|----------------------------------|
 *  | `null`                               | anonymous — normal, closed       |
 *  | `{ user: {…}, … }`                   | signed in — open                 |
 *  | `{ user: undefined, … }`             | silent refresh failed; the       |
 *  |                                      | `session` callback degrades to   |
 *  |                                      | anonymous on purpose (F3)        |
 *  | `{ message: "There was a problem…" }`| auth.js is misconfigured         |
 *
 * Only row 2 may pass. `!session` passed rows 2, 3 **and 4**.
 */
function hasAuthenticatedUser(session: unknown): boolean {
  if (typeof session !== 'object' || session === null) return false;
  return (session as { user?: unknown }).user != null;
}

/**
 * Resolve "is this request authenticated?" so that every failure mode —
 * throw, error payload, degraded session — lands on `false` (closed).
 */
async function isAuthenticated(pathname: string): Promise<boolean> {
  let session: unknown;
  try {
    session = await auth();
  } catch (error) {
    // auth.js can also throw outright (e.g. an unparseable config). Closed.
    console.error(
      `[middleware] auth() threw for ${pathname}; failing closed to /login`,
      error,
    );
    return false;
  }
  if (session == null) return false; // anonymous — the ordinary case, not an error
  if (hasAuthenticatedUser(session)) return true;
  // Non-null but carrying no user: an auth.js error payload, or a session the
  // `session` callback degraded to anonymous. Both are closed, and the first
  // is an outage — say so, because option (A) is otherwise silent.
  console.error(
    `[middleware] auth() returned no user for ${pathname}; failing closed to /login. ` +
      'If this is a configuration error, /api/auth/providers answers 500. Value: ' +
      JSON.stringify(session),
  );
  return false;
}

export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  // Public paths — never gate.
  if (
    pathname.startsWith('/login') ||
    pathname.startsWith('/api/auth') ||
    pathname.startsWith('/_next') ||
    pathname === '/favicon.ico'
  ) {
    return NextResponse.next();
  }
  if (!(await isAuthenticated(pathname))) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = '/login';
    loginUrl.search = `?from=${encodeURIComponent(pathname + search)}`;
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match every path except:
     *  - /api/auth (next-auth handler)
     *  - /_next/static, /_next/image
     *  - /favicon.ico, /robots.txt, /sitemap.xml
     *  - All public asset extensions handled by the negative lookahead
     */
    '/((?!api/auth|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)',
  ],
};
