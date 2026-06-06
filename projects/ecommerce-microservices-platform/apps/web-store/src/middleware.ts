import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth } from '@/shared/auth/auth';

/**
 * Route guard. Public storefront paths (`/`, `/products/...`, `/login`,
 * `/api/auth/...`) are never gated — these match the existing customer
 * browsing experience pre-cutover. Protected paths (cart, checkout, my, etc.)
 * require an authenticated CONSUMER session; an unauthenticated visit
 * lands on `/login?from=<original>`.
 *
 * Cross-app guard: if a non-CONSUMER somehow holds a session (e.g. an
 * OPERATOR after `/api/auth/callback/iam`), the session callback in
 * `auth.ts` returns a session with `accountId=null`, and the `auth` check
 * here treats them as anonymous → redirected to `/login?error=...`.
 */
export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  // Public paths — never gate.
  if (
    pathname === '/' ||
    pathname.startsWith('/login') ||
    pathname.startsWith('/signup') ||
    pathname.startsWith('/products') ||
    pathname.startsWith('/api/auth') ||
    pathname.startsWith('/_next') ||
    pathname === '/favicon.ico'
  ) {
    return NextResponse.next();
  }

  const session = await auth();
  if (!session || !session.accountId) {
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
