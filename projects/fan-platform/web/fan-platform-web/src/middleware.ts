import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth } from '@/shared/auth/auth';

/**
 * Route guard. Protects every page except `/login` and `/api/auth/*`.
 *
 * The `authorized` callback in auth.ts produces the same logic for next-auth
 * internal flows; this middleware is the explicit redirect path so unauth'd
 * visits to a protected route land on `/login?from=<original>`.
 */
export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  // Public paths — never gate.
  if (
    pathname.startsWith('/login') ||
    pathname.startsWith('/api/auth') ||
    // ⏳ TEMPORARY — TASK-MONO-571 / ADR-MONO-067 AC-0 ②. Removed with the probe route (AC-5).
    // Without this the probe is swallowed by the session gate and answers with a login redirect,
    // whose body is not JSON — a wiring failure that would read as a measurement failure.
    pathname.startsWith('/api/ac0-probe') ||
    pathname.startsWith('/_next') ||
    pathname === '/favicon.ico'
  ) {
    return NextResponse.next();
  }
  const session = await auth();
  if (!session) {
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
