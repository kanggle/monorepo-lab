import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth } from '@/shared/auth/auth';

/**
 * Route guard for admin-dashboard. Every page outside `/login` and
 * `/api/auth/*` requires an OPERATOR session. Non-OPERATOR sessions are
 * downgraded by the `session` callback in `auth.ts` (returns no `accountId`)
 * and bounced to `/login?from=...`.
 */
export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (
    pathname.startsWith('/login') ||
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
    '/((?!api/auth|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)',
  ],
};
